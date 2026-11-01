package com.gaifd.carradio.auto

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.playback.PlaybackService

/**
 * Android будит приложение, когда телефон соединяется по Bluetooth с любым устройством,
 * даже если приложение выгружено. Реагируем только на выбранную машину.
 */
class CarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        Prefs.init(context)
        StationRepo.init(context)
        val s = Prefs.settings.value
        val mac = s.carMac ?: return

        val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (dev?.address?.equals(mac, ignoreCase = true) != true) return

        val app = context.applicationContext
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            if (!s.autoPlay || StationRepo.stations.value.isEmpty()) return
            // Звук в машину идёт не сразу после соединения — даём магнитоле пару секунд.
            val pending = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startAutoplay(app)
                } finally {
                    pending.finish()
                }
            }, 2500)
        } else if (s.pauseOnDisconnect && PlaybackService.running) {
            try {
                app.startService(Intent(app, PlaybackService::class.java).setAction(PlaybackService.ACTION_PAUSE))
            } catch (_: Exception) {
            }
        }
    }

    private fun startAutoplay(ctx: Context) {
        val i = Intent(ctx, PlaybackService::class.java).setAction(PlaybackService.ACTION_AUTOPLAY)
        try {
            ContextCompat.startForegroundService(ctx, i)
        } catch (e: Exception) {
            // Система не разрешила запуск из фона (ограничение батареи) — просим нажать.
            Notify.tapToPlay(ctx)
        }
    }
}
