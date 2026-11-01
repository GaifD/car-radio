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
import com.gaifd.carradio.data.Traffic

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
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Настройки")

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
            ToggleRow(
                "Трек на экране магнитолы",
                "песня, исполнитель и станция вместо «Bluetooth Audio»",
                s.showTrack, divider = false,
            ) { v -> Prefs.update { it.copy(showTrack = v) } }
        }

        // ---- Трафик ----
        Spacer(Modifier.height(10.dp))
        TrafficCard()

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
private fun Segmented(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
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
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (sel) C.accent else C.soft,
                )
            }
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
