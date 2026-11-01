package com.gaifd.carradio.data

import org.json.JSONArray
import org.json.JSONObject

/** Одна ссылка на поток станции. bitrate = 0, если неизвестен. */
data class Stream(val url: String, val bitrate: Int = 0)

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val genre: String = "",
    val bitrate: Int = 0,
    val alts: List<Stream> = emptyList(),
    val logo: String = "",          // ссылка на логотип из каталога (картинка хранится локально)
) {
    /** Основная ссылка первой, затем запасные. */
    val streams: List<Stream> get() = listOf(Stream(url, bitrate)) + alts

    /** Короткая подпись для плитки: «Хіт FM» → «ХІТ», «Radio ROKS» → «ROKS». */
    val short: String
        get() {
            val words = name.split(' ', '-', '.', '_').filter { it.isNotBlank() }
            val meaningful = words.filter { it.lowercase() !in STOP_WORDS }.ifEmpty { words }
            val first = meaningful.firstOrNull() ?: return "?"
            return when {
                first.length <= 5 -> first.uppercase()
                meaningful.size >= 2 -> meaningful.take(3).joinToString("") { it.take(1) }.uppercase()
                else -> first.take(4).uppercase()
            }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("genre", genre)
        .put("bitrate", bitrate)
        .put("logo", logo)
        .put("alts", JSONArray().also { a ->
            alts.forEach { a.put(JSONObject().put("url", it.url).put("bitrate", it.bitrate)) }
        })

    companion object {
        private val STOP_WORDS = setOf("fm", "radio", "радио", "радіо", "the", "ua", "online", "онлайн")

        fun fromJson(o: JSONObject): Station {
            val alts = o.optJSONArray("alts")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val s = a.optJSONObject(i) ?: return@mapNotNull null
                    val u = s.optString("url")
                    if (u.isBlank()) null else Stream(u, s.optInt("bitrate", 0))
                }
            } ?: emptyList()
            return Station(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                genre = o.optString("genre", ""),
                bitrate = o.optInt("bitrate", 0),
                alts = alts,
                logo = o.optString("logo", ""),
            )
        }

        /** Для сравнения названий: регистр, «FM/радио», украинские буквы и латиница — не важны. */
        fun normalizeName(s: String): String {
            val words = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() && it !in STOP_WORDS }
            return translit(words.joinToString(""))
        }

        private val TR = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ґ' to "g", 'д' to "d", 'е' to "e", 'є' to "e",
            'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'і' to "i", 'ї' to "i", 'й' to "i", 'к' to "k",
            'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
            'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "u", 'я' to "a", '\'' to "", '’' to "",
        )

        private fun translit(s: String): String {
            val sb = StringBuilder()
            for (ch in s) sb.append(TR[ch] ?: ch.toString())
            // Сглаживаем латинские варианты одного звука: kiss/кисс → kis, hit/хит → hit, x → ks
            return sb.toString()
                .replace("x", "ks").replace("w", "v").replace("y", "i").replace("q", "k")
                .replace("ck", "k").replace("ph", "f").replace("kh", "h")
                .replace(Regex("(.)\\1+"), "$1")
        }
    }
}
