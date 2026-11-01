package com.gaifd.carradio.auto

import android.app.Activity
import android.app.SearchManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.Intent
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.playback.PlaybackService

/**
 * «Окей Google, включи Хіт FM в Авторадио». Ассистент присылает текст запроса,
 * находим похожую станцию и запускаем, не открывая приложение.
 */
class VoiceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        StationRepo.init(this)
        val query = intent?.getStringExtra(SearchManager.QUERY)
            ?: intent?.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
            ?: ""
        val stations = StationRepo.stations.value
        if (stations.isEmpty()) {
            Toast.makeText(this, "Сначала добавь станции в Авторадио", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val match = if (query.isBlank()) null else bestMatch(query, stations)
        if (query.isNotBlank() && match == null) {
            Toast.makeText(this, "Не нашёл станцию «$query» — включаю последнюю", Toast.LENGTH_LONG).show()
        }
        val i = Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_AUTOPLAY)
        match?.let { i.putExtra(PlaybackService.EXTRA_STATION, it.id) }
        try {
            ContextCompat.startForegroundService(this, i)
        } catch (_: Exception) {
        }
        finish()
    }

    companion object {
        /** Похожесть по названию: «хит фм», «hit fm», «Хіт» — всё найдёт «Хіт FM». */
        fun bestMatch(query: String, stations: List<Station>): Station? {
            val q = Station.normalizeName(
                query.lowercase()
                    .replace("в авторадио", "").replace("на авторадио", "").replace("авторадио", "")
                    .replace("включи", "").replace("радиостанцию", "").replace("станцию", "")
            )
            if (q.isBlank()) return null
            var best: Station? = null
            var bestScore = 0.0
            for (st in stations) {
                val n = Station.normalizeName(st.name)
                if (n.isBlank()) continue
                val score = when {
                    n == q -> 1.0
                    n.contains(q) || q.contains(n) -> 0.85 * minOf(n.length, q.length) / maxOf(n.length, q.length) + 0.15
                    else -> 1.0 - distance(n, q).toDouble() / maxOf(n.length, q.length)
                }
                if (score > bestScore) {
                    bestScore = score
                    best = st
                }
            }
            return if (bestScore >= 0.55) best else null
        }

        private fun distance(a: String, b: String): Int {
            val d = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = d[0]
                d[0] = i
                for (j in 1..b.length) {
                    val tmp = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return d[b.length]
        }
    }
}
