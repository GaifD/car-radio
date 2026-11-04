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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.data.FoundStation
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.ProbeResult
import com.gaifd.carradio.data.RadioBrowser
import com.gaifd.carradio.data.Settings
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.StationRepo
import com.gaifd.carradio.data.StreamProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SearchScreen(stations: List<Station>, settings: Settings) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val ukraine = settings.searchUkraine

    LaunchedEffect(query, ukraine) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            message = null
            loading = false
            return@LaunchedEffect
        }
        delay(600) // ждём, пока допечатаешь
        loading = true
        message = null
        try {
            results = RadioBrowser.search(q, ukraine)
            if (results.isEmpty()) {
                message = if (ukraine) "Ничего не нашлось. Попробуй «Все страны» или другое написание." else "Ничего не нашлось."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            message = "Каталог не отвечает — проверь интернет и попробуй ещё раз."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Добавить станцию")

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(C.card)
                .border(1.dp, if (query.isNotEmpty()) C.accent else C.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.search, null, tint = C.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Название станции", fontSize = 16.sp, color = C.faint)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.text),
                    cursorBrush = SolidColor(C.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (loading) {
                CircularProgressIndicator(color = C.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { query = "" }
                        .semantics { contentDescription = "Очистить" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.close, null, tint = C.muted, modifier = Modifier.size(18.dp)) }
            }
        }

        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Украина", ukraine) { Prefs.update { it.copy(searchUkraine = true) } }
            Chip("Все страны", !ukraine) { Prefs.update { it.copy(searchUkraine = false) } }
        }

        if (results.isNotEmpty() || message != null) {
            SectionTitle("Найдено", "каталог Radio Browser")
        }
        message?.let { Text(it, fontSize = 13.sp, color = C.soft, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 2.dp)) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { f ->
                val added = stations.any { st -> st.id == f.uuid || st.streams.any { it.url == f.url } }
                ResultRow(f, added) {
                    // Другие ссылки той же станции из выдачи — сразу запасными.
                    val alts = RadioBrowser.sameStation(Station.normalizeName(f.name), results, setOf(f.url))
                    StationRepo.add(f.toStation(alts))
                }
            }
        }

        AddByUrl()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ResultRow(f: FoundStation, added: Boolean, onAdd: () -> Unit) {
    val st = f.toStation()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(st.short, 42, active = false, seed = st.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OneLine(f.name, 14, weight = FontWeight.SemiBold)
            if (f.meta.isNotBlank()) OneLine(f.meta, 12, color = C.muted)
        }
        Spacer(Modifier.width(8.dp))
        if (added) {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.check, null, tint = C.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Есть", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.accent)
            }
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, C.border, CircleShape)
                    .clickable(onClick = onAdd)
                    .semantics { contentDescription = "Добавить ${f.name}" },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.plus, null, tint = C.accent, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun AddByUrl() {
    var url by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<ProbeResult?>(null) }
    val scope = rememberCoroutineScope()

    Spacer(Modifier.height(14.dp))
    Card {
        Text("Нет в каталоге?", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Ссылка на поток (.mp3, .aac, .m3u, .pls, .m3u8)", fontSize = 12.sp, color = C.muted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(C.bg)
                .border(1.dp, C.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (url.isEmpty()) Text("https://…", fontSize = 14.sp, color = C.faint)
            BasicTextField(
                value = url,
                onValueChange = { url = it; error = null },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = C.text),
                cursorBrush = SolidColor(C.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (url.isBlank() || checking) C.border else C.accent)
                    .clickable(enabled = url.isNotBlank() && !checking) {
                        checking = true
                        error = null
                        scope.launch {
                            try {
                                naming = StreamProbe.probe(url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Не удалось открыть поток"
                            } finally {
                                checking = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    if (checking) "Проверяю…" else "Проверить и добавить",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (url.isBlank() || checking) C.muted else C.onAccent,
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", fontSize = 12.sp, color = C.amber, lineHeight = 16.sp)
            Text(
                "Добавить без проверки",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.accent,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { naming = ProbeResult(url.trim(), null) }
                    .padding(vertical = 8.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Перед сохранением приложение проверит, что поток играет, и подтянет название станции.",
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
            )
        }
    }

    naming?.let { r ->
        NameDialog(
            initial = r.name ?: hostOf(r.url),
            onDismiss = { naming = null },
            onSave = { name ->
                StationRepo.add(Station(id = UUID.randomUUID().toString(), name = name, url = r.url))
                naming = null
                url = ""
                error = null
            },
        )
    }
}

private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        textContentColor = C.soft,
        title = { Text("Как назвать станцию?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.bg)
                    .border(1.dp, C.border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Manrope, fontSize = 16.sp, color = C.text),
                    cursorBrush = SolidColor(C.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) {
                Text("Сохранить", color = C.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = C.muted) }
        },
    )
}
