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
    val bufferMode: Int = BUFFER_OFF,
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
    val stationView: Int = VIEW_GRID,
    val eqPreset: String = "off",
    val eqCustom: List<Int> = listOf(0, 0, 0, 0, 0),   // дБ на 60 / 230 / 910 / 3600 / 14000 Гц
    val scheduleOn: Boolean = false,
    val schedule: Map<String, String> = emptyMap(),    // утро/день/вечер/ночь → id станции
    val theme: Int = THEME_DARK,
    val accent: Int = DEFAULT_ACCENT,          // свой цвет приложения (ARGB)
    val accentSource: Int = ACCENT_FIXED,      // свой цвет или цвет обоев
) {
    /** Буфер копится: включён вручную, «Авто» на тарифицируемой сети (мобильный, точка доступа) или «Трасса». */
    fun bufferOn(metered: Boolean): Boolean =
        highway || bufferMode == BUFFER_ON || (bufferMode == BUFFER_AUTO && metered)

    /** Сколько держать впрок. Буфер выключен — лишь несколько секунд, без них поток не играет. «Трасса» — 3 минуты. */
    fun targetBufferMs(metered: Boolean): Long = when {
        !bufferOn(metered) -> MIN_BUFFER_MS
        highway -> HIGHWAY_BUFFER_MS
        else -> bufferSec * 1000L
    }

    companion object {
        const val HIGHWAY_BUFFER_MS = 180_000L
        const val MIN_BUFFER_MS = 5_000L

        const val BUFFER_OFF = 0
        const val BUFFER_ON = 1
        const val BUFFER_AUTO = 2

        const val VIEW_GRID = 0
        const val VIEW_LIST = 1
        const val VIEW_BIG = 2
        const val VIEW_COMPACT = 3

        const val THEME_SYSTEM = 0
        const val THEME_DARK = 1
        const val THEME_LIGHT = 2
        const val THEME_BY_TIME = 3

        const val DEFAULT_ACCENT = 0xFF2DE1D3.toInt()
        const val ACCENT_FIXED = 0
        const val ACCENT_WALLPAPER = 1

        /** Периоды для «станции по расписанию». */
        val PERIODS = listOf("morning" to "Утро · 5–11", "day" to "День · 11–17", "evening" to "Вечер · 17–23", "night" to "Ночь · 23–5")

        fun periodAt(hour: Int): String = when (hour) {
            in 5..10 -> "morning"
            in 11..16 -> "day"
            in 17..22 -> "evening"
            else -> "night"
        }
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
            bufferMode = sp.getInt("bufferMode", d.bufferMode),
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
            stationView = sp.getInt("stationView", d.stationView),
            eqPreset = sp.getString("eqPreset", d.eqPreset) ?: d.eqPreset,
            eqCustom = sp.getString("eqCustom", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
                ?.takeIf { it.size == 5 } ?: d.eqCustom,
            scheduleOn = sp.getBoolean("scheduleOn", d.scheduleOn),
            schedule = sp.getString("schedule", "")!!.split(';').mapNotNull {
                val k = it.substringBefore('=', ""); val v = it.substringAfter('=', "")
                if (k.isNotBlank() && v.isNotBlank()) k to v else null
            }.toMap(),
            theme = sp.getInt("theme", d.theme),
            accent = sp.getInt("accent", d.accent),
            accentSource = sp.getInt("accentSource", d.accentSource),
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
            .putInt("bufferMode", s.bufferMode)
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
            .putInt("stationView", s.stationView)
            .putString("eqPreset", s.eqPreset)
            .putString("eqCustom", s.eqCustom.joinToString(","))
            .putBoolean("scheduleOn", s.scheduleOn)
            .putString("schedule", s.schedule.entries.joinToString(";") { "${it.key}=${it.value}" })
            .putInt("theme", s.theme)
            .putInt("accent", s.accent)
            .putInt("accentSource", s.accentSource)
            .apply()
    }

    var askedNotifications: Boolean
        get() = sp.getBoolean("askedNotif", false)
        set(v) = sp.edit().putBoolean("askedNotif", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    /** Подобранное выравниванием усиление для станции — чтобы при следующем включении начать сразу с него. */
    fun gain(stationId: String): Float = gains.getFloat(stationId, 1f)
    fun setGain(stationId: String, g: Float) = gains.edit().putFloat(stationId, g).apply()
}
