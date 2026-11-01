package com.gaifd.carradio.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** То, что плеер показывает экрану: буфер, связь, трек. Экран и плеер живут в одном процессе. */
data class RadioUi(
    val stationId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val track: String? = null,
    val bufferMs: Long = 0,
    val targetMs: Long = 30_000,
    val offline: Boolean = false,
    val interrupted: Boolean = false,
    val error: String? = null,
)

object RadioBus {
    val state = MutableStateFlow(RadioUi())
}
