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
import com.gaifd.carradio.data.History
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
                    "Если основная ссылка не отвечает или зависла, приложение само переключится на запасную. " +
                        "Ссылку с меньшим битрейтом возьмёт при слабой связи.",
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
                                if (i == 0) "Основная" else "Запасная $i",
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
                                    "Сделать основной", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.accent,
                                    modifier = Modifier.clickable { StationRepo.makePrimary(st.id, s.url) }.padding(vertical = 6.dp),
                                )
                                Text(
                                    "Удалить", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.amber,
                                    modifier = Modifier.clickable { StationRepo.removeAlt(st.id, s.url) }.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    if (busy) "Ищу…" else "Найти запасные в каталоге",
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
                                    msg = if (found.isEmpty()) "В каталоге других ссылок нет" else "Добавлено: ${found.size}"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    msg = "Каталог не отвечает"
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
                    if (url.isEmpty()) Text("Своя ссылка https://…", fontSize = 14.sp, color = C.faint)
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
                        if (busy) "Проверяю…" else "Проверить и добавить запасной",
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
                                        msg = "Добавлено"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        msg = e.message ?: "Не удалось открыть поток"
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
            TextButton(onClick = onDismiss) { Text("Готово", color = C.accent, fontWeight = FontWeight.Bold) }
        },
    )
}

/** Вся история треков. */
@Composable
fun HistoryScreen(items: List<HistoryEntry>, onBack: () -> Unit, onSearch: (String) -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Назад" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.chevronRight, null, tint = C.text, modifier = Modifier.size(22.dp).scale(-1f, 1f))
            }
            Spacer(Modifier.width(4.dp))
            Text("История", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "Очистить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.muted,
                    modifier = Modifier.clickable { confirmClear = true }.padding(8.dp),
                )
            }
        }
        Text(
            "Нажми на песню, чтобы найти её в интернете.",
            fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        if (items.isEmpty()) {
            Text("Пока пусто", fontSize = 14.sp, color = C.soft, modifier = Modifier.padding(4.dp))
        } else {
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
        Spacer(Modifier.height(12.dp))
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = C.card,
            titleContentColor = C.text,
            textContentColor = C.soft,
            title = { Text("Очистить историю?", fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = { History.clear(); confirmClear = false }) { Text("Очистить", color = C.amber) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = C.muted) }
            },
        )
    }
}

private fun dayOf(t: Long) = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(t))

private fun dayLabel(t: Long): String {
    val today = dayOf(System.currentTimeMillis())
    val yesterday = dayOf(System.currentTimeMillis() - 86_400_000L)
    return when (dayOf(t)) {
        today -> "Сегодня"
        yesterday -> "Вчера"
        else -> java.text.SimpleDateFormat("d MMMM", java.util.Locale("ru")).format(java.util.Date(t))
    }
}

