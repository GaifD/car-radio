package com.gaifd.carradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.playback.RadioUi

/** Режим «за рулём»: экран не гаснет, всё крупно, свайп по экрану переключает станции. */
@Composable
fun DriveScreen(
    st: Station?,
    ui: RadioUi,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val status = when {
        ui.offline -> "нет связи · хватит на ${ui.bufferMs / 1000} с"
        ui.playing && ui.loading -> "подключаюсь…"
        ui.playing && ui.bufferOn -> "в эфире · буфер ${ui.bufferMs / 1000} с"
        ui.playing -> "в эфире"
        else -> "пауза"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .swipeStations(onPrev, onNext)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, C.border, RoundedCornerShape(99.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Выйти", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = C.soft) }
            Spacer(Modifier.weight(1f))
            Text(status, fontSize = 15.sp, color = if (ui.offline) C.amber else C.muted)
        }

        Spacer(Modifier.weight(1f))
        if (st != null) {
            StationLogo(st, 128, active = true, big = true)
            Spacer(Modifier.height(20.dp))
            Text(
                st.name, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                ui.trackTitle ?: " ", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.soft,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 27.sp,
            )
            if (ui.trackArtist != null) {
                Text(ui.trackArtist, fontSize = 18.sp, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text("Нет станций", fontSize = 24.sp, color = C.soft)
        }
        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigButton(96, false, "Предыдущая станция", onPrev) { Icon(Ic.prev, null, tint = C.text, modifier = Modifier.size(40.dp)) }
            BigButton(128, true, if (ui.playing) "Пауза" else "Играть", onToggle) {
                Icon(if (ui.playing) Ic.pause else Ic.play, null, tint = C.onAccent, modifier = Modifier.size(52.dp))
            }
            BigButton(96, false, "Следующая станция", onNext) { Icon(Ic.next, null, tint = C.text, modifier = Modifier.size(40.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Text("свайп по экрану — соседняя станция", fontSize = 13.sp, color = C.faint)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BigButton(size: Int, filled: Boolean, desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(C.accent) else Modifier.border(2.dp, C.border, CircleShape))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content() }
}
