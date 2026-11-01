package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val crossfadeMs: Int = 1500,
    val bufferSec: Int = 30,
    val growBuffer: Boolean = true,
    val smartPause: Boolean = true,
    val highway: Boolean = false,
    val warmupSec: Int = 0,
    val fallback: Boolean = true,
    val lowerQuality: Boolean = true,
    val leveler: Boolean = false,
    val autoPlay: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val limitStartVolume: Boolean = false,
    val showTrack: Boolean = true,
    val carMac: String? = null,
    val carName: String? = null,
    val lastStationId: String? = null,
    val searchUkraine: Boolean = true,
) {
    /** Режим «Трасса» — запас до 3 минут. */
    val targetBufferMs: Long get() = if (highway) HIGHWAY_BUFFER_MS else bufferSec * 1000L

    companion object {
        const val HIGHWAY_BUFFER_MS = 180_000L
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var gains: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        gains = ctx.applicationContext.getSharedPreferences("gains", Context.MODE_PRIVATE)
        val d = Settings()
        _settings.value = Settings(
            crossfadeMs = sp.getInt("crossfadeMs", d.crossfadeMs),
            bufferSec = sp.getInt("bufferSec", d.bufferSec),
            growBuffer = sp.getBoolean("growBuffer", d.growBuffer),
            smartPause = sp.getBoolean("smartPause", d.smartPause),
            highway = sp.getBoolean("highway", d.highway),
            warmupSec = sp.getInt("warmupSec", d.warmupSec),
            fallback = sp.getBoolean("fallback", d.fallback),
            lowerQuality = sp.getBoolean("lowerQuality", d.lowerQuality),
            leveler = sp.getBoolean("leveler", d.leveler),
            autoPlay = sp.getBoolean("autoPlay", d.autoPlay),
            pauseOnDisconnect = sp.getBoolean("pauseOnDisconnect", d.pauseOnDisconnect),
            limitStartVolume = sp.getBoolean("limitStartVolume", d.limitStartVolume),
            showTrack = sp.getBoolean("showTrack", d.showTrack),
            carMac = sp.getString("carMac", null),
            carName = sp.getString("carName", null),
            lastStationId = sp.getString("lastStationId", null),
            searchUkraine = sp.getBoolean("searchUkraine", d.searchUkraine),
        )
    }

    @Synchronized
    fun update(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        if (s == _settings.value) return
        _settings.value = s
        sp.edit()
            .putInt("crossfadeMs", s.crossfadeMs)
            .putInt("bufferSec", s.bufferSec)
            .putBoolean("growBuffer", s.growBuffer)
            .putBoolean("smartPause", s.smartPause)
            .putBoolean("highway", s.highway)
            .putInt("warmupSec", s.warmupSec)
            .putBoolean("fallback", s.fallback)
            .putBoolean("lowerQuality", s.lowerQuality)
            .putBoolean("leveler", s.leveler)
            .putBoolean("autoPlay", s.autoPlay)
            .putBoolean("pauseOnDisconnect", s.pauseOnDisconnect)
            .putBoolean("limitStartVolume", s.limitStartVolume)
            .putBoolean("showTrack", s.showTrack)
            .putString("carMac", s.carMac)
            .putString("carName", s.carName)
            .putString("lastStationId", s.lastStationId)
            .putBoolean("searchUkraine", s.searchUkraine)
            .apply()
    }

    var askedNotifications: Boolean
        get() = sp.getBoolean("askedNotif", false)
        set(v) = sp.edit().putBoolean("askedNotif", v).apply()

    /** Подобранное выравниванием усиление для станции — чтобы при следующем включении начать сразу с него. */
    fun gain(stationId: String): Float = gains.getFloat(stationId, 1f)
    fun setGain(stationId: String, g: Float) = gains.edit().putFloat(stationId, g).apply()
}
