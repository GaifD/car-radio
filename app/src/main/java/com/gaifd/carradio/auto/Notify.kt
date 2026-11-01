package com.gaifd.carradio.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gaifd.carradio.R
import com.gaifd.carradio.ui.MainActivity

object Notify {
    private const val CHANNEL = "car"

    fun tapToPlay(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Подключение к машине", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_AUTOPLAY, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Машина подключена")
            .setContentText("Нажми, чтобы включить радио. Чтобы включалось само — сними ограничение батареи.")
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(2, n)
        } catch (_: SecurityException) {
        }
    }
}
