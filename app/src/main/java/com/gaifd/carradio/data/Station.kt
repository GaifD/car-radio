package com.gaifd.carradio.data

import org.json.JSONObject

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val genre: String = "",
) {
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

    companion object {
        private val STOP_WORDS = setOf("fm", "radio", "радио", "радіо", "the", "ua", "online", "онлайн")

        fun fromJson(o: JSONObject) = Station(
            id = o.getString("id"),
            name = o.getString("name"),
            url = o.getString("url"),
            genre = o.optString("genre", ""),
        )
    }
}
