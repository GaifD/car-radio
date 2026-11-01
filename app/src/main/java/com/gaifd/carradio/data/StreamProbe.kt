package com.gaifd.carradio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ProbeResult(val url: String, val name: String?)

/**
 * Проверяет ссылку перед сохранением: разворачивает плейлисты .m3u/.pls до настоящего потока,
 * убеждается, что сервер отдаёт звук, и берёт название станции из заголовка icy-name.
 */
object StreamProbe {

    suspend fun probe(input: String): ProbeResult = withContext(Dispatchers.IO) {
        var url = input.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "http://$url"

        for (step in 0 until 3) {
            val (c, finalUrl) = open(url)
            url = finalUrl
            try {
                val type = (c.contentType ?: "").lowercase()
                val path = url.lowercase().substringBefore('?')
                val isPls = type.contains("scpls") || path.endsWith(".pls")
                val isHls = path.endsWith(".m3u8") || type.contains("vnd.apple.mpegurl")
                val isM3u = !isHls && (type.contains("mpegurl") || path.endsWith(".m3u"))

                if (isHls) return@withContext ProbeResult(url, c.getHeaderField("icy-name"))

                if (isPls || isM3u) {
                    val body = c.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(20_000)
                        val n = r.read(buf)
                        if (n > 0) String(buf, 0, n) else ""
                    }
                    if (body.contains("#EXT-X-")) return@withContext ProbeResult(url, null)
                    val lines = body.lines().map { it.trim() }
                    val next = if (isPls) {
                        lines.firstOrNull { it.lowercase().startsWith("file") && it.contains('=') }
                            ?.substringAfter('=')?.trim()
                    } else {
                        lines.firstOrNull { it.startsWith("http", true) }
                    }
                    url = next ?: throw IOException("Плейлист пустой")
                    continue
                }

                val audio = type.startsWith("audio/") || type.contains("ogg") ||
                    type.contains("octet-stream") || type.contains("aac") || type.isBlank()
                if (!audio) throw IOException("Это не аудиопоток (${type.substringBefore(';')})")

                val got = c.inputStream.use { s ->
                    val b = ByteArray(4096)
                    var total = 0
                    while (total < 4096) {
                        val n = s.read(b, 0, b.size)
                        if (n <= 0) break
                        total += n
                    }
                    total
                }
                if (got == 0) throw IOException("Сервер не отдаёт звук")
                val name = c.getHeaderField("icy-name")?.trim()?.takeIf { it.isNotBlank() && it != "no name" }
                return@withContext ProbeResult(url, name)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Слишком много вложенных плейлистов")
    }

    /** Открывает соединение и сам проходит перенаправления, включая http → https. */
    private fun open(start: String): Pair<HttpURLConnection, String> {
        var u = start
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Icy-MetaData", "1")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw IOException("Пустое перенаправление")
                u = URL(URL(u), loc).toString()
            } else {
                if (code != 200) {
                    c.disconnect()
                    throw IOException("Сервер ответил $code")
                }
                return c to u
            }
        }
        throw IOException("Слишком много перенаправлений")
    }
}
