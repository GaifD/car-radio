package com.gaifd.carradio.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gaifd.carradio.R
import com.gaifd.carradio.data.History
import com.gaifd.carradio.data.Logos
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Settings
import com.gaifd.carradio.data.Stats
import com.gaifd.carradio.data.Traffic
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.ui.MainActivity
import kotlin.math.roundToInt

/**
 * Фоновый сервис плеера. Пока играет звук — это «приоритетная» служба с уведомлением,
 * её система не выгружает. Через MediaSession же работают экран магнитолы и кнопки на руле.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_AUTOPLAY = "com.gaifd.carradio.AUTOPLAY"
        const val ACTION_PAUSE = "com.gaifd.carradio.PAUSE"
        const val ACTION_TOGGLE = "com.gaifd.carradio.TOGGLE"
        const val ACTION_NEXT = "com.gaifd.carradio.NEXT"
        const val ACTION_PREV = "com.gaifd.carradio.PREV"
        const val EXTRA_STATION = "station"
        const val EXTRA_FROM_CAR = "fromCar"

        @Volatile
        var running = false
            private set
    }

    private var session: MediaSession? = null
    private lateinit var player: CrossfadePlayer

    // Bluetooth отвалился / наушники выдернули — Android шлёт «звук сейчас пойдёт в динамик».
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && Prefs.settings.value.pauseOnDisconnect) {
                player.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        StationRepo.init(this)
        History.init(this)
        Traffic.init(this)
        Logos.init(this)
        Stats.init(this)
        player = CrossfadePlayer(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        val provider = DefaultMediaNotificationProvider.Builder(this).build()
        provider.setSmallIcon(R.drawable.ic_notif)
        setMediaNotificationProvider(provider)
        ContextCompat.registerReceiver(
            this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        running = true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_AUTOPLAY -> {
                if (StationRepo.stations.value.isEmpty()) {
                    // Запустили как приоритетную службу, а играть нечего — обязаны показаться и уйти.
                    bail()
                    return START_NOT_STICKY
                }
                val s = Prefs.settings.value
                val fromCar = intent.getBooleanExtra(EXTRA_FROM_CAR, false)
                if (fromCar && s.limitStartVolume) capVolume()
                // Станция по расписанию: утром одна, вечером другая (только при автозапуске из машины).
                if (fromCar && s.scheduleOn) {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    s.schedule[Settings.periodAt(hour)]?.let { player.selectById(it) }
                }
                intent.getStringExtra(EXTRA_STATION)?.let { player.selectById(it) }
                player.play()
            }
            ACTION_PAUSE -> player.pause()
            ACTION_TOGGLE -> when {
                player.playWhenReady -> player.pause()
                StationRepo.stations.value.isEmpty() -> bail()
                else -> player.play()
            }
            // С виджета: листаем и сразу играем.
            ACTION_NEXT -> {
                player.seekToNextMediaItem()
                if (StationRepo.stations.value.isNotEmpty()) player.play() else bail()
            }
            ACTION_PREV -> {
                player.seekToPreviousMediaItem()
                if (StationRepo.stations.value.isNotEmpty()) player.play() else bail()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Смахнули приложение из списка: если радио играет — играем дальше.
        val p = session?.player
        if (p == null || !p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        running = false
        try {
            unregisterReceiver(noisy)
        } catch (_: Exception) {
        }
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun capVolume() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = (max * 0.6f).roundToInt()
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
        }
    }

    private fun bail() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("service", "Служебное", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, "service")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Авторадио")
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(77, n)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }
}
