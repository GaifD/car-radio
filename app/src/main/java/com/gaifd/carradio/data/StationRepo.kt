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

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        save(_stations.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }
}
