package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Favorite(val time: Long, val station: String, val track: String)

/** Понравившиеся песни. */
object Favorites {
    private lateinit var sp: SharedPreferences
    private val _items = MutableStateFlow<List<Favorite>>(emptyList())
    val items: StateFlow<List<Favorite>> = _items

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        _items.value = try {
            val a = JSONArray(sp.getString("list", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Favorite(o.getLong("t"), o.getString("s"), o.getString("k"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun contains(track: String) = _items.value.any { it.track.equals(track, ignoreCase = true) }

    @Synchronized
    fun toggle(station: String, track: String) {
        val cur = _items.value
        val list = if (contains(track)) cur.filterNot { it.track.equals(track, ignoreCase = true) }
        else listOf(Favorite(System.currentTimeMillis(), station, track)) + cur
        _items.value = list
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.time).put("s", it.station).put("k", it.track)) }
        sp.edit().putString("list", a.toString()).apply()
    }
}

data class StationStat(val stationId: String, val name: String, val ms: Long)

/** Сколько времени слушал каждую станцию — за месяц и за всё время. */
object Stats {
    private lateinit var sp: SharedPreferences
    private val pending = HashMap<String, Long>()
    private val names = HashMap<String, String>()
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("stats", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(stationId: String, name: String, ms: Long) {
        pending[stationId] = (pending[stationId] ?: 0) + ms
        names[stationId] = name
    }

    @Synchronized
    fun flush() {
        if (!::sp.isInitialized || pending.isEmpty()) return
        val month = monthFmt.format(Date())
        val e = sp.edit()
        for ((id, ms) in pending) {
            e.putLong("m|$month|$id", sp.getLong("m|$month|$id", 0) + ms)
            e.putLong("all|$id", sp.getLong("all|$id", 0) + ms)
            names[id]?.let { e.putString("n|$id", it) }
        }
        e.apply()
        pending.clear()
        _version.value++
    }

    @Synchronized
    fun month(): List<StationStat> {
        if (!::sp.isInitialized) return emptyList()
        val prefix = "m|${monthFmt.format(Date())}|"
        return collect(prefix)
    }

    @Synchronized
    fun allTime(): List<StationStat> = if (!::sp.isInitialized) emptyList() else collect("all|")

    private fun collect(prefix: String): List<StationStat> =
        sp.all.mapNotNull { (k, v) ->
            if (!k.startsWith(prefix)) return@mapNotNull null
            val id = k.removePrefix(prefix)
            val ms = (v as? Long) ?: return@mapNotNull null
            StationStat(id, sp.getString("n|$id", null) ?: "Удалённая станция", ms)
        }.sortedByDescending { it.ms }

    @Synchronized
    fun reset() {
        if (!::sp.isInitialized) return
        sp.edit().clear().apply()
        pending.clear()
        _version.value++
    }
}
