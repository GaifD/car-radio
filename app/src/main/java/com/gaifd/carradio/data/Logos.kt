package com.gaifd.carradio.data

import android.content.Context
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
 * так что без интернета тоже видны. Нет логотипа — показывается буквенная плашка.
 */
object Logos {
    private const val SIZE = 256
    private lateinit var dir: File
    private val bitmaps = HashMap<String, Bitmap?>()
    private val bytes = HashMap<String, ByteArray?>()
    private val attempted = HashSet<String>()
    private val _version = MutableStateFlow(0)
    /** Меняется, когда появился или убран логотип — экран перерисовывается. */
    val version: StateFlow<Int> = _version

    @Synchronized
    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        dir = File(ctx.applicationContext.filesDir, "logos").apply { mkdirs() }
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

    suspend fun download(id: String, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var u = url
            var data: ByteArray? = null
            repeat(5) {
                if (data != null) return@repeat
                val c = URL(u).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", USER_AGENT)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc.isNullOrBlank()) return@withContext false
                    u = URL(URL(u), loc).toString()
                } else {
                    if (code != 200) {
                        c.disconnect()
                        return@withContext false
                    }
                    data = c.inputStream.use { s -> s.readNBytesCompat(3_000_000) }
                    c.disconnect()
                }
            }
            val d = data ?: return@withContext false
            val bmp = BitmapFactory.decodeByteArray(d, 0, d.size) ?: return@withContext false
            if (bmp.width < 32 || bmp.height < 32) return@withContext false // крошечные иконки сайта выглядят плохо
            save(id, bmp)
            true
        } catch (_: Exception) {
            false
        }
    }

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
        _version.value++
    }

    /** Докачать логотипы, которых ещё нет (один раз за запуск на станцию). */
    suspend fun ensure(stations: List<Station>) {
        for (st in stations) {
            if (st.logo.isBlank() || has(st.id)) continue
            synchronized(this) { if (!attempted.add(st.id)) return@synchronized null else Unit } ?: continue
            download(st.id, st.logo)
        }
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

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
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
