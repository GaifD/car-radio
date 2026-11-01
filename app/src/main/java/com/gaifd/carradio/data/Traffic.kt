package com.gaifd.carradio.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class TrafficStats(
    val todayTotal: Long = 0,
    val todayMobile: Long = 0,
    val monthTotal: Long = 0,
    val monthMobile: Long = 0,
)

/** Сколько радио скачало: всего и через мобильную сеть, за сегодня и за месяц. */
object Traffic {
    private val pending = AtomicLong(0)
    private lateinit var sp: SharedPreferences
    private lateinit var cm: ConnectivityManager
    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("traffic", Context.MODE_PRIVATE)
        cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        recompute()
    }

    /** Из потоков загрузки — только копим, без записи на диск. */
    fun add(bytes: Long) {
        pending.addAndGet(bytes)
    }

    /** Раз в несколько секунд переносим накопленное в статистику. */
    @Synchronized
    fun flush() {
        if (!::sp.isInitialized) return
        val b = pending.getAndSet(0)
        if (b <= 0) return
        val mobile = try {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (_: Exception) {
            false
        }
        val day = dayFmt.format(Date())
        val e = sp.edit()
        e.putLong("t_$day", sp.getLong("t_$day", 0) + b)
        if (mobile) e.putLong("m_$day", sp.getLong("m_$day", 0) + b)
        e.apply()
        recompute()
    }

    @Synchronized
    fun reset() {
        if (!::sp.isInitialized) return
        sp.edit().clear().apply()
        recompute()
    }

    private fun recompute() {
        val today = dayFmt.format(Date())
        val month = today.substring(0, 7)
        var mt = 0L
        var mm = 0L
        val old = mutableListOf<String>()
        for ((k, v) in sp.all) {
            val n = (v as? Long) ?: continue
            val d = k.substringAfter('_')
            if (d.startsWith(month)) {
                if (k.startsWith("t_")) mt += n else if (k.startsWith("m_")) mm += n
            } else if (d < month) {
                old += k
            }
        }
        if (old.size > 120) sp.edit().apply { old.forEach { remove(it) } }.apply() // чистим старьё
        _stats.value = TrafficStats(
            todayTotal = sp.getLong("t_$today", 0),
            todayMobile = sp.getLong("m_$today", 0),
            monthTotal = mt,
            monthMobile = mm,
        )
    }
}
