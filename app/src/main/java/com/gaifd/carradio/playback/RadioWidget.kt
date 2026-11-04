package com.gaifd.carradio.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.gaifd.carradio.R
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.ui.Accent
import com.gaifd.carradio.ui.MainActivity

/** Виджет на рабочий стол: станция, трек, назад / играть / вперёд. */
class RadioWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Prefs.init(context)
        StationRepo.init(context)
        val st = RadioBus.state.value
        val name = StationRepo.stations.value.firstOrNull { it.id == (st.stationId ?: Prefs.settings.value.lastStationId) }?.name
            ?: StationRepo.stations.value.firstOrNull()?.name ?: "Авторадио"
        val track = listOfNotNull(st.trackArtist, st.trackTitle).joinToString(" — ").ifBlank { null }
        render(context, manager, ids, name, track, st.playing)
    }

    companion object {
        /** Перерисовать виджет (например, сменился цвет приложения). */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(ctx, RadioWidget::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) return
            ctx.sendBroadcast(
                Intent(ctx, RadioWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        fun update(ctx: Context, station: String, track: String?, playing: Boolean) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = try {
                mgr.getAppWidgetIds(ComponentName(ctx, RadioWidget::class.java))
            } catch (_: Exception) {
                return
            }
            if (ids.isEmpty()) return
            render(ctx, mgr, ids, station, track?.let { t -> splitTrack(t).let { listOfNotNull(it.artist, it.title).joinToString(" — ") } }, playing)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, station: String, track: String?, playing: Boolean) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_radio)
            v.setTextViewText(R.id.w_station, station)
            if (track.isNullOrBlank()) {
                v.setViewVisibility(R.id.w_track, View.GONE)
            } else {
                v.setViewVisibility(R.id.w_track, View.VISIBLE)
                v.setTextViewText(R.id.w_track, track)
            }
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.w_pause else R.drawable.w_play)
            // Кружок «играть» — в цвет приложения, значок на нём — чёрный или белый, что читается.
            Prefs.init(ctx)
            val acc = Accent.forDark(Accent.base(ctx, Prefs.settings.value))
            v.setInt(R.id.w_play_circle, "setColorFilter", acc.accent)
            v.setInt(R.id.w_play, "setColorFilter", acc.onAccent)

            // Пока играет — сервис уже запущен, достаточно обычной команды.
            // На паузе кнопки сами запускают плеер (так Android разрешает старт с виджета).
            v.setOnClickPendingIntent(R.id.w_play, action(ctx, if (playing) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_TOGGLE, !playing, 10))
            v.setOnClickPendingIntent(R.id.w_next, action(ctx, PlaybackService.ACTION_NEXT, !playing, 11))
            v.setOnClickPendingIntent(R.id.w_prev, action(ctx, PlaybackService.ACTION_PREV, !playing, 12))
            val open = PendingIntent.getActivity(
                ctx, 13, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_info, open)
            try {
                mgr.updateAppWidget(ids, v)
            } catch (_: Exception) {
            }
        }

        private fun action(ctx: Context, action: String, foreground: Boolean, code: Int): PendingIntent {
            val i = Intent(ctx, PlaybackService::class.java).setAction(action)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (foreground) PendingIntent.getForegroundService(ctx, code, i, flags)
            else PendingIntent.getService(ctx, code, i, flags)
        }
    }
}
