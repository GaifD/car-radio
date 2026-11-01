package com.gaifd.carradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.data.Settings
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.playback.RadioUi

@Composable
fun RadioScreen(
    stations: List<Station>,
    ui: RadioUi,
    settings: Settings,
    batteryOk: Boolean,
    onFixBattery: () -> Unit,
    onPlay: (Station) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoAdd: () -> Unit,
) {
    val current = stations.firstOrNull { it.id == ui.stationId } ?: stations.firstOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Header(settings, ui)

        if (!batteryOk) {
            Banner("Телефон может выключить радио в фоне. Нажми, чтобы снять ограничение батареи.", onFixBattery)
            Spacer(Modifier.height(10.dp))
        }

        if (current == null) {
            Card {
                Text("Станций пока нет", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Найди свои станции по названию во вкладке «Добавить» — одна кнопка, и она здесь.",
                    fontSize = 13.sp, color = C.soft, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.accent)
                        .clickable(onClick = onGoAdd)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Добавить станции", color = C.bg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            NowPlaying(current, ui, settings, onToggle, onPrev, onNext)
            SectionTitle("Мои станции", "удерживай — меню")
            StationGrid(stations, ui.stationId, onPlay)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header(settings: Settings, ui: RadioUi) {
    val dot = when {
        ui.offline -> C.amber
        ui.playing && !ui.loading -> C.accent
        else -> C.faint
    }
    val status = when {
        ui.offline -> "нет связи · играет из буфера"
        ui.interrupted -> "пауза на время звонка"
        ui.playing && ui.loading -> "подключаюсь…"
        ui.playing -> "в эфире"
        else -> "пауза"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Column {
            OneLine(settings.carName ?: "Авторадио", 15, weight = FontWeight.Bold)
            Text(status, fontSize = 12.sp, color = C.muted)
        }
    }
}

@Composable
private fun NowPlaying(
    st: Station,
    ui: RadioUi,
    settings: Settings,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val label: String
    val labelColor: Color
    when {
        ui.offline -> { label = "НЕТ СВЯЗИ"; labelColor = C.amber }
        ui.playing && ui.loading -> { label = "ЗАГРУЗКА"; labelColor = C.soft }
        ui.playing -> { label = "В ЭФИРЕ"; labelColor = C.accent }
        else -> { label = "ПАУЗА"; labelColor = C.muted }
    }
    val sub = ui.track ?: st.genre.ifBlank { " " }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.card)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(st.short, 72, active = true, big = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor, letterSpacing = 1.sp)
                Text(
                    st.name, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                    lineHeight = 28.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                OneLine(sub, 14, color = C.soft)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(56, filled = false, desc = "Предыдущая станция", onClick = onPrev) {
                Icon(Ic.prev, null, tint = C.text, modifier = Modifier.size(24.dp))
            }
            RoundButton(80, filled = true, desc = if (ui.playing) "Пауза" else "Играть", onClick = onToggle) {
                Icon(if (ui.playing) Ic.pause else Ic.play, null, tint = C.bg, modifier = Modifier.size(32.dp))
            }
            RoundButton(56, filled = false, desc = "Следующая станция", onClick = onNext) {
                Icon(Ic.next, null, tint = C.text, modifier = Modifier.size(24.dp))
            }
        }

        BufferBar(ui, settings)

        if (ui.error != null) {
            Text(ui.error, fontSize = 12.sp, color = C.amber, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun BufferBar(ui: RadioUi, settings: Settings) {
    val sec = (ui.bufferMs / 1000).toInt()
    val target = (ui.targetMs / 1000).toInt()
    val right = when {
        !ui.playing -> ""
        ui.offline -> "хватит ещё на $sec с"
        ui.loading -> "подключаюсь"
        ui.bufferMs < ui.targetMs - 2000 -> if (settings.growBuffer) "копится до $target с" else "запас до $target с"
        else -> "поток стабилен"
    }
    val color = if (ui.offline) C.amber else C.accent
    val frac = if (ui.targetMs > 0) (ui.bufferMs.toFloat() / ui.targetMs).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Буфер ", fontSize = 12.sp, color = C.muted)
            Text("$sec с", fontSize = 12.sp, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, color = C.text)
            Spacer(Modifier.weight(1f))
            Text(right, fontSize = 12.sp, color = if (ui.offline) C.amber else C.muted)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(C.line)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun RoundButton(size: Int, filled: Boolean, desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(C.accent) else Modifier.border(1.dp, C.border, CircleShape))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StationGrid(stations: List<Station>, currentId: String?, onPlay: (Station) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stations.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { st ->
                    StationTile(
                        st,
                        active = st.id == currentId,
                        isFirst = stations.firstOrNull()?.id == st.id,
                        isLast = stations.lastOrNull()?.id == st.id,
                        onPlay = { onPlay(st) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationTile(
    st: Station,
    active: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) C.accentBg else C.card)
                .border(1.dp, if (active) C.accent else Color.Transparent, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(st.short, 42, active = active)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                OneLine(st.name, 14, weight = FontWeight.Bold)
                if (st.genre.isNotBlank()) OneLine(st.genre, 11, color = C.muted)
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(C.card),
        ) {
            if (!isFirst) DropdownMenuItem(
                text = { Text("Сдвинуть раньше", color = C.text) },
                onClick = { StationRepo.move(st.id, -1); menu = false },
            )
            if (!isLast) DropdownMenuItem(
                text = { Text("Сдвинуть позже", color = C.text) },
                onClick = { StationRepo.move(st.id, 1); menu = false },
            )
            DropdownMenuItem(
                text = { Text("Удалить", color = C.amber) },
                onClick = { StationRepo.remove(st.id); menu = false },
            )
        }
    }
}
