package com.gaifd.carradio.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.gaifd.carradio.data.History
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.data.Stream
import com.gaifd.carradio.data.Traffic
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Плеер для системы (уведомление, экран блокировки, Bluetooth-магнитола) выглядит как один,
 * а внутри — два ExoPlayer: пока новая станция грузится, старая играет, потом плавный переход.
 *
 * Плейлист = станции пользователя, «следующий трек» = следующая станция (кнопки на руле).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CrossfadePlayer(private val ctx: Context) : SimpleBasePlayer(Looper.getMainLooper()), RadioControl {

    private companion object {
        const val SWITCH_DEBOUNCE_MS = 450L     // быстрое листание кнопками: грузим только конечную станцию
        const val INCOMING_TIMEOUT_MS = 20_000L
        const val STALE_PAUSE_MS = 20_000L      // без «умной паузы»: после такой паузы — заново к эфиру
        const val SLOW_SPEED = 0.97f            // копим буфер: на 3% медленнее, высота звука не меняется
        const val OFFLINE_AFTER_MS = 4_000L
        const val FAILOVER_AFTER_MS = 15_000L   // поток завис, а интернет есть — пробуем запасную ссылку
        const val LOW_QUALITY_AFTER_MS = 15_000L

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

    /** Один внутренний плеер и всё, что к нему относится. */
    private inner class Slot(val stationId: String, val streamIdx: Int, val stream: Stream, val targetMs: Long, val noFade: Boolean) {
        val counter = AtomicLong(0)
        val leveler = Leveler(Prefs.gain(stationId)).also { it.enabled = Prefs.settings.value.leveler }
        val exo: ExoPlayer = buildExo(this)
        var title: String? = null
        var retries = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    private var stations: List<Station> = StationRepo.stations.value
    private var currentId: String? = Prefs.settings.value.lastStationId
        ?.takeIf { id -> stations.any { it.id == id } } ?: stations.firstOrNull()?.id

    private var active: Slot? = null
    private var incoming: Slot? = null
    private var fadingOut: Slot? = null
    private var fadeRunnable: Runnable? = null
    private var rampRunnable: Runnable? = null
    private var pendingSwitch: Runnable? = null
    private val incomingTimeout = Runnable { incomingFailed("станция не отвечает") }

    private var userPlay = false
    private var transientLoss = false
    private var ducked = false
    private var hasFocus = false
    private var pausedAt = 0L
    private var lastDirection = 0
    private var skipCount = 0
    private val tried = mutableSetOf<Int>()            // какие ссылки текущей станции уже не ответили
    private val preferredIdx = HashMap<String, Int>()  // какая ссылка станции сработала последней

    private var stockTargetMs = 0L
    private var offline = false
    private var offlineSince = 0L
    private var lastFailoverAt = 0L
    private var lowSince = 0L
    private var lastBuffered = 0L
    private var lastGrowAt = 0L
    private var error: String? = null
    private var lastTarget = Prefs.settings.value.targetBufferMs
    private var lastLeveler = Prefs.settings.value.leveler

    private var rxFor: Slot? = null
    private var rxBytes = 0L
    private var rxAt = 0L
    private var rxKbps = 0.0
    private var codec: String? = null
    private var bitrateKbps = 0
    private var ticks = 0

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
        RadioBus.control = this
        handler.post(tick)
        scope.launch {
            StationRepo.stations.collect { list -> onStationsChanged(list) }
        }
        scope.launch {
            Prefs.settings.collect { s ->
                if (s.leveler != lastLeveler) {
                    lastLeveler = s.leveler
                    listOfNotNull(active, incoming, fadingOut).forEach { it.leveler.enabled = s.leveler }
                }
                if (s.targetBufferMs != lastTarget) {
                    lastTarget = s.targetBufferMs
                    // Новый размер буфера («Трасса» и т.п.) — переподключаем текущую станцию сразу.
                    val a = active
                    if (userPlay && a != null && a.stationId == currentId && stockTargetMs == 0L) {
                        beginLoad(streamIndex = a.streamIdx, reload = true)
                    }
                }
                invalidate()
            }
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
                .setMediaItem(mediaItem(st, st.url))
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
        active?.let { releaseSlot(it) }
        active = null
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
        if (RadioBus.control === this) RadioBus.control = null
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        cancelIncoming()
        finishFade()
        active?.let { releaseSlot(it) }
        active = null
        abandonFocus()
        Traffic.flush()
        return Futures.immediateVoidFuture()
    }

    // ---------- Команды экрана ----------

    /** «Запастись»: звук плавно затихает, буфер копится до полного, потом радио продолжает с того же места. */
    override fun stockUp() = stockUp(Prefs.settings.value.targetBufferMs)

    fun stockUp(requestMs: Long) {
        if (stations.isEmpty()) return
        if (!userPlay) startPlayback()
        val target = Prefs.settings.value.targetBufferMs
        stockTargetMs = min(requestMs, target - 2_000).coerceAtLeast(3_000)
        val a = active
        if (a != null && a.stationId == currentId && a.exo.isPlaying) {
            finishFade()
            ramp(a, a.exo.volume, 0f, 600) { if (stockTargetMs > 0) a.exo.pause() }
        }
        invalidate()
    }

    override fun playNow() {
        if (stockTargetMs == 0L) return
        stockTargetMs = 0
        resumeAfterStock()
    }

    fun selectById(id: String) {
        val idx = stations.indexOfFirst { it.id == id }
        if (idx >= 0) select(idx, 0, immediate = true)
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
        tried.clear()
        requestFocus()
        beginLoad()
        invalidate()
    }

    private fun pausePlayback() {
        userPlay = false
        stockTargetMs = 0
        pendingSwitch?.let { handler.removeCallbacks(it) }
        pendingSwitch = null
        cancelIncoming()
        finishFade()
        active?.exo?.pause()
        pausedAt = SystemClock.uptimeMillis()
        abandonFocus()
        invalidate()
    }

    private fun select(index: Int, direction: Int, immediate: Boolean) {
        val st = stations.getOrNull(index) ?: return
        currentId = st.id
        lastDirection = direction
        skipCount = 0
        tried.clear()
        error = null
        stockTargetMs = 0
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

    /**
     * Запускает загрузку текущей станции во второй плеер.
     * streamIndex — какую ссылку взять (запасная, облегчённая); reload — переподключить ту же.
     */
    private fun beginLoad(streamIndex: Int? = null, reload: Boolean = false) {
        pendingSwitch = null
        if (!userPlay) return
        val st = stations.firstOrNull { it.id == currentId } ?: return
        val s = Prefs.settings.value
        val a = active
        if (!reload && streamIndex == null && a != null && a.stationId == st.id && incoming == null) {
            val pausedFor = SystemClock.uptimeMillis() - pausedAt
            // Умная пауза: пока пауза не дольше запаса — продолжаем с того же места, буфер не теряем.
            val limit = if (s.smartPause) max(a.targetMs + 5_000, 30_000) else STALE_PAUSE_MS
            val stale = !a.exo.isPlaying && pausedAt > 0 && pausedFor > limit
            if (a.exo.playbackState != Player.STATE_IDLE && !stale) {
                if (!transientLoss && stockTargetMs == 0L) a.exo.play()
                a.exo.volume = level()
                invalidate()
                return
            }
        }
        val idx = (streamIndex ?: preferredIdx[st.id] ?: 0).coerceIn(0, st.streams.size - 1)
        val inc = incoming
        if (!reload && inc != null && inc.stationId == st.id && inc.streamIdx == idx) return
        cancelIncoming()
        val slot = Slot(st.id, idx, st.streams[idx], s.targetBufferMs, noFade = reload)
        incoming = slot
        slot.exo.volume = 0f
        slot.exo.setMediaItem(mediaItem(st, slot.stream.url))
        slot.exo.prepare()
        slot.exo.playWhenReady = !transientLoss && stockTargetMs == 0L
        handler.postDelayed(incomingTimeout, INCOMING_TIMEOUT_MS)
        invalidate()
    }

    /** Новая станция готова — делаем её основной и плавно переходим. */
    private fun promoteIncoming() {
        val inc = incoming ?: return
        handler.removeCallbacks(incomingTimeout)
        finishFade()
        val old = active
        val oldAudible = old != null && old.exo.isPlaying
        active = inc
        incoming = null
        preferredIdx[inc.stationId] = inc.streamIdx
        tried.clear()
        offline = false
        offlineSince = 0
        lowSince = 0
        skipCount = 0
        if (error?.contains("запасн") != true && error?.contains("облегч") != true) error = null
        lastBuffered = 0
        lastGrowAt = SystemClock.uptimeMillis()
        inc.title?.let { t -> stationName(inc.stationId)?.let { History.add(it, t) } }

        if (stockTargetMs > 0) {
            // «Запастись» / прогрев: копим молча.
            old?.let { releaseSlot(it) }
            inc.exo.volume = 0f
            inc.exo.pause()
            invalidate()
            return
        }

        val dur = if (inc.noFade) 0L else Prefs.settings.value.crossfadeMs.toLong()
        when {
            dur <= 0 -> {
                inc.exo.volume = level()
                old?.let { releaseSlot(it) }
            }
            old == null || !oldAudible -> {
                old?.let { releaseSlot(it) }
                fade(inc, null, min(dur, 800L)) // мягкое появление звука из тишины
            }
            else -> fade(inc, old, dur)
        }
        invalidate()
    }

    private fun incomingFailed(msg: String) {
        val inc = incoming ?: return
        handler.removeCallbacks(incomingTimeout)
        val st = stations.firstOrNull { it.id == inc.stationId }
        val failedName = st?.name ?: "Станция"
        incoming = null
        releaseSlot(inc)

        // Есть другие ссылки этой станции — пробуем их.
        if (userPlay && st != null && Prefs.settings.value.fallback && st.streams.size > 1) {
            tried += inc.streamIdx
            val next = st.streams.indices.firstOrNull { it !in tried }
            if (next != null) {
                error = "$failedName: ссылка не отвечает — пробую запасную"
                beginLoad(streamIndex = next)
                invalidate()
                return
            }
        }

        // Листали кнопками и попали на мёртвую станцию — едем дальше в ту же сторону,
        // а пока ищем, продолжает играть то, что играло.
        if (userPlay && lastDirection != 0 && stations.size > 1 && skipCount < stations.size - 1) {
            skipCount++
            tried.clear()
            val idx = (currentIndex() + lastDirection).mod(stations.size)
            currentId = stations[idx].id
            Prefs.update { it.copy(lastStationId = currentId) }
            error = "$failedName не отвечает — переключаю дальше"
            beginLoad()
            invalidate()
            return
        }

        val a = active
        if (a != null && a.stationId == inc.stationId) {
            // Не удалась запасная/облегчённая ссылка, а старая ещё играет — остаёмся на ней.
            error = "$failedName: $msg, остаюсь на текущей ссылке"
            invalidate()
            return
        }
        error = "$failedName: $msg"
        a?.let { releaseSlot(it) }
        active = null
        userPlay = false
        stockTargetMs = 0
        abandonFocus()
        invalidate()
    }

    private fun resumeAfterStock() {
        val a = active
        if (a == null || a.stationId != currentId) {
            beginLoad()
            return
        }
        rampRunnable?.let { handler.removeCallbacks(it) }
        rampRunnable = null
        a.exo.volume = 0f
        if (!transientLoss) a.exo.play()
        fade(a, null, 900)
        invalidate()
    }

    // ---------- Плавный переход ----------

    private fun fade(fadeIn: Slot, fadeOut: Slot?, dur: Long) {
        fadingOut = fadeOut
        val start = SystemClock.uptimeMillis()
        val r = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / dur).coerceIn(0f, 1f)
                val lv = level()
                // Равная мощность: середина перехода не проваливается по громкости.
                fadeIn.exo.volume = sin(t * PI / 2).toFloat() * lv
                fadeOut?.exo?.volume = cos(t * PI / 2).toFloat() * lv
                if (t < 1f) {
                    handler.postDelayed(this, 30)
                } else {
                    fadeRunnable = null
                    fadingOut?.let { releaseSlot(it) }
                    fadingOut = null
                }
            }
        }
        fadeRunnable = r
        handler.post(r)
    }

    private fun ramp(slot: Slot, from: Float, to: Float, dur: Long, then: () -> Unit) {
        rampRunnable?.let { handler.removeCallbacks(it) }
        val start = SystemClock.uptimeMillis()
        val r = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - start).toFloat() / dur).coerceIn(0f, 1f)
                slot.exo.volume = from + (to - from) * t
                if (t < 1f) handler.postDelayed(this, 30) else {
                    rampRunnable = null
                    then()
                }
            }
        }
        rampRunnable = r
        handler.post(r)
    }

    private fun finishFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
        rampRunnable?.let { handler.removeCallbacks(it) }
        rampRunnable = null
        fadingOut?.let { releaseSlot(it) }
        fadingOut = null
        if (stockTargetMs == 0L) active?.exo?.volume = level()
    }

    private fun level() = if (ducked) 0.25f else 1f

    // ---------- ExoPlayer ----------

    private fun buildExo(slot: Slot): ExoPlayer {
        val t = slot.targetMs.toInt()
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(t, t + 5_000, 2_500, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setTransferListener(ByteCounter(slot.counter))
        val sources = DefaultMediaSourceFactory(ctx)
            .setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(RetryPolicy())
        // Своя цепочка обработки звука: выравнивание громкости перед выводом.
        val renderers = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(slot.leveler))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        val exo = ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(load)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(ATTRS, false) // фокусом управляем сами, на оба плеера сразу
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK) // не даёт уснуть процессору и Wi-Fi, пока играет
            .build()
        exo.addListener(ExoListener(slot))
        return exo
    }

    private fun mediaItem(st: Station, url: String): MediaItem = MediaItem.Builder()
        .setMediaId(st.id)
        .setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(st.name).setStation(st.name).build())
        .build()

    private fun releaseSlot(s: Slot) {
        if (Prefs.settings.value.leveler) Prefs.setGain(s.stationId, s.leveler.gain)
        try {
            s.exo.stop()
            s.exo.release()
        } catch (_: Exception) {
        }
    }

    private fun cancelIncoming() {
        handler.removeCallbacks(incomingTimeout)
        incoming?.let { releaseSlot(it) }
        incoming = null
    }

    /** Считает байты из сети — «приём N kbps» и счётчик трафика. Вызывается в потоке загрузчика. */
    private class ByteCounter(private val bytes: AtomicLong) : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) {
                bytes.addAndGet(bytesTransferred.toLong())
                Traffic.add(bytesTransferred.toLong())
            }
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
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

    private inner class ExoListener(private val slot: Slot) : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (slot === incoming && playbackState == Player.STATE_READY) {
                promoteIncoming()
                return
            }
            if (slot === active) {
                if (playbackState == Player.STATE_ENDED && userPlay) {
                    // Сервер закрыл поток — просто подключаемся снова.
                    slot.exo.seekToDefaultPosition()
                    slot.exo.prepare()
                }
                invalidate()
            }
        }

        override fun onPlayerError(err: PlaybackException) {
            val exo = slot.exo
            if (slot === incoming) {
                slot.retries++
                if (slot.retries > 2) {
                    incomingFailed(describe(err))
                } else {
                    handler.postDelayed({
                        if (slot === incoming) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                        }
                    }, 1500)
                }
            } else if (slot === active) {
                if (!offline) offlineSince = SystemClock.uptimeMillis()
                offline = true
                handler.postDelayed({
                    if (slot === active && userPlay) {
                        exo.seekToDefaultPosition()
                        exo.prepare()
                        if (!transientLoss && stockTargetMs == 0L) exo.play()
                    }
                }, 2000)
                invalidate()
            }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val e = metadata.get(i)
                if (e is IcyInfo) {
                    val t = e.title?.trim()
                        ?.takeIf { it.length > 1 && it != "-" && !it.equals("unknown", ignoreCase = true) }
                    if (t == slot.title) continue
                    slot.title = t
                    if (slot === active) {
                        if (t != null) stationName(slot.stationId)?.let { History.add(it, t) }
                        invalidate()
                    }
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
                if (transientLoss && userPlay && stockTargetMs == 0L) {
                    active?.exo?.play()
                    incoming?.exo?.play()
                }
                transientLoss = false
                if (fadeRunnable == null && stockTargetMs == 0L) active?.exo?.volume = level()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true // навигатор говорит — приглушаем, не останавливаем
                if (fadeRunnable == null && stockTargetMs == 0L) active?.exo?.volume = level()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                transientLoss = true // звонок — пауза, после него продолжим
                finishFade()
                active?.exo?.pause()
                incoming?.exo?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausePlayback()
            }
        }
        invalidate()
    }

    // ---------- Буфер, связь, качество ----------

    private fun onTick() {
        ticks++
        if (ticks % 10 == 0) Traffic.flush()
        val a = active
        val now = SystemClock.uptimeMillis()
        var bufMs = 0L
        if (a != null && a.stationId == currentId) {
            val exo = a.exo
            measureRx(a, now)
            readFormat(a)
            bufMs = exo.totalBufferedDuration
            val bp = exo.bufferedPosition
            if (bp > lastBuffered + 50) {
                lastBuffered = bp
                lastGrowAt = now
                if (offline) {
                    offline = false
                    offlineSince = 0
                }
            } else if (bp + 1000 < lastBuffered) {
                lastBuffered = bp // переподключились, позиция сбросилась
                lastGrowAt = now
            }

            // «Запастись»: держим на паузе, пока не накопится.
            if (stockTargetMs > 0) {
                if (exo.isPlaying && rampRunnable == null) exo.pause()
                if (bufMs >= stockTargetMs) {
                    stockTargetMs = 0
                    resumeAfterStock()
                }
            }

            val playing = userPlay && !transientLoss && stockTargetMs == 0L
            if (playing && exo.playbackState != Player.STATE_IDLE &&
                bufMs < a.targetMs - 1000 && now - lastGrowAt > OFFLINE_AFTER_MS
            ) {
                if (!offline) {
                    offline = true
                    offlineSince = now
                    invalidate()
                }
            }
            maybeFailover(a, now)
            maybeLowerQuality(a, now, bufMs, playing)

            val slow = playing && Prefs.settings.value.growBuffer && !offline &&
                exo.playbackState == Player.STATE_READY && bufMs < a.targetMs - 1500
            val speed = if (slow) SLOW_SPEED else 1f
            if (exo.playbackParameters.speed != speed) exo.playbackParameters = PlaybackParameters(speed)
        } else {
            rxFor = null
            rxKbps = 0.0
            codec = null
            bitrateKbps = 0
        }
        publish(bufMs)
    }

    /** Связи от потока нет уже 15 с, а интернет в телефоне есть — значит, завис сервер. Пробуем другую ссылку. */
    private fun maybeFailover(a: Slot, now: Long) {
        if (!offline || !userPlay || incoming != null || !Prefs.settings.value.fallback) return
        if (offlineSince == 0L || now - offlineSince < FAILOVER_AFTER_MS || now - lastFailoverAt < FAILOVER_AFTER_MS) return
        val st = stations.firstOrNull { it.id == a.stationId } ?: return
        if (st.streams.size < 2 || !networkOk()) return
        lastFailoverAt = now
        val next = (a.streamIdx + 1) % st.streams.size
        error = "Поток завис — переключаюсь на запасную ссылку"
        beginLoad(streamIndex = next)
    }

    /** Приходит меньше, чем нужно для звука, и запас тает — берём ссылку с меньшим битрейтом. */
    private fun maybeLowerQuality(a: Slot, now: Long, bufMs: Long, playing: Boolean) {
        if (!Prefs.settings.value.lowerQuality || !playing || offline || incoming != null) {
            lowSince = 0
            return
        }
        val cur = if (a.stream.bitrate > 0) a.stream.bitrate else bitrateKbps
        val st = stations.firstOrNull { it.id == a.stationId }
        val lower = st?.streams?.withIndex()
            ?.filter { it.value.bitrate in 1 until cur }
            ?.maxByOrNull { it.value.bitrate }
        val starving = cur > 0 && rxKbps > 0 && rxKbps < cur * 0.85 && bufMs < a.targetMs / 2
        if (lower == null || !starving) {
            lowSince = 0
            return
        }
        if (lowSince == 0L) lowSince = now
        if (now - lowSince < LOW_QUALITY_AFTER_MS) return
        lowSince = 0
        error = "Слабая связь — облегчённый поток ${lower.value.bitrate} kbps"
        beginLoad(streamIndex = lower.index)
    }

    private fun networkOk(): Boolean = try {
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (_: Exception) {
        false
    }

    private fun measureRx(a: Slot, now: Long) {
        val total = a.counter.get()
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

    private fun readFormat(a: Slot) {
        val f = a.exo.audioFormat
        if (f == null) {
            codec = null
            bitrateKbps = a.stream.bitrate
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
        if (br <= 0) br = a.stream.bitrate
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

    private fun onStationsChanged(list: List<Station>) {
        stations = list
        val a = active
        if (a != null && list.none { it.id == a.stationId }) {
            // Удалили станцию, которая играла.
            cancelIncoming()
            finishFade()
            releaseSlot(a)
            active = null
            userPlay = false
            stockTargetMs = 0
            abandonFocus()
        }
        if (currentId == null || list.none { it.id == currentId }) currentId = list.firstOrNull()?.id
        invalidate()
    }

    // ---------- Вспомогательное ----------

    private fun currentIndex() = stations.indexOfFirst { it.id == currentId }

    private fun stationName(id: String) = stations.firstOrNull { it.id == id }?.name

    private fun currentTrack(): String? = active?.takeIf { it.stationId == currentId }?.title

    private fun isLoadingNow(): Boolean = userPlay && (
        stockTargetMs > 0 || pendingSwitch != null || incoming != null || active == null ||
            active?.stationId != currentId || active?.exo?.playbackState == Player.STATE_BUFFERING
        )

    private fun invalidate() {
        invalidateState()
        publish(active?.takeIf { it.stationId == currentId }?.exo?.totalBufferedDuration ?: 0L)
    }

    private var widgetKey = ""

    private fun publish(bufMs: Long) {
        val raw = currentTrack()
        val track = raw?.let { splitTrack(it) }
        val a = active?.takeIf { it.stationId == currentId }
        val s = Prefs.settings.value
        val note = if (a != null && a.streamIdx > 0) {
            if (a.stream.bitrate > 0) "запасная ссылка · ${a.stream.bitrate} kbps" else "запасная ссылка"
        } else null
        RadioBus.state.value = RadioUi(
            stationId = currentId,
            playing = userPlay,
            loading = isLoadingNow(),
            trackTitle = track?.title,
            trackArtist = track?.artist,
            bufferMs = bufMs,
            targetMs = a?.targetMs ?: s.targetBufferMs,
            offline = offline && userPlay,
            interrupted = userPlay && transientLoss,
            error = error,
            note = note,
            codec = if (a != null) codec else null,
            bitrateKbps = if (a != null) bitrateKbps else 0,
            rxKbps = if (a != null) rxKbps.toInt() else 0,
            stockingMs = stockTargetMs,
            highway = s.highway,
        )
        // Виджет обновляем только когда что-то видимое поменялось.
        val name = stations.firstOrNull { it.id == currentId }?.name ?: "Авторадио"
        val key = "$name|$raw|$userPlay"
        if (key != widgetKey) {
            widgetKey = key
            RadioWidget.update(ctx, name, raw, userPlay)
        }
    }
}
