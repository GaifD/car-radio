package com.gaifd.carradio.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.BuildConfig
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Settings
import com.gaifd.carradio.data.Station
import com.gaifd.carradio.data.Traffic
import com.gaifd.carradio.data.UpdateInfo
import com.gaifd.carradio.data.UpdateState
import com.gaifd.carradio.data.Updater
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class SystemStatus(
    val batteryOk: Boolean,
    val notificationsOk: Boolean,
    val bluetoothOk: Boolean,
    val carLinked: Boolean,
)

@Composable
fun SettingsScreen(
    s: Settings,
    sys: SystemStatus,
    onPickCar: () -> Unit,
    onLinkCar: () -> Unit,
    onFixBattery: () -> Unit,
    onFixNotifications: () -> Unit,
    onAppSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    stations: List<Station>,
    logoStatus: String?,
    onFetchLogos: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateInfo) -> Unit,
    onInstallUpdate: (java.io.File) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Настройки")

        // ---- Вид ----
        Card(padding = 16) {
            CardTitle("Вид")
            Text("Тема", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(Settings.THEME_DARK to "Тёмная", Settings.THEME_LIGHT to "Светлая", Settings.THEME_SYSTEM to "Как в системе", Settings.THEME_BY_TIME to "По времени"),
                s.theme, small = true,
            ) { v -> Prefs.update { it.copy(theme = v) } }
            if (s.theme == Settings.THEME_BY_TIME) {
                Text("светлая с 7:00 до 19:00, остальное время тёмная", fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            AccentPicker(s)
            Spacer(Modifier.height(14.dp))
            Text("Список станций", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(Settings.VIEW_GRID to "Плитки", Settings.VIEW_LIST to "Список", Settings.VIEW_BIG to "Крупно", Settings.VIEW_COMPACT to "Компакт"),
                s.stationView, small = true,
            ) { v -> Prefs.update { it.copy(stationView = v) } }
            Spacer(Modifier.height(6.dp))
            ActionRow(
                title = "Подтянуть логотипы",
                hint = logoStatus ?: "найти в каталоге логотипы для станций, у которых их нет",
                icon = Ic.image,
                divider = false,
                onClick = onFetchLogos,
            )
        }
        Spacer(Modifier.height(10.dp))

        // ---- Автозапуск ----
        Card(padding = 16) {
            CardTitle("Автозапуск в машине")
            ActionRow(
                title = "Машина",
                hint = s.carName?.let { "$it — на неё реагировать" } ?: "Выбери Bluetooth-устройство машины",
                icon = Ic.chevronDown,
                iconTint = if (s.carMac == null) C.accent else C.muted,
                onClick = onPickCar,
            )
            if (s.carMac != null) {
                ActionRow(
                    title = "Привязка к телефону",
                    hint = if (sys.carLinked) "Есть — Android будит радио при подключении машины"
                    else "Нет — нажми, чтобы Android разрешил запуск из фона",
                    icon = if (sys.carLinked) Ic.check else Ic.link,
                    iconTint = if (sys.carLinked) C.accent else C.amber,
                    onClick = onLinkCar,
                )
            }
            ToggleRow("Играть при подключении", "сразу последняя станция", s.autoPlay) { v ->
                Prefs.update { it.copy(autoPlay = v) }
            }
            if (s.autoPlay) {
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text("Прогрев: начинать с запасом", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "пока машина греется, радио молча копит буфер и потом начинает играть",
                        fontSize = 12.sp, color = C.muted, lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf(0 to "сразу", 30 to "30 с", 60 to "60 с"), s.warmupSec) { v ->
                        Prefs.update { it.copy(warmupSec = v) }
                    }
                }
                Divider()
            }
            ToggleRow("Станция по расписанию", "при автозапуске утром одна станция, вечером другая", s.scheduleOn) { v ->
                Prefs.update { it.copy(scheduleOn = v) }
            }
            if (s.scheduleOn) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Settings.PERIODS.forEach { (key, label) ->
                        ScheduleRow(label, s.schedule[key], stations) { id ->
                            Prefs.update { it.copy(schedule = if (id == null) it.schedule - key else it.schedule + (key to id)) }
                        }
                    }
                }
                Divider()
            }
            ToggleRow("Пауза при отключении", "заглушил машину — радио стоп", s.pauseOnDisconnect) { v ->
                Prefs.update { it.copy(pauseOnDisconnect = v) }
            }
            ToggleRow("Громкость при старте", "не громче 60%, чтобы не оглушало", s.limitStartVolume, divider = false) { v ->
                Prefs.update { it.copy(limitStartVolume = v) }
            }
        }

        // ---- Переход ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Плавный переход", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (s.crossfadeMs == 0) "выключен" else "%.1f с".format(s.crossfadeMs / 1000f),
                    fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = if (s.crossfadeMs == 0) C.muted else C.accent,
                )
            }
            Slider(
                value = s.crossfadeMs / 1000f,
                onValueChange = { v -> Prefs.update { it.copy(crossfadeMs = (v * 2).toInt() * 500) } },
                valueRange = 0f..3f,
                steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = C.accent,
                    activeTrackColor = C.accent,
                    inactiveTrackColor = C.border,
                    activeTickColor = C.bg,
                    inactiveTickColor = C.muted,
                ),
            )
            Text(
                "Пока новая станция загружается, старая играет — тишины между ними не будет.",
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
            )
        }

        // ---- Буфер ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle("Буфер")
            ToggleRow(
                "Режим «Трасса»",
                "запас до 3 минут — для поездок за город. Включается и кнопкой на главном экране",
                s.highway,
            ) { v -> Prefs.update { it.copy(highway = v) } }
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    if (s.highway) "Запас в городе (сейчас «Трасса» — 180 с)" else "Запас буфера",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Segmented(listOf(10 to "10 с", 30 to "30 с", 60 to "60 с"), s.bufferSec) { v ->
                    Prefs.update { it.copy(bufferSec = v) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Больше запас — дольше играет без связи. Новое значение применяется сразу, станция переподключится.",
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
                )
            }
            Divider()
            ToggleRow(
                "Умная пауза",
                "короткая пауза продолжает с того же места и не сбрасывает запас",
                s.smartPause,
            ) { v -> Prefs.update { it.copy(smartPause = v) } }
            ToggleRow(
                "Копить буфер на ходу",
                "играет на 3% медленнее, пока запас не наберётся; высота звука не меняется",
                s.growBuffer, divider = false,
            ) { v -> Prefs.update { it.copy(growBuffer = v) } }
        }

        // ---- Если поток пропал ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle("Если поток пропал")
            ToggleRow(
                "Запасные ссылки",
                "ссылка не отвечает или зависла — переключиться на другую ссылку той же станции",
                s.fallback,
            ) { v -> Prefs.update { it.copy(fallback = v) } }
            ToggleRow(
                "Понижать качество",
                "слабая связь и запас тает — взять ссылку с меньшим битрейтом",
                s.lowerQuality,
            ) { v -> Prefs.update { it.copy(lowerQuality = v) } }
            Text(
                "Работает у станций, где есть несколько ссылок: удерживай плитку → «Ссылки потока». " +
                    "Переподключение при обрывах работает всегда.",
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- Звук ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle("Звук")
            ToggleRow(
                "Выравнивание громкости",
                "станции звучат одинаково громко; подстраивается плавно, за несколько секунд",
                s.leveler,
            ) { v -> Prefs.update { it.copy(leveler = v) } }
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("Эквалайзер", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val presets = listOf("off" to "Выкл", "bass" to "Бас", "voice" to "Голос", "car" to "Машина", "soft" to "Мягче", "custom" to "Свой")
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { (id, label) -> Chip(label, s.eqPreset == id) { Prefs.update { it.copy(eqPreset = id) } } }
                    }
                }
                if (s.eqPreset == "custom") {
                    listOf("60 Гц", "230 Гц", "910 Гц", "3.6 кГц", "14 кГц").forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 12.sp, color = C.muted, modifier = Modifier.width(64.dp))
                            Slider(
                                value = s.eqCustom[i].toFloat(),
                                onValueChange = { v ->
                                    Prefs.update { st -> st.copy(eqCustom = st.eqCustom.toMutableList().also { it[i] = v.toInt() }) }
                                },
                                valueRange = -6f..6f,
                                steps = 11,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.border),
                            )
                            Text(
                                (if (s.eqCustom[i] > 0) "+" else "") + "${s.eqCustom[i]}",
                                fontSize = 12.sp, fontFamily = Chakra, modifier = Modifier.width(28.dp),
                            )
                        }
                    }
                }
                Text(
                    "«Машина» — чуть больше баса и верхов, «Голос» — разборчивее новости и ток-шоу.",
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
                )
            }
            Divider()
            ToggleRow(
                "Трек на экране магнитолы",
                "песня, исполнитель и станция вместо «Bluetooth Audio»",
                s.showTrack, divider = false,
            ) { v -> Prefs.update { it.copy(showTrack = v) } }
        }

        // ---- Трафик ----
        Spacer(Modifier.height(10.dp))
        TrafficCard()

        // ---- Обновления ----
        Spacer(Modifier.height(10.dp))
        UpdatesCard(onCheckUpdate, onDownloadUpdate, onInstallUpdate)

        // ---- Станции ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle("Станции")
            ActionRow(
                title = "Сохранить в файл",
                hint = "все станции со ссылками — на случай смены телефона",
                icon = Ic.chevronRight,
                onClick = onExport,
            )
            ActionRow(
                title = "Загрузить из файла",
                hint = "добавит станции, которых ещё нет; свои не тронет",
                icon = Ic.chevronRight,
                divider = false,
                onClick = onImport,
            )
        }

        // ---- Фон ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle("Работа в фоне")
            ActionRow(
                title = "Батарея",
                hint = if (sys.batteryOk) "Без ограничений — радио не выключится в фоне"
                else "Телефон ограничивает приложение. Нажми, чтобы снять.",
                icon = if (sys.batteryOk) Ic.check else Ic.alert,
                iconTint = if (sys.batteryOk) C.accent else C.amber,
                onClick = onFixBattery,
            )
            ActionRow(
                title = "Уведомления",
                hint = if (sys.notificationsOk) "Разрешены — кнопки плеера на экране блокировки"
                else "Выключены — без них Android может остановить радио",
                icon = if (sys.notificationsOk) Ic.check else Ic.alert,
                iconTint = if (sys.notificationsOk) C.accent else C.amber,
                onClick = onFixNotifications,
            )
            if (s.carMac != null && !sys.bluetoothOk) {
                ActionRow(
                    title = "Доступ к Bluetooth",
                    hint = "Нужен, чтобы узнать, что подключилась именно машина",
                    icon = Ic.alert, iconTint = C.amber,
                    onClick = onPickCar,
                )
            }
            ActionRow(
                title = "На Samsung дополнительно",
                hint = "Свойства приложения → Батарея → «Не ограничено». И в Настройки → Батарея → " +
                    "Ограничения фонового использования → добавь «Авторадио» в приложения, которые никогда не спят.",
                icon = Ic.chevronRight,
                divider = false,
                onClick = onAppSettings,
            )
        }

        Text(
            "Авторадио ${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp, color = C.faint,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp, start = 4.dp),
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.line))
}

@Composable
private fun Segmented(options: List<Pair<Int, String>>, selected: Int, small: Boolean = false, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.bg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val sel = selected == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) C.accentBg else C.bg)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = if (small) 11.sp else 13.sp,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (sel) C.accent else C.soft,
                )
            }
        }
    }
}

/** Выбор цвета приложения: готовые, свой (оттенок + яркость) и цвет обоев. */
@Composable
private fun AccentPicker(s: Settings) {
    val wallpaper = s.accentSource == Settings.ACCENT_WALLPAPER
    val isPreset = !wallpaper && Accent.PRESETS.contains(s.accent)
    var customOpen by remember { mutableStateOf(!wallpaper && !isPreset) }
    val custom = customOpen && !wallpaper

    Text("Цвет", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Accent.PRESETS.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { c ->
                Swatch(
                    fill = Brush.linearGradient(listOf(Color(c), Color(c))),
                    selected = !wallpaper && !custom && s.accent == c,
                    desc = "Цвет",
                ) {
                    customOpen = false
                    Prefs.update { it.copy(accent = c, accentSource = Settings.ACCENT_FIXED) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Свой цвет", custom) {
            customOpen = true
            Prefs.update { it.copy(accentSource = Settings.ACCENT_FIXED) }
        }
        if (Accent.wallpaperAvailable()) {
            Chip("Цвет обоев", wallpaper) {
                customOpen = false
                Prefs.update { it.copy(accentSource = Settings.ACCENT_WALLPAPER) }
            }
        }
    }

    if (custom) {
        val hsl = remember(s.accent) { Accent.hsl(s.accent) }
        Spacer(Modifier.height(12.dp))
        Text("Оттенок", fontSize = 12.sp, color = C.muted)
        GradientSlider(
            value = hsl[0] / 360f,
            brush = Brush.horizontalGradient((0..6).map { Color(Accent.fromHsl(it * 60f, 0.85f, 0.6f)) }),
        ) { v -> Prefs.update { it.copy(accent = Accent.fromHsl(v * 360f, maxOf(hsl[1], 0.6f), hsl[2])) } }
        Spacer(Modifier.height(8.dp))
        Text("Яркость", fontSize = 12.sp, color = C.muted)
        GradientSlider(
            value = ((hsl[2] - 0.35f) / 0.45f).coerceIn(0f, 1f),
            brush = Brush.horizontalGradient(listOf(0.35f, 0.575f, 0.8f).map { Color(Accent.fromHsl(hsl[0], maxOf(hsl[1], 0.6f), it)) }),
        ) { v -> Prefs.update { it.copy(accent = Accent.fromHsl(hsl[0], maxOf(hsl[1], 0.6f), 0.35f + v * 0.45f)) } }
    }
    if (wallpaper) {
        Text(
            "Цвет берётся из обоев и меняется вместе с ними.",
            fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Swatch(fill: Brush, selected: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) C.text else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .padding(5.dp)
            .clip(CircleShape)
            .background(fill)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Ic.check, null, tint = Color(0xFF0A0E11), modifier = Modifier.size(20.dp))
    }
}

/** Ползунок с цветной полосой вместо обычной дорожки. */
@Composable
private fun GradientSlider(value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = C.text,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ScheduleRow(label: String, stationId: String?, stations: List<Station>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = stations.firstOrNull { it.id == stationId }?.name ?: "последняя"
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = C.soft, modifier = Modifier.weight(1f))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (stationId != null) C.accent else C.muted)
            Icon(Ic.chevronDown, null, tint = C.muted, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(C.card)) {
            DropdownMenuItem(text = { Text("Последняя станция", color = C.text) }, onClick = { onPick(null); open = false })
            stations.forEach { st ->
                DropdownMenuItem(text = { Text(st.name, color = C.text) }, onClick = { onPick(st.id); open = false })
            }
        }
    }
}

@Composable
private fun UpdatesCard(onCheck: () -> Unit, onDownload: (UpdateInfo) -> Unit, onInstall: (java.io.File) -> Unit) {
    val st by Updater.state.collectAsState()
    Card(padding = 16) {
        CardTitle("Обновления")
        Text("Установлена версия ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = C.soft)
        Spacer(Modifier.height(10.dp))
        when (val u = st) {
            is UpdateState.Checking -> Text("Проверяю…", fontSize = 13.sp, color = C.muted)
            is UpdateState.Available -> {
                Text("Доступна версия ${u.info.version}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.accent)
                if (u.info.notes.isNotBlank()) {
                    Text(u.info.notes, fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, maxLines = 8, modifier = Modifier.padding(vertical = 6.dp))
                }
                PillButton("Скачать и установить", filled = true) { onDownload(u.info) }
            }
            is UpdateState.Downloading -> {
                Text("Скачиваю ${u.info.version}… ${(u.progress * 100).toInt()}%", fontSize = 13.sp, color = C.soft)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
                    Box(Modifier.fillMaxWidth(u.progress.coerceAtLeast(0.02f)).height(6.dp).background(C.accent))
                }
            }
            is UpdateState.Ready -> {
                Text("Версия ${u.info.version} скачана", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Если Android попросит — разреши установку из «Авторадио». Станции и настройки сохранятся.",
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
                PillButton("Установить", filled = true) { onInstall(u.file) }
            }
            is UpdateState.UpToDate -> {
                Text("Это последняя версия", fontSize = 13.sp, color = C.muted)
                Spacer(Modifier.height(8.dp))
                PillButton("Проверить ещё раз", filled = false, onClick = onCheck)
            }
            is UpdateState.Failed -> {
                Text(u.message, fontSize = 13.sp, color = C.amber)
                Spacer(Modifier.height(8.dp))
                PillButton("Попробовать снова", filled = false, onClick = onCheck)
            }
            is UpdateState.Idle -> PillButton("Проверить обновления", filled = false, onClick = onCheck)
        }
    }
}

@Composable
private fun TrafficCard() {
    val t by Traffic.stats.collectAsState()
    Card(padding = 16) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Трафик радио", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "сбросить", fontSize = 12.sp, color = C.muted,
                modifier = Modifier.clickable { Traffic.reset() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficCell("Сегодня", t.todayTotal, t.todayMobile, Modifier.weight(1f))
            TrafficCell("Этот месяц", t.monthTotal, t.monthMobile, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Час радио 128 kbps — около 58 МБ. Буфер трафик не увеличивает: качается то же самое, только заранее.",
            fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun TrafficCell(label: String, total: Long, mobile: Long, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.bg)
            .padding(12.dp)
    ) {
        Text(label, fontSize = 12.sp, color = C.muted)
        Text(mb(total), fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Text("моб. сеть ${mb(mobile)}", fontSize = 12.sp, color = C.soft)
    }
}

private fun mb(bytes: Long): String {
    val m = bytes / 1_048_576.0
    return when {
        m >= 1024 -> "%.1f ГБ".format(m / 1024)
        m >= 10 -> "%.0f МБ".format(m)
        else -> "%.1f МБ".format(m)
    }
}

@Composable
fun CarPickerDialog(devices: List<Pair<String, String>>, current: String?, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        title = { Text("Какое устройство — машина?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "Сопряжённых устройств нет. Сначала соедини телефон с машиной по Bluetooth.",
                    color = C.soft, fontSize = 14.sp, lineHeight = 19.sp,
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    devices.forEach { (name, mac) ->
                        val sel = mac.equals(current, ignoreCase = true)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) C.accentBg else C.card)
                                .clickable { onPick(name, mac) }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (sel) C.accent else C.text)
                            Text(mac, fontSize = 11.sp, color = C.muted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = C.muted) }
        },
    )
}
