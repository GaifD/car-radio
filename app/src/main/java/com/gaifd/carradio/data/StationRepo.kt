package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** Список станций пользователя. Один на весь процесс: и экран, и плеер видят одно и то же. */
object StationRepo {
    private lateinit var prefs: SharedPreferences
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations

    @Synchronized
    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("stations", Context.MODE_PRIVATE)
        _stations.value = load()
    }

    private fun load(): List<Station> = try {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun save(list: List<Station>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString("list", a.toString()).apply()
        _stations.value = list
    }

    fun contains(url: String) = _stations.value.any { it.url == url }

    fun add(s: Station) {
        val list = _stations.value
        if (list.any { it.url == s.url || it.id == s.id }) return
        save(list + s)
    }

    fun remove(id: String) = save(_stations.value.filterNot { it.id == id })

    fun move(id: String, delta: Int) {
        val list = _stations.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val s = list.removeAt(i)
        list.add(j, s)
        save(list)
    }

    fun addAlt(id: String, stream: Stream) = save(_stations.value.map { st ->
        if (st.id == id && st.streams.none { it.url == stream.url }) st.copy(alts = st.alts + stream) else st
    })

    fun removeAlt(id: String, url: String) = save(_stations.value.map { st ->
        if (st.id == id) st.copy(alts = st.alts.filterNot { it.url == url }) else st
    })

    /** Делает ссылку основной (например, если основная умерла насовсем). */
    fun makePrimary(id: String, url: String) = save(_stations.value.map { st ->
        val chosen = st.alts.firstOrNull { it.url == url }
        if (st.id != id || chosen == null) st
        else st.copy(url = chosen.url, bitrate = chosen.bitrate, alts = listOf(Stream(st.url, st.bitrate)) + st.alts.filterNot { it.url == url })
    })

    /** Импорт из файла: добавляем то, чего ещё нет, свои станции не трогаем. Возвращает, сколько добавлено. */
    fun import(list: List<Station>): Int {
        val cur = _stations.value
        val fresh = list.filter { n -> cur.none { it.id == n.id || it.url == n.url } }.distinctBy { it.url }
        if (fresh.isNotEmpty()) save(cur + fresh)
        return fresh.size
    }

    fun exportJson(): String {
        val a = JSONArray()
        _stations.value.forEach { a.put(it.toJson()) }
        return org.json.JSONObject().put("app", "car-radio").put("version", 1).put("stations", a).toString(2)
    }

    fun parseExport(text: String): List<Station> {
        val t = text.trim()
        val a = if (t.startsWith("[")) JSONArray(t) else org.json.JSONObject(t).getJSONArray("stations")
        return (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }
    }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        save(_stations.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }
}
