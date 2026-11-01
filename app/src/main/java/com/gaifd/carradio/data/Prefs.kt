package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val crossfadeMs: Int = 1500,
    val bufferSec: Int = 30,
    val growBuffer: Boolean = true,
    val autoPlay: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val limitStartVolume: Boolean = false,
    val showTrack: Boolean = true,
    val carMac: String? = null,
    val carName: String? = null,
    val lastStationId: String? = null,
    val searchUkraine: Boolean = true,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = Settings()
        _settings.value = Settings(
            crossfadeMs = sp.getInt("crossfadeMs", d.crossfadeMs),
            bufferSec = sp.getInt("bufferSec", d.bufferSec),
            growBuffer = sp.getBoolean("growBuffer", d.growBuffer),
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
}
