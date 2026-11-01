package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val time: Long, val station: String, val track: String)

/** Что играло: последние 300 треков. */
object History {
    private const val MAX = 300
    private lateinit var sp: SharedPreferences
    private val _items = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val items: StateFlow<List<HistoryEntry>> = _items

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
        _items.value = try {
            val a = JSONArray(sp.getString("list", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                HistoryEntry(o.getLong("t"), o.getString("s"), o.getString("k"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(station: String, track: String) {
        val cur = _items.value
        val last = cur.firstOrNull()
        if (last != null && last.track == track && last.station == station) return
        val list = (listOf(HistoryEntry(System.currentTimeMillis(), station, track)) + cur).take(MAX)
        _items.value = list
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.time).put("s", it.station).put("k", it.track)) }
        sp.edit().putString("list", a.toString()).apply()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        sp.edit().remove("list").apply()
    }
}
