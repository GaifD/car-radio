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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.BuildConfig
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Settings

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
            Text("Автозапуск в машине", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
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
            Text("Устойчивость к обрывам", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Запас буфера", fontSize = 12.sp, color = C.muted)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.bg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(10, 30, 60).forEach { sec ->
                    val sel = s.bufferSec == sec
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) C.accentBg else C.bg)
                            .clickable { Prefs.update { it.copy(bufferSec = sec) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$sec с",
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (sel) C.accent else C.soft,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Больше запас — дольше играет без связи. Новое значение сработает со следующей станции.",
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                "Копить буфер",
                "играет на 3% медленнее, пока запас не наберётся; высота звука не меняется",
                s.growBuffer,
            ) { v -> Prefs.update { it.copy(growBuffer = v) } }
            ToggleRow("Переподключаться сам", "без ошибок на экране, молча — всегда включено", true, divider = false) {}
        }

        // ---- Магнитола ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            ToggleRow(
                "Трек на экране магнитолы",
                "песня и станция вместо «Bluetooth Audio»",
                s.showTrack, divider = false,
            ) { v -> Prefs.update { it.copy(showTrack = v) } }
        }

        // ---- Фон ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            Text("Работа в фоне", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
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
