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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.data.Favorites
import com.gaifd.carradio.data.HistoryEntry
import com.gaifd.carradio.data.Logos
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Settings
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.playback.RadioBus
import com.gaifd.carradio.playback.RadioUi

@Composable
fun RadioScreen(
    stations: List<Station>,
    ui: RadioUi,
    settings: Settings,
    history: List<HistoryEntry>,
    batteryOk: Boolean,
    onFixBattery: () -> Unit,
    onPlay: (Station) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onGoAdd: () -> Unit,
    onEditStreams: (Station) -> Unit,
    onOpenHistory: () -> Unit,
    onSearchTrack: (String) -> Unit,
    output: AudioOut,
    update: String?,
    onUpdate: () -> Unit,
    onDrive: () -> Unit,
) {
    val current = stations.firstOrNull { it.id == ui.stationId } ?: stations.firstOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Header(settings, ui, output, onDrive)

        if (update != null) {
            Banner("Есть новая версия $update — нажми, чтобы обновить", onUpdate)
            Spacer(Modifier.height(10.dp))
        }
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
                PillButton("Добавить станции", filled = true, onClick = onGoAdd)
            }
        } else {
            NowPlaying(current, ui, settings, onToggle, onPrev, onNext)
            SectionTitle("Мои станции", "удерживай — меню")
            StationList(settings.stationView, stations, ui.stationId, onPlay, onEditStreams)
            if (history.isNotEmpty()) {
                SectionTitle("Недавно играло", null)
                Card(padding = 14) {
                    history.take(5).forEachIndexed { i, h ->
                        HistoryRow(h, divider = i < minOf(4, history.size - 1), onSearch = onSearchTrack)
                    }
                    if (history.size > 5) {
                        Text(
                            "Вся история (${history.size})",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenHistory)
                                .padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header(settings: Settings, ui: RadioUi, output: AudioOut, onDrive: () -> Unit) {
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
        Column(Modifier.weight(1f)) {
            // Куда реально идёт звук: машина, наушники или динамик телефона.
            OneLine(output.name, 15, weight = FontWeight.Bold)
            Text(status, fontSize = 12.sp, color = C.muted)
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, C.border, CircleShape)
                .clickable(onClick = onDrive)
                .semantics { contentDescription = "Режим за рулём" },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.wheel, null, tint = C.soft, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(8.dp))
        // Быстрый переключатель «Трассы» прямо на главном экране.
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(99.dp))
                .then(
                    if (settings.highway) Modifier.background(C.accentBg)
                    else Modifier.border(1.dp, C.border, RoundedCornerShape(99.dp))
                )
                .clickable { Prefs.update { it.copy(highway = !it.highway) } }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (settings.highway) "ТРАССА · ВКЛ" else "Трасса",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (settings.highway) C.accent else C.soft,
                letterSpacing = if (settings.highway) 0.5.sp else 0.sp,
            )
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

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.card)
            .swipeStations(onPrev, onNext)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StationLogo(st, 72, active = true, big = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor, letterSpacing = 1.sp)
                Text(
                    st.name, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                    lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                OneLine(ui.trackTitle ?: st.genre.ifBlank { " " }, 14, color = C.soft, weight = FontWeight.SemiBold)
                if (ui.trackArtist != null) OneLine(ui.trackArtist, 13, color = C.muted)
            }
            val raw = ui.rawTrack
            if (raw != null) HeartButton(st.name, raw)
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
                Icon(if (ui.playing) Ic.pause else Ic.play, null, tint = C.onAccent, modifier = Modifier.size(32.dp))
            }
            RoundButton(56, filled = false, desc = "Следующая станция", onClick = onNext) {
                Icon(Ic.next, null, tint = C.text, modifier = Modifier.size(24.dp))
            }
        }

        BufferBar(ui, settings)
        StreamInfo(ui)

        if (ui.error != null) {
            Text(ui.error, fontSize = 12.sp, color = C.amber, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun BufferBar(ui: RadioUi, settings: Settings) {
    // Буфер выключен: ни полоски, ни отставания — только «нет связи», если она пропала.
    if (!ui.bufferOn) {
        if (ui.playing && ui.offline) {
            Text("нет связи · переподключаюсь", fontSize = 12.sp, color = C.amber)
        }
        ui.notice?.let { Text(it, fontSize = 12.sp, color = C.muted, lineHeight = 16.sp) }
        return
    }

    // Буфер включён: одно число — сколько звука в запасе. На столько же и отстаёшь от эфира.
    val sec = (ui.bufferMs / 1000).toInt()
    val goal = (ui.targetMs / 1000).toInt()
    val right = when {
        !ui.playing -> ""
        ui.offline -> "нет связи"
        ui.loading -> "подключаюсь"
        ui.bufferMs < ui.targetMs - 2000 -> if (settings.growBuffer) "копится" else "не полный"
        else -> "полный"
    }
    val color = if (ui.offline) C.amber else C.accent
    val frac = if (ui.targetMs > 0) (ui.bufferMs.toFloat() / ui.targetMs).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            // «Буфер 7 из 30 с»: сколько в запасе и сколько копим. Справа — только состояние.
            Text("Буфер ", fontSize = 12.sp, color = C.muted)
            Text("$sec", fontSize = 12.sp, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, color = if (ui.offline) C.amber else C.text)
            Text(" из $goal с", fontSize = 12.sp, color = C.muted)
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
    ui.notice?.let { Text(it, fontSize = 12.sp, color = C.muted, lineHeight = 16.sp) }

    // «В прямой эфир» = сбросить буфер, когда запас (он же отставание от эфира) заметный.
    val canGoLive = ui.playing && !ui.loading && !ui.offline && ui.bufferMs >= 10_000
    if (canGoLive) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "слышишь эфир $sec с назад",
                fontSize = 12.sp, color = C.muted, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .border(1.dp, C.accent, RoundedCornerShape(99.dp))
                    .clickable { RadioBus.control?.goLive() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("В прямой эфир", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accent)
            }
        }
    }
}

/** Формат и битрейт потока + сколько реально приходит из сети. */
@Composable
private fun StreamInfo(ui: RadioUi) {
    if (!ui.playing || ui.loading) return
    val left = listOfNotNull(
        ui.codec,
        if (ui.bitrateKbps > 0) "${ui.bitrateKbps} kbps" else null,
    ).joinToString(" · ").ifBlank { "поток" }
    // Приходит заметно меньше, чем нужно для звука, — запас тает.
    val slow = ui.offline || (ui.bitrateKbps > 0 && ui.rxKbps < ui.bitrateKbps * 0.85 && ui.bufferMs < ui.targetMs - 2000)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(left, fontSize = 12.sp, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, color = C.soft)
            Spacer(Modifier.weight(1f))
            Text("приём ", fontSize = 12.sp, color = C.muted)
            Text(
                "${ui.rxKbps} kbps",
                fontSize = 12.sp, fontFamily = Chakra, fontWeight = FontWeight.SemiBold,
                color = if (slow) C.amber else C.text,
            )
        }
        if (ui.note != null) Text(ui.note, fontSize = 12.sp, color = C.muted)
    }
}

@Composable
fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (filled) Modifier.background(C.accent) else Modifier.border(1.dp, C.border, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (filled) C.onAccent else C.accent, maxLines = 2,
        )
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
fun HistoryRow(h: HistoryEntry, divider: Boolean, onSearch: (String) -> Unit) {
    val t = com.gaifd.carradio.playback.splitTrack(h.track)
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(h.time))
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { onSearch(h.track) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, fontSize = 12.sp, fontFamily = Chakra, color = C.muted, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                OneLine(t.title, 14, weight = FontWeight.SemiBold)
                OneLine(listOfNotNull(t.artist, h.station).joinToString(" · "), 12, color = C.muted)
            }
            HeartButton(h.station, h.track, 38)
        }
        if (divider) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.line))
    }
}

/** Свайп по карточке влево/вправо — соседняя станция. */
fun Modifier.swipeStations(onPrev: () -> Unit, onNext: () -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -120f) onNext() else if (total > 120f) onPrev()
            total = 0f
        },
        onHorizontalDrag = { _, d -> total += d },
    )
}

@Composable
fun HeartButton(station: String, track: String, size: Int = 40) {
    val favs by Favorites.items.collectAsState()
    val liked = favs.any { it.track.equals(track, ignoreCase = true) }
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable { Favorites.toggle(station, track) }
            .semantics { contentDescription = if (liked) "Убрать из понравившихся" else "Нравится" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (liked) Ic.heartFilled else Ic.heart, null, tint = if (liked) C.accent else C.muted, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Четыре вида списка станций — выбираются в Настройках. */
@Composable
private fun StationList(
    view: Int,
    stations: List<Station>,
    currentId: String?,
    onPlay: (Station) -> Unit,
    onEditStreams: (Station) -> Unit,
) {
    val perRow = when (view) {
        Settings.VIEW_LIST, Settings.VIEW_COMPACT -> 1
        Settings.VIEW_BIG -> 3
        else -> 2
    }
    val gap = if (view == Settings.VIEW_COMPACT) 6.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        stations.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { st ->
                    StationTile(
                        st,
                        view = view,
                        active = st.id == currentId,
                        isFirst = stations.firstOrNull()?.id == st.id,
                        isLast = stations.lastOrNull()?.id == st.id,
                        onPlay = { onPlay(st) },
                        onEditStreams = { onEditStreams(st) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationTile(
    st: Station,
    view: Int,
    active: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPlay: () -> Unit,
    onEditStreams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (view == Settings.VIEW_COMPACT) 12.dp else 16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (active) C.accentBg else C.card)
        .border(1.dp, if (active) C.accent else Color.Transparent, shape)
        .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
    val sub = listOf(
        st.genre,
        if (st.bitrate > 0 && view == Settings.VIEW_LIST) "${st.bitrate} kbps" else "",
        if (st.alts.isNotEmpty()) "+${st.alts.size} ссыл." else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Box(modifier) {
        when (view) {
            Settings.VIEW_BIG -> Column(
                base.heightIn(min = 112.dp).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                StationLogo(st, 56, active = active)
                Text(
                    st.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Settings.VIEW_LIST -> Row(
                base.heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationLogo(st, 52, active = active)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(st.name, 16, weight = FontWeight.Bold)
                    if (sub.isNotBlank()) OneLine(sub, 12, color = C.muted)
                }
                if (active) Icon(Ic.play, null, tint = C.accent, modifier = Modifier.size(18.dp))
            }
            Settings.VIEW_COMPACT -> Row(
                base.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationLogo(st, 32, active = active)
                Spacer(Modifier.width(12.dp))
                OneLine(st.name, 14, weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (st.genre.isNotBlank()) Text(st.genre.substringBefore(" ·"), fontSize = 11.sp, color = C.muted, maxLines = 1)
            }
            else -> Row(
                base.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationLogo(st, 42, active = active)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(st.name, 14, weight = FontWeight.Bold)
                    if (sub.isNotBlank()) OneLine(sub, 11, color = C.muted)
                }
            }
        }
        StationMenu(st, menu, isFirst, isLast, onDismiss = { menu = false }, onEditStreams = onEditStreams)
    }
}

@Composable
private fun StationMenu(st: Station, open: Boolean, isFirst: Boolean, isLast: Boolean, onDismiss: () -> Unit, onEditStreams: () -> Unit) {
    val hasLogo = Logos.has(st.id)
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, modifier = Modifier.background(C.card)) {
        DropdownMenuItem(
            text = { Text("Ссылки потока (${st.streams.size})", color = C.text) },
            onClick = { onDismiss(); onEditStreams() },
        )
        DropdownMenuItem(
            text = { Text(if (hasLogo) "Сменить логотип" else "Свой логотип", color = C.text) },
            onClick = { onDismiss(); RadioScreenActions.pickLogo?.invoke(st.id) },
        )
        if (hasLogo) DropdownMenuItem(
            text = { Text("Убрать логотип", color = C.text) },
            onClick = { onDismiss(); Logos.remove(st.id); StationRepo.setLogo(st.id, "") },
        )
        if (!isFirst) DropdownMenuItem(
            text = { Text("Сдвинуть раньше", color = C.text) },
            onClick = { StationRepo.move(st.id, -1); onDismiss() },
        )
        if (!isLast) DropdownMenuItem(
            text = { Text("Сдвинуть позже", color = C.text) },
            onClick = { StationRepo.move(st.id, 1); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Удалить", color = C.amber) },
            onClick = { StationRepo.remove(st.id); Logos.remove(st.id); onDismiss() },
        )
    }
}

/** Действия, которым нужна Activity (выбор картинки из галереи). */
object RadioScreenActions {
    var pickLogo: ((String) -> Unit)? = null
}
