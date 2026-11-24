package com.gaifd.carradio.ui

import com.gaifd.carradio.R
import com.gaifd.carradio.L

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.data.Favorites
import com.gaifd.carradio.data.History
import com.gaifd.carradio.data.Stats
import com.gaifd.carradio.data.HistoryEntry
import com.gaifd.carradio.data.RadioBrowser
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.data.Stream
import com.gaifd.carradio.data.StreamProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Ссылки потока одной станции: основная и запасные. */
@Composable
fun StreamsDialog(stationId: String, onDismiss: () -> Unit) {
    val stations by StationRepo.stations.collectAsState()
    val st = stations.firstOrNull { it.id == stationId }
    if (st == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        title = { Text(st.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    L.s(R.string.songs_001),
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
                )
                st.streams.forEachIndexed { i, s ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (i == 0) C.accentBg else C.bg)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (i == 0) L.s(R.string.songs_002) else L.s(R.string.songs_003, i),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (i == 0) C.accent else C.soft,
                                modifier = Modifier.weight(1f),
                            )
                            if (s.bitrate > 0) Text("${s.bitrate} kbps", fontSize = 12.sp, fontFamily = Chakra, color = C.muted)
                        }
                        Text(s.url, fontSize = 11.sp, color = C.muted, maxLines = 2, lineHeight = 14.sp)
                        if (i > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    L.s(R.string.songs_004), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.accent,
                                    modifier = Modifier.clickable { StationRepo.makePrimary(st.id, s.url) }.padding(vertical = 6.dp),
                                )
                                Text(
                                    L.s(R.string.songs_005), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.amber,
                                    modifier = Modifier.clickable { StationRepo.removeAlt(st.id, s.url) }.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    if (busy) L.s(R.string.songs_006) else L.s(R.string.songs_007),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (busy) C.muted else C.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) {
                            busy = true
                            msg = null
                            scope.launch {
                                try {
                                    val found = RadioBrowser.findAlternatives(st)
                                    found.forEach { StationRepo.addAlt(st.id, it) }
                                    msg = if (found.isEmpty()) L.s(R.string.songs_008) else L.s(R.string.songs_009, found.size)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    msg = L.s(R.string.songs_010)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.bg)
                        .border(1.dp, C.border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (url.isEmpty()) Text(L.s(R.string.songs_011), fontSize = 14.sp, color = C.faint)
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = C.text),
                        cursorBrush = SolidColor(C.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (url.isNotBlank()) {
                    Text(
                        if (busy) L.s(R.string.songs_012) else L.s(R.string.songs_013),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (busy) C.muted else C.accent,
                        modifier = Modifier
                            .clickable(enabled = !busy) {
                                busy = true
                                msg = null
                                scope.launch {
                                    try {
                                        val r = StreamProbe.probe(url)
                                        StationRepo.addAlt(st.id, Stream(r.url, 0))
                                        url = ""
                                        msg = L.s(R.string.songs_014)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        msg = e.message ?: L.s(R.string.songs_015)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
                msg?.let { Text(it, fontSize = 12.sp, color = C.soft) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(L.s(R.string.songs_016), color = C.accent, fontWeight = FontWeight.Bold) }
        },
    )
}

/** Вкладка «Песни»: понравившиеся, история и статистика. */
@Composable
fun SongsScreen(segment: Int, onSegment: (Int) -> Unit, history: List<HistoryEntry>, onSearch: (String) -> Unit) {
    val favs by Favorites.items.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenTitle(L.s(R.string.songs_017))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Chip(L.s(R.string.songs_018), segment == 0) { onSegment(0) }
            Chip(L.s(R.string.songs_019), segment == 1) { onSegment(1) }
            Chip(L.s(R.string.songs_020), segment == 2) { onSegment(2) }
        }
        when (segment) {
            0 -> {
                if (favs.isEmpty()) {
                    Hint(L.s(R.string.songs_021))
                } else {
                    Hint(L.s(R.string.songs_022))
                    SongList(favs.map { HistoryEntry(it.time, it.station, it.track) }, onSearch)
                }
            }
            1 -> {
                if (history.isEmpty()) {
                    Hint(L.s(R.string.songs_023))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Hint(L.s(R.string.songs_024), Modifier.weight(1f))
                        Text(
                            L.s(R.string.songs_025), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.muted,
                            modifier = Modifier.clickable { confirmClear = true }.padding(8.dp),
                        )
                    }
                    SongList(history, onSearch)
                }
            }
            else -> StatsView()
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.card,
            titleContentColor = C.text,
            title = { Text(L.s(R.string.songs_026), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = { History.clear(); confirmClear = false }) { Text(L.s(R.string.songs_025), color = C.amber) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(L.s(R.string.songs_027), color = C.muted) }
            },
        )
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = C.muted, lineHeight = 17.sp, modifier = modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun SongList(items: List<HistoryEntry>, onSearch: (String) -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(horizontal = 14.dp)
    ) {
        itemsIndexed(items) { i, h ->
            if (i == 0 || dayOf(items[i - 1].time) != dayOf(h.time)) {
                Text(
                    dayLabel(h.time), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accent,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
            HistoryRow(h, divider = i < items.size - 1 && dayOf(items[i + 1].time) == dayOf(h.time), onSearch = onSearch)
        }
    }
}

@Composable
private fun StatsView() {
    val ver by Stats.version.collectAsState()
    val month = remember(ver) { Stats.month() }
    val all = remember(ver) { Stats.allTime() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Card(padding = 16) {
            Text(L.s(R.string.songs_028), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(hours(month.sumOf { it.ms }), fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = C.accent)
            Spacer(Modifier.height(8.dp))
            if (month.isEmpty()) Text(L.s(R.string.songs_029), fontSize = 13.sp, color = C.soft)
            val top = month.firstOrNull()?.ms ?: 1L
            month.take(10).forEach { st -> StatBar(st.name, st.ms, top) }
        }
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            Text(L.s(R.string.songs_030), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(hours(all.sumOf { it.ms }), fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
            Spacer(Modifier.height(4.dp))
            all.take(5).forEach { st ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OneLine(st.name, 13, color = C.soft)
                    Spacer(Modifier.weight(1f))
                    Text(hours(st.ms), fontSize = 13.sp, fontFamily = Chakra, color = C.text)
                }
            }
            Text(
                L.s(R.string.songs_031), fontSize = 12.sp, color = C.muted,
                modifier = Modifier.clickable { Stats.reset() }.padding(top = 10.dp, bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatBar(name: String, ms: Long, top: Long) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            OneLine(name, 13, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(hours(ms), fontSize = 13.sp, fontFamily = Chakra, color = C.soft)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
            Box(
                Modifier
                    .fillMaxWidth((ms.toFloat() / top).coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(C.accent)
            )
        }
    }
}

private fun hours(ms: Long): String {
    val min = ms / 60_000
    return if (min < 60) L.s(R.string.songs_032, min) else L.s(R.string.songs_033, min / 60, min % 60)
}

/** Где искать песню. */
@Composable
fun SearchChooser(query: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        title = { Text(query, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ytmusic" to "YouTube Music", "spotify" to "Spotify", "google" to "Google", "share" to L.s(R.string.share)).forEach { (id, label) ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.bg)
                            .clickable { onPick(id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L.s(R.string.songs_034), color = C.muted) } },
    )
}

private fun dayOf(t: Long) = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(t))

private fun dayLabel(t: Long): String {
    val today = dayOf(System.currentTimeMillis())
    val yesterday = dayOf(System.currentTimeMillis() - 86_400_000L)
    return when (dayOf(t)) {
        today -> L.s(R.string.songs_035)
        yesterday -> L.s(R.string.songs_036)
        else -> java.text.SimpleDateFormat("d MMMM", com.gaifd.carradio.L.locale()).format(java.util.Date(t))
    }
}

