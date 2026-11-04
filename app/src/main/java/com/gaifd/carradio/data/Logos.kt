package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Логотипы станций: скачиваются один раз и хранятся в памяти телефона,
 * так что без интернета тоже видны. Нет логотипа — цветная плитка с названием.
 *
 * Где ищем, по порядку:
 * 1) ссылка из каталога Radio Browser (у этой записи или у той же станции под другим id);
 * 2) логотип «родительской» станции из твоего списка — для подканалов (Melodia FM Disco → Мелодія FM) с подписью;
 * 3) иконка с сайта станции (адрес из каталога или из заголовков потока).
 */
object Logos {
    private const val SIZE = 256
    private const val RETRY_AFTER_MS = 7L * 24 * 3600_000 // не нашли — пробуем снова через неделю
    private lateinit var dir: File
    private lateinit var sp: SharedPreferences
    private val bitmaps = HashMap<String, Bitmap?>()
    private val bytes = HashMap<String, ByteArray?>()
    private val _version = MutableStateFlow(0)
    /** Меняется, когда появился или убран логотип — экран перерисовывается. */
    val version: StateFlow<Int> = _version

    @Synchronized
    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = File(ctx.applicationContext.filesDir, "logos").apply { mkdirs() }
        sp = ctx.applicationContext.getSharedPreferences("logos", Context.MODE_PRIVATE)
    }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".png")

    fun has(id: String) = ::dir.isInitialized && file(id).exists()

    @Synchronized
    fun bitmap(id: String): Bitmap? {
        if (!::dir.isInitialized) return null
        return bitmaps.getOrPut(id) {
            val f = file(id)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }

    /** PNG для обложки на экране блокировки и магнитоле. */
    @Synchronized
    fun pngBytes(id: String): ByteArray? {
        if (!::dir.isInitialized) return null
        return bytes.getOrPut(id) {
            val f = file(id)
            if (f.exists()) f.readBytes() else null
        }
    }

    // ---------- Автопоиск ----------

    /**
     * Найти логотипы станциям, у которых их нет. Каждую станцию пробуем не чаще раза в неделю,
     * а по кнопке «Подтянуть логотипы» (force) — сразу. onProgress(i, всего) — для экрана.
     * Возвращает (нашлось, искали).
     */
    suspend fun autoFind(
        stations: List<Station>,
        force: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Pair<Int, Int> {
        if (!::dir.isInitialized) return 0 to 0
        val now = System.currentTimeMillis()
        val todo = stations.filter { st ->
            !has(st.id) && (force || now - sp.getLong("tried_${st.id}", 0) > RETRY_AFTER_MS)
        }
        var found = 0
        todo.forEachIndexed { i, st ->
            onProgress(i + 1, todo.size)
            if (findOne(st)) found++
            sp.edit().putLong("tried_${st.id}", System.currentTimeMillis()).apply()
        }
        // Второй проход: подканалы, чей «родитель» нашёлся только что.
        if (found > 0) {
            for (st in todo) if (!has(st.id) && fromParent(st)) found++
        }
        return found to todo.size
    }

    private suspend fun findOne(st: Station): Boolean {
        // 1) Ссылка, которая уже есть у станции.
        if (st.logo.isNotBlank() && download(st.id, st.logo)) return true

        // 2) Каталог: та же станция (иногда логотип есть у другой записи с тем же названием).
        var homepage = st.homepage
        try {
            val matches = RadioBrowser.lookup(st)
            for (m in matches) {
                if (homepage.isBlank() && m.homepage.isNotBlank()) homepage = m.homepage
                if (m.favicon.isNotBlank() && m.favicon != st.logo && download(st.id, m.favicon)) {
                    StationRepo.setLogo(st.id, m.favicon)
                    if (homepage.isNotBlank()) StationRepo.setHomepage(st.id, homepage)
                    return true
                }
            }
        } catch (_: Exception) {
        }

        // 3) Подканал известной станции — её логотип с подписью.
        if (fromParent(st)) return true

        // 4) Сайт станции: из каталога или из заголовка потока icy-url.
        if (homepage.isBlank()) homepage = siteFromStream(st.url) ?: ""
        if (homepage.isNotBlank()) {
            StationRepo.setHomepage(st.id, homepage)
            val icon = siteIcon(homepage)
            if (icon != null) {
                save(st.id, icon)
                StationRepo.setLogo(st.id, "")
                return true
            }
        }
        return false
    }

    /** «Melodia FM Disco» → логотип «Мелодія FM» из твоего списка и подпись «Disco». */
    private fun fromParent(st: Station): Boolean {
        val key = Station.normalizeName(st.name)
        val parent = StationRepo.stations.value
            .filter { it.id != st.id && has(it.id) && it.logoBadge.isBlank() }
            .map { it to Station.normalizeName(it.name) }
            .filter { (_, k) -> k.length >= 3 && key.length > k.length && key.startsWith(k) }
            .maxByOrNull { (_, k) -> k.length }
            ?.first ?: return false
        val parentKeys = Station.words(parent.name).map { Station.normalizeName(it) }.toSet()
        val badge = Station.words(st.name)
            .filter { Station.normalizeName(it) !in parentKeys }
            .joinToString(" ")
            .take(14)
        val src = file(parent.id)
        return try {
            src.copyTo(file(st.id), overwrite = true)
            synchronized(this) {
                bitmaps.remove(st.id)
                bytes.remove(st.id)
                _version.value++
            }
            StationRepo.setLogo(st.id, "", badge)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- Сеть ----------

    suspend fun download(id: String, url: String): Boolean {
        val bmp = fetchBitmap(url) ?: return false
        save(id, bmp)
        return true
    }

    private suspend fun fetchBitmap(url: String, squareOnly: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val (c, _) = open(url) ?: return@withContext null
            val data = try {
                c.inputStream.use { it.readUpTo(3_000_000) }
            } finally {
                c.disconnect()
            }
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@withContext null
            if (bmp.width < 48 || bmp.height < 48) return@withContext null // крошечные иконки сайта выглядят плохо
            val ratio = bmp.width.toFloat() / bmp.height
            if (squareOnly && (ratio < 0.6f || ratio > 1.7f)) return@withContext null // широкие баннеры не годятся
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** Поток часто сообщает адрес сайта в заголовке icy-url. */
    private suspend fun siteFromStream(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val (c, _) = open(url, icy = true) ?: return@withContext null
            val site = c.getHeaderField("icy-url")?.trim()
            c.disconnect()
            site?.takeIf { it.startsWith("http") && !it.contains("localhost") }
        } catch (_: Exception) {
            null
        }
    }

    /** Иконка с сайта: для телефона (apple-touch-icon), большая обычная, картинка для соцсетей. */
    private suspend fun siteIcon(home: String): Bitmap? {
        val (html, base) = withContext(Dispatchers.IO) {
            try {
                val (c, finalUrl) = open(home) ?: return@withContext null
                val text = try {
                    String(c.inputStream.use { it.readUpTo(400_000) }, Charsets.UTF_8)
                } finally {
                    c.disconnect()
                }
                text to finalUrl
            } catch (_: Exception) {
                null
            }
        } ?: return fetchBitmap(rootOf(home) + "/apple-touch-icon.png")

        val candidates = mutableListOf<Pair<Int, String>>() // (приоритет, ссылка)
        val linkRe = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (m in linkRe.findAll(html)) {
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            val href = attr(tag, "href") ?: continue
            val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull() ?: 0
            when {
                rel.contains("apple-touch-icon") -> candidates += (300 + size) to href
                rel.contains("icon") && size >= 96 -> candidates += (200 + size) to href
            }
        }
        val metaRe = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (m in metaRe.findAll(html)) {
            val tag = m.value
            val prop = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
            if (prop == "og:image" || prop == "twitter:image") attr(tag, "content")?.let { candidates += 100 to it }
        }
        candidates += 50 to "/apple-touch-icon.png"

        for ((prio, href) in candidates.sortedByDescending { it.first }.distinctBy { it.second }) {
            val abs = try {
                URL(URL(base), href.replace("&amp;", "&")).toString()
            } catch (_: Exception) {
                continue
            }
            val bmp = fetchBitmap(abs, squareOnly = prio < 200) // картинки для соцсетей бывают широкими
            if (bmp != null) return bmp
        }
        return null
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let {
            it.groups[2]?.value ?: it.groups[3]?.value ?: it.groups[4]?.value
        }?.trim()?.takeIf { it.isNotEmpty() }

    private fun rootOf(url: String): String = try {
        val u = URL(url)
        "${u.protocol}://${u.host}"
    } catch (_: Exception) {
        url
    }

    /** Открывает ссылку и сам проходит перенаправления (в том числе http → https). */
    private fun open(start: String, icy: Boolean = false): Pair<HttpURLConnection, String>? {
        var u = start
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (icy) c.setRequestProperty("Icy-MetaData", "1")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) return null
                u = URL(URL(u), loc).toString()
            } else {
                if (code != 200) {
                    c.disconnect()
                    return null
                }
                return c to u
            }
        }
        return null
    }

    // ---------- Свои картинки и хранение ----------

    /** Своя картинка из галереи. */
    suspend fun fromUri(ctx: Context, id: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= SIZE && opts.outHeight / (sample * 2) >= SIZE) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            save(id, bmp)
            StationRepo.setLogo(id, "")
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun remove(id: String) {
        if (!::dir.isInitialized) return
        file(id).delete()
        bitmaps.remove(id)
        bytes.remove(id)
        sp.edit().remove("tried_$id").apply()
        _version.value++
    }

    /** Убрать навсегда: больше не искать автоматически (если поставили «Убрать логотип»). */
    fun dontSearch(id: String) {
        if (::sp.isInitialized) sp.edit().putLong("tried_$id", Long.MAX_VALUE / 2).apply()
    }

    private fun save(id: String, src: Bitmap) {
        val scale = minOf(1f, SIZE.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val png = out.toByteArray()
        synchronized(this) {
            file(id).writeBytes(png)
            bitmaps[id] = bmp
            bytes[id] = png
            _version.value++
        }
    }

    private fun java.io.InputStream.readUpTo(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        var total = 0
        while (total < max) {
            val n = read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
