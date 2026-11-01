package com.gaifd.carradio.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.data.USER_AGENT
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Плеер для системы (уведомление, экран блокировки, Bluetooth-магнитола) выглядит как один,
 * а внутри — два ExoPlayer: пока новая станция грузится, старая играет, потом плавный переход.
 *
 * Плейлист = станции пользователя, «следующий трек» = следующая станция (кнопки на руле).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CrossfadePlayer(private val ctx: Context) : SimpleBasePlayer(Looper.getMainLooper()) {

    private companion object {
        const val SWITCH_DEBOUNCE_MS = 450L   // быстрое листание кнопками: грузим только конечную станцию
        const val INCOMING_TIMEOUT_MS = 20_000L
        const val STALE_PAUSE_MS = 20_000L    // после долгой паузы подключаемся заново, а не играем старый буфер
        const val SLOW_SPEED = 0.97f          // копим буфер: на 3% медленнее, высота звука не меняется
        const val OFFLINE_AFTER_MS = 4_000L

        val COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_RELEASE,
        ).build()

        val ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val am = ctx.getSystemService(AudioManager::class.java)

    private var stations: List<Station> = StationRepo.stations.value
    private var currentId: String? = Prefs.settings.value.lastStationId
        ?.takeIf { id -> stations.any { it.id == id } } ?: stations.firstOrNull()?.id

    private var active: ExoPlayer? = null
    private var activeId: String? = null
    private var activeTargetMs = 30_000L

    private var incoming: ExoPlayer? = null
    private var incomingId: String? = null
    private var incomingTargetMs = 30_000L
    private var incomingRetries = 0

    private var fadingOut: ExoPlayer? = null
    private var fadeRunnable: Runnable? = null
    private var pendingSwitch: Runnable? = null
    private val incomingTimeout = Runnable { incomingFailed("Станция не отвечает") }

    private var userPlay = false
    private var transientLoss = false
    private var ducked = false
    private var hasFocus = false
    private var pausedAt = 0L
    private var lastDirection = 0
    private var skipCount = 0

    private val titles = HashMap<ExoPlayer, String?>()
    private val counters = HashMap<ExoPlayer, AtomicLong>()
    private var rxFor: ExoPlayer? = null
    private var rxBytes = 0L
    private var rxAt = 0L
    private var rxKbps = 0.0
    private var codec: String? = null
    private var bitrateKbps = 0
    private var offline = false
    private var lastBuffered = 0L
    private var lastGrowAt = 0L
    private var error: String? = null

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener({ onFocus(it) }, handler)
        .build()

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, 500)
        }
    }

    init {
        handler.post(tick)
        scope.launch {
            StationRepo.stations.collect { list -> onStationsChanged(list) }
        }
        scope.launch {
            Prefs.settings.collect { invalidate() }
        }
    }

    // ---------- Состояние для системы ----------

    override fun getState(): State {
        val showTrack = Prefs.settings.value.showTrack
        val items = stations.map { st ->
            val track = if (st.id == currentId && showTrack) currentTrack()?.let { splitTrack(it) } else null
            // Магнитола показывает отдельными строками: песня / исполнитель / альбом (= станция).
            val meta = MediaMetadata.Builder()
                .setTitle(track?.title ?: st.name)
                .setArtist(track?.artist ?: if (track != null) st.name else "Авторадио")
                .setAlbumTitle(st.name)
                .setDisplayTitle(st.name)
                .setStation(st.name)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
            MediaItemData.Builder(st.id)
                .setMediaItem(mediaItem(st))
                .setMediaMetadata(meta)
                .setIsSeekable(false)
                .setIsDynamic(true)
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                .setDurationUs(C.TIME_UNSET)
                .build()
        }
        val b = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setAudioAttributes(ATTRS)
        if (items.isEmpty()) {
            return b.setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val loading = isLoadingNow()
        return b.setPlaylist(items)
            .setCurrentMediaItemIndex(currentIndex().coerceAtLeast(0))
            .setContentPositionMs(0)
            .setPlayWhenReady(userPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(
                if (userPlay && transientLoss) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                else Player.PLAYBACK_SUPPRESSION_REASON_NONE
            )
            .setPlaybackState(if (userPlay && loading) Player.STATE_BUFFERING else Player.STATE_READY)
            .setIsLoading(loading)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) startPlayback() else pausePlayback()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        pausePlayback()
        active?.let { releaseExo(it) }
        active = null
        activeId = null
        invalidate()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val dir = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> 1
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> -1
            else -> 0
        }
        val isDirectPick = seekCommand == Player.COMMAND_SEEK_TO_MEDIA_ITEM
        if (dir == 0 && !isDirectPick && mediaItemIndex == currentIndex()) {
            return Futures.immediateVoidFuture() // «перемотка в начало» живого эфира — ничего не делаем
        }
        select(mediaItemIndex, dir, immediate = dir == 0)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        cancelIncoming()
        finishFade()
        active?.let { releaseExo(it) }
        active = null
        abandonFocus()
        return Futures.immediateVoidFuture()
    }

    // ---------- Управление ----------

    private fun startPlayback() {
        if (stations.isEmpty()) return
        if (currentId == null || stations.none { it.id == currentId }) currentId = stations.first().id
        userPlay = true
        transientLoss = false
        error = null
        lastDirection = 0
        skipCount = 0
        requestFocus()
        beginLoad()
        invalidate()
    }

    private fun pausePlayback() {
        userPlay = false
        pendingSwitch?.let { handler.removeCallbacks(it) }
        pendingSwitch = null
        cancelIncoming()
        finishFade()
        active?.pause()
        pausedAt = SystemClock.uptimeMillis()
        abandonFocus()
        invalidate()
    }

    private fun select(index: Int, direction: Int, immediate: Boolean) {
        val st = stations.getOrNull(index) ?: return
        currentId = st.id
        lastDirection = direction
        skipCount = 0
        error = null
        Prefs.update { it.copy(lastStationId = st.id) }
        pendingSwitch?.let { handler.removeCallbacks(it) }
        pendingSwitch = null
        if (!userPlay) {
            cancelIncoming()
            invalidate()
            return
        }
        val r = Runnable { beginLoad() }
        pendingSwitch = r
        handler.postDelayed(r, if (immediate) 0L else SWITCH_DEBOUNCE_MS)
        invalidate()
    }

    private fun beginLoad() {
        pendingSwitch = null
        if (!userPlay) return
        val st = stations.firstOrNull { it.id == currentId } ?: return
        val a = active
        if (a != null && activeId == st.id && incoming == null) {
            val stale = !a.isPlaying && SystemClock.uptimeMillis() - pausedAt > STALE_PAUSE_MS
            if (a.playbackState != Player.STATE_IDLE && !stale) {
                if (!transientLoss) a.play()
                a.volume = level()
                invalidate()
                return
            }
        }
        if (incomingId == st.id && incoming != null) return
        cancelIncoming()
        val target = Prefs.settings.value.bufferSec * 1000L
        val exo = newExo(target)
        incoming = exo
        incomingId = st.id
        incomingTargetMs = target
        incomingRetries = 0
        exo.volume = 0f
        exo.setMediaItem(mediaItem(st))
        exo.prepare()
        exo.playWhenReady = !transientLoss
        handler.postDelayed(incomingTimeout, INCOMING_TIMEOUT_MS)
        invalidate()
    }

    /** Новая станция готова — делаем её основной и плавно переходим. */
    private fun promoteIncoming() {
        val inc = incoming ?: return
        handler.removeCallbacks(incomingTimeout)
        finishFade()
        val old = active
        val oldAudible = old != null && old.isPlaying
        active = inc
        activeId = incomingId
        activeTargetMs = incomingTargetMs
        incoming = null
        incomingId = null
        offline = false
        error = null
        skipCount = 0
        lastBuffered = 0
        lastGrowAt = SystemClock.uptimeMillis()

        val dur = Prefs.settings.value.crossfadeMs.toLong()
        when {
            dur <= 0 -> {
                inc.volume = level()
                old?.let { releaseExo(it) }
            }
            old == null || !oldAudible -> {
                old?.let { releaseExo(it) }
                fade(inc, null, min(dur, 800L)) // мягкое появление звука из тишины
            }
            else -> fade(inc, old, dur)
        }
        invalidate()
    }

    private fun incomingFailed(msg: String) {
        val inc = incoming ?: return
        handler.removeCallbacks(incomingTimeout)
        val failedName = stations.firstOrNull { it.id == incomingId }?.name ?: "Станция"
        incoming = null
        incomingId = null
        releaseExo(inc)

        // Листали кнопками и попали на мёртвую станцию — едем дальше в ту же сторону,
        // а пока ищем, продолжает играть то, что играло.
        if (userPlay && lastDirection != 0 && stations.size > 1 && skipCount < stations.size - 1) {
            skipCount++
            val idx = (currentIndex() + lastDirection).mod(stations.size)
            currentId = stations[idx].id
            Prefs.update { it.copy(lastStationId = currentId) }
            error = "$failedName не отвечает — переключаю дальше"
            beginLoad()
            invalidate()
            return
        }

        error = "$failedName: $msg"
        active?.let { if (activeId != currentId) { releaseExo(it); active = null; activeId = null } }
        userPlay = false
        abandonFocus()
        invalidate()
    }

    // ---------- Плавный переход ----------

    private fun fade(fadeIn: ExoPlayer, fadeOut: ExoPlayer?, dur: Long) {
        fadingOut = fadeOut
        val start = SystemClock.uptimeMillis()
        val r = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / dur).coerceIn(0f, 1f)
                val lv = level()
                // Равная мощность: середина перехода не проваливается по громкости.
                fadeIn.volume = sin(t * PI / 2).toFloat() * lv
                fadeOut?.volume = cos(t * PI / 2).toFloat() * lv
                if (t < 1f) {
                    handler.postDelayed(this, 30)
                } else {
                    fadeRunnable = null
                    fadingOut?.let { releaseExo(it) }
                    fadingOut = null
                }
            }
        }
        fadeRunnable = r
        handler.post(r)
    }

    private fun finishFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
        fadingOut?.let { releaseExo(it) }
        fadingOut = null
        active?.volume = level()
    }

    private fun level() = if (ducked) 0.25f else 1f

    // ---------- ExoPlayer ----------

    private fun newExo(targetMs: Long): ExoPlayer {
        val t = targetMs.toInt()
        val counter = AtomicLong(0)
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(t, t + 5_000, 2_500, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setTransferListener(ByteCounter(counter))
        val sources = DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(RetryPolicy())
        val exo = ExoPlayer.Builder(ctx)
            .setLoadControl(load)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(ATTRS, false) // фокусом управляем сами, на оба плеера сразу
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK) // не даёт уснуть процессору и Wi-Fi, пока играет
            .build()
        exo.addListener(ExoListener(exo))
        counters[exo] = counter
        return exo
    }

    /** Считает байты из сети — отсюда «приём N kbps». Вызывается в потоке загрузчика. */
    private class ByteCounter(private val bytes: AtomicLong) : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) bytes.addAndGet(bytesTransferred.toLong())
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }

    private fun readFormat(a: ExoPlayer) {
        val f = a.audioFormat
        if (f == null) {
            codec = null
            bitrateKbps = 0
            return
        }
        var br = 0
        f.metadata?.let { m ->
            for (i in 0 until m.length()) {
                val e = m.get(i)
                if (e is IcyHeaders && e.bitrate > 0) br = e.bitrate / 1000
            }
        }
        if (br <= 0) br = listOf(f.averageBitrate, f.bitrate, f.peakBitrate).firstOrNull { it > 0 }?.div(1000) ?: 0
        bitrateKbps = br
        codec = when (f.sampleMimeType) {
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            null -> null
            else -> f.sampleMimeType?.substringAfter('/')?.uppercase()
        }
    }

    private fun mediaItem(st: Station): MediaItem = MediaItem.Builder()
        .setMediaId(st.id)
        .setUri(st.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(st.name).setStation(st.name).build())
        .build()

    private fun releaseExo(e: ExoPlayer) {
        titles.remove(e)
        counters.remove(e)
        try {
            e.stop()
            e.release()
        } catch (_: Exception) {
        }
    }

    private fun cancelIncoming() {
        handler.removeCallbacks(incomingTimeout)
        incoming?.let { releaseExo(it) }
        incoming = null
        incomingId = null
    }

    /** Связь пропала — не сдаёмся: переподключаемся бесконечно, пока играет то, что успели накопить. */
    private class RetryPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = loadErrorInfo.exception
            val n = loadErrorInfo.errorCount
            if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499 && n > 2) return C.TIME_UNSET
            if (e is ParserException && n > 2) return C.TIME_UNSET
            return min(1000L * n, 5000L)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
    }

    private inner class ExoListener(private val exo: ExoPlayer) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (exo === incoming && playbackState == Player.STATE_READY) {
                promoteIncoming()
                return
            }
            if (exo === active) {
                if (playbackState == Player.STATE_ENDED && userPlay) {
                    // Сервер закрыл поток — просто подключаемся снова.
                    exo.seekToDefaultPosition()
                    exo.prepare()
                }
                invalidate()
            }
        }

        override fun onPlayerError(err: PlaybackException) {
            if (exo === incoming) {
                incomingRetries++
                if (incomingRetries > 2) {
                    incomingFailed(describe(err))
                } else {
                    handler.postDelayed({
                        if (exo === incoming) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                        }
                    }, 1500)
                }
            } else if (exo === active) {
                offline = true
                handler.postDelayed({
                    if (exo === active && userPlay) {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                        if (!transientLoss) exo.play()
                    }
                }, 2000)
                invalidate()
            }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val e = metadata.get(i)
                if (e is IcyInfo) {
                    titles[exo] = e.title?.trim()
                        ?.takeIf { it.length > 1 && it != "-" && !it.equals("unknown", ignoreCase = true) }
                    if (exo === active) invalidate()
                }
            }
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "нет связи со станцией"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "поток недоступен"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "формат не поддерживается"
        else -> "ошибка воспроизведения"
    }

    // ---------- Аудиофокус: звонки, навигатор ----------

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (hasFocus) am.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onFocus(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                ducked = false
                if (transientLoss && userPlay) {
                    active?.play()
                    incoming?.play()
                }
                transientLoss = false
                if (fadeRunnable == null) active?.volume = level()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true // навигатор говорит — приглушаем, не останавливаем
                if (fadeRunnable == null) active?.volume = level()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                transientLoss = true // звонок — пауза, после него продолжим
                finishFade()
                active?.pause()
                incoming?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausePlayback()
            }
        }
        invalidate()
    }

    // ---------- Буфер и связь ----------

    private fun onTick() {
        val a = active
        val now = SystemClock.uptimeMillis()
        var bufMs = 0L
        if (a != null && activeId == currentId) {
            measureRx(a, now)
            readFormat(a)
            bufMs = a.totalBufferedDuration
            val bp = a.bufferedPosition
            if (bp > lastBuffered + 50) {
                lastBuffered = bp
                lastGrowAt = now
                if (offline) offline = false
            } else if (bp + 1000 < lastBuffered) {
                lastBuffered = bp // переподключились, позиция сбросилась
                lastGrowAt = now
            }
            val playing = userPlay && !transientLoss
            if (playing && a.playbackState != Player.STATE_IDLE &&
                bufMs < activeTargetMs - 1000 && now - lastGrowAt > OFFLINE_AFTER_MS
            ) {
                if (!offline) {
                    offline = true
                    invalidate()
                }
            }
            val slow = playing && Prefs.settings.value.growBuffer && !offline &&
                a.playbackState == Player.STATE_READY && bufMs < activeTargetMs - 1500
            val speed = if (slow) SLOW_SPEED else 1f
            if (a.playbackParameters.speed != speed) a.playbackParameters = PlaybackParameters(speed)
        } else {
            rxFor = null
            rxKbps = 0.0
            codec = null
            bitrateKbps = 0
        }
        publish(bufMs)
    }

    private fun measureRx(a: ExoPlayer, now: Long) {
        val total = counters[a]?.get() ?: return
        if (rxFor !== a) {
            rxFor = a
            rxBytes = total
            rxAt = now
            rxKbps = 0.0
            return
        }
        val dt = now - rxAt
        if (dt < 400) return
        val inst = (total - rxBytes) * 8.0 / dt // бит/мс = kbps
        rxKbps = if (rxKbps == 0.0) inst else rxKbps * 0.7 + inst * 0.3 // сглаживаем, чтобы цифра не прыгала
        rxBytes = total
        rxAt = now
    }

    private fun onStationsChanged(list: List<Station>) {
        stations = list
        if (activeId != null && list.none { it.id == activeId }) {
            // Удалили станцию, которая играла.
            cancelIncoming()
            finishFade()
            active?.let { releaseExo(it) }
            active = null
            activeId = null
            userPlay = false
            abandonFocus()
        }
        if (currentId == null || list.none { it.id == currentId }) currentId = list.firstOrNull()?.id
        invalidate()
    }

    // ---------- Вспомогательное ----------

    private fun currentIndex() = stations.indexOfFirst { it.id == currentId }

    private fun currentTrack(): String? = active?.takeIf { activeId == currentId }?.let { titles[it] }

    private fun isLoadingNow(): Boolean = userPlay && (
        pendingSwitch != null || incoming != null || active == null || activeId != currentId ||
            active?.playbackState == Player.STATE_BUFFERING
        )

    private fun invalidate() {
        invalidateState()
        publish(active?.takeIf { activeId == currentId }?.totalBufferedDuration ?: 0L)
    }

    private fun publish(bufMs: Long) {
        val track = currentTrack()?.let { splitTrack(it) }
        val same = activeId == currentId
        RadioBus.state.value = RadioUi(
            stationId = currentId,
            playing = userPlay,
            loading = isLoadingNow(),
            trackTitle = track?.title,
            trackArtist = track?.artist,
            codec = if (same) codec else null,
            bitrateKbps = if (same) bitrateKbps else 0,
            rxKbps = if (same) rxKbps.toInt() else 0,
            bufferMs = bufMs,
            targetMs = if (activeId == currentId) activeTargetMs else Prefs.settings.value.bufferSec * 1000L,
            offline = offline && userPlay,
            interrupted = userPlay && transientLoss,
            error = error,
        )
    }
}
