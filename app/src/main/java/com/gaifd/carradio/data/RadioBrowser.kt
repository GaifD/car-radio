package com.gaifd.carradio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val USER_AGENT = "CarRadio/1.0 (github.com/GaifD/car-radio)"

data class FoundStation(
    val uuid: String,
    val name: String,
    val url: String,
    val codec: String,
    val bitrate: Int,
    val country: String,
    val tags: String,
    val favicon: String = "",
) {
    val meta: String
        get() = listOf(
            country,
            listOf(codec, if (bitrate > 0) "$bitrate kbps" else "").filter { it.isNotBlank() }.joinToString(" "),
        ).filter { it.isNotBlank() }.joinToString(" · ")

    fun toStation(alts: List<Stream> = emptyList()) = Station(
        id = uuid,
        name = name.trim(),
        url = url,
        bitrate = bitrate,
        alts = alts,
        logo = favicon,
        genre = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2)
            .joinToString(" · ") { it.replaceFirstChar { c -> c.uppercase() } },
    )
}

/** Открытый каталог интернет-радио radio-browser.info. Несколько зеркал: если одно лежит, берём следующее. */
object RadioBrowser {
    private val hosts = listOf("de1", "de2", "fi1", "nl1", "at1")

    suspend fun search(query: String, ukraineOnly: Boolean): List<FoundStation> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val country = if (ukraineOnly) "&countrycode=UA" else ""
        var last: Exception? = null
        for (h in hosts) {
            try {
                val url = URL(
                    "https://$h.api.radio-browser.info/json/stations/search" +
                        "?name=$q$country&limit=40&hidebroken=true&order=clickcount&reverse=true"
                )
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", USER_AGENT)
                val text = try {
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
                val a = JSONArray(text)
                val list = (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    FoundStation(
                        uuid = o.optString("stationuuid"),
                        name = o.optString("name").trim(),
                        url = o.optString("url_resolved").ifBlank { o.optString("url") },
                        codec = o.optString("codec"),
                        bitrate = o.optInt("bitrate", 0),
                        country = o.optString("countrycode"),
                        tags = o.optString("tags"),
                        favicon = o.optString("favicon").takeIf { it.startsWith("http") } ?: "",
                    )
                }
                return@withContext list
                    .filter { it.uuid.isNotBlank() && it.name.isNotBlank() && it.url.startsWith("http") }
                    .distinctBy { it.url }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Каталог недоступен")
    }

    /** Другие ссылки той же станции в каталоге (часто есть варианты 64/128/320 kbps). */
    suspend fun findAlternatives(st: Station): List<Stream> {
        val key = Station.normalizeName(st.name)
        if (key.isBlank()) return emptyList()
        val found = search(st.name, ukraineOnly = false)
        return sameStation(key, found, st.streams.map { it.url }.toSet())
    }

    /** Логотип станции по названию (для станций, добавленных до появления логотипов). */
    suspend fun findLogo(st: Station): String? {
        val key = Station.normalizeName(st.name)
        val found = search(st.name, ukraineOnly = false)
        return (found.firstOrNull { it.uuid == st.id && it.favicon.isNotBlank() }
            ?: found.firstOrNull { Station.normalizeName(it.name) == key && it.favicon.isNotBlank() })?.favicon
    }

    fun sameStation(key: String, found: List<FoundStation>, exclude: Set<String>): List<Stream> =
        found.filter { Station.normalizeName(it.name) == key && it.url !in exclude }
            .distinctBy { it.url }
            .map { Stream(it.url, it.bitrate) }
            .sortedByDescending { it.bitrate }
            .take(4)
}
