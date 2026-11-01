package com.gaifd.carradio.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** То, что плеер показывает экрану: буфер, связь, трек. Экран и плеер живут в одном процессе. */
data class RadioUi(
    val stationId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val bufferMs: Long = 0,
    val targetMs: Long = 30_000,
    val offline: Boolean = false,
    val interrupted: Boolean = false,
    val error: String? = null,
    val codec: String? = null,
    val bitrateKbps: Int = 0,   // заявленный битрейт потока
    val rxKbps: Int = 0,        // сколько реально приходит из сети сейчас
)

object RadioBus {
    val state = MutableStateFlow(RadioUi())
}

data class TrackInfo(val artist: String?, val title: String)

/** Станции шлют трек одной строкой «Исполнитель - Песня» — делим на два поля. */
fun splitTrack(raw: String): TrackInfo {
    for (sep in listOf(" - ", " – ", " — ", " | ")) {
        val i = raw.indexOf(sep)
        if (i > 0 && i + sep.length < raw.length) {
            val artist = raw.substring(0, i).trim()
            val title = raw.substring(i + sep.length).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) return TrackInfo(artist, title)
        }
    }
    return TrackInfo(null, raw.trim())
}
