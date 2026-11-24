package com.gaifd.carradio.ui

import com.gaifd.carradio.R
import com.gaifd.carradio.L

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
        ScreenTitle(L.s(R.string.main_019))

        // ---- Вид ----
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_001))
            LanguagePicker()
            CountryRow(s)
            Spacer(Modifier.height(6.dp))
            Text(L.s(R.string.set_002), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(Settings.THEME_DARK to L.s(R.string.set_003), Settings.THEME_LIGHT to L.s(R.string.set_004), Settings.THEME_SYSTEM to L.s(R.string.set_005), Settings.THEME_BY_TIME to L.s(R.string.set_006)),
                s.theme, small = true,
            ) { v -> Prefs.update { it.copy(theme = v) } }
            if (s.theme == Settings.THEME_BY_TIME) {
                Text(L.s(R.string.set_007), fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            AccentPicker(s)
            Spacer(Modifier.height(14.dp))
            Text(L.s(R.string.set_008), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Segmented(
                listOf(Settings.VIEW_GRID to L.s(R.string.set_009), Settings.VIEW_LIST to L.s(R.string.set_010), Settings.VIEW_BIG to L.s(R.string.set_011), Settings.VIEW_COMPACT to L.s(R.string.set_012)),
                s.stationView, small = true,
            ) { v -> Prefs.update { it.copy(stationView = v) } }
            Spacer(Modifier.height(6.dp))
            ActionRow(
                title = L.s(R.string.set_013),
                hint = logoStatus ?: L.s(R.string.set_014),
                icon = Ic.image,
                divider = false,
                onClick = onFetchLogos,
            )
        }
        Spacer(Modifier.height(10.dp))

        // ---- Автозапуск ----
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_015))
            ActionRow(
                title = L.s(R.string.set_016),
                hint = s.carName?.let { L.s(R.string.set_017, it) } ?: L.s(R.string.set_018),
                icon = Ic.chevronDown,
                iconTint = if (s.carMac == null) C.accent else C.muted,
                onClick = onPickCar,
            )
            if (s.carMac != null) {
                ActionRow(
                    title = L.s(R.string.set_019),
                    hint = if (sys.carLinked) L.s(R.string.set_020)
                    else L.s(R.string.set_021),
                    icon = if (sys.carLinked) Ic.check else Ic.link,
                    iconTint = if (sys.carLinked) C.accent else C.amber,
                    onClick = onLinkCar,
                )
            }
            ToggleRow(L.s(R.string.set_022), L.s(R.string.set_023), s.autoPlay) { v ->
                Prefs.update { it.copy(autoPlay = v) }
            }
            ToggleRow(L.s(R.string.set_024), L.s(R.string.set_025), s.scheduleOn) { v ->
                Prefs.update { it.copy(scheduleOn = v) }
            }
            if (s.scheduleOn) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Settings.PERIODS.forEach { (key, label) ->
                        ScheduleRow(L.s(label), s.schedule[key], stations) { id ->
                            Prefs.update { it.copy(schedule = if (id == null) it.schedule - key else it.schedule + (key to id)) }
                        }
                    }
                }
                Divider()
            }
            ToggleRow(L.s(R.string.set_026), L.s(R.string.set_027), s.pauseOnDisconnect) { v ->
                Prefs.update { it.copy(pauseOnDisconnect = v) }
            }
            ToggleRow(L.s(R.string.set_028), L.s(R.string.set_029), s.limitStartVolume, divider = false) { v ->
                Prefs.update { it.copy(limitStartVolume = v) }
            }
        }

        // ---- Переход ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(L.s(R.string.set_030), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (s.crossfadeMs == 0) L.s(R.string.set_031) else L.s(R.string.set_032).format(s.crossfadeMs / 1000f),
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
                L.s(R.string.set_033),
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
            )
        }

        // ---- Буфер ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_034))
            Text(
                L.s(R.string.set_035),
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            // Один переключатель на всё: «Трасса» — четвёртый вариант, чтобы было видно, что реально работает.
            val HIGHWAY = 99
            Segmented(
                listOf(Settings.BUFFER_OFF to L.s(R.string.set_036), Settings.BUFFER_ON to L.s(R.string.set_037), Settings.BUFFER_AUTO to L.s(R.string.set_038), HIGHWAY to L.s(R.string.radio_014)),
                if (s.highway) HIGHWAY else s.bufferMode,
            ) { v ->
                Prefs.update { if (v == HIGHWAY) it.copy(highway = true) else it.copy(bufferMode = v, highway = false) }
            }
            Text(
                when {
                    s.highway -> L.s(R.string.set_039)
                    s.bufferMode == Settings.BUFFER_ON -> L.s(R.string.set_040)
                    s.bufferMode == Settings.BUFFER_AUTO -> L.s(R.string.set_041)
                    else -> L.s(R.string.set_042)
                },
                fontSize = 12.sp, color = C.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
            if (s.bufferMode != Settings.BUFFER_OFF && !s.highway) {
                Divider()
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(L.s(R.string.set_043), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf(10 to L.s(R.string.set_044), 30 to L.s(R.string.set_045), 60 to L.s(R.string.set_046)), s.bufferSec) { v ->
                        Prefs.update { it.copy(bufferSec = v) }
                    }
                }
            }
            if (s.bufferMode != Settings.BUFFER_OFF || s.highway) {
                Divider()
                ToggleRow(
                    L.s(R.string.set_047),
                    L.s(R.string.set_048),
                    s.growBuffer, divider = false,
                ) { v -> Prefs.update { it.copy(growBuffer = v) } }
            }
            Text(
                L.s(R.string.set_049),
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- Если поток пропал ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_050))
            ToggleRow(
                L.s(R.string.set_051),
                L.s(R.string.set_052),
                s.fallback,
            ) { v -> Prefs.update { it.copy(fallback = v) } }
            ToggleRow(
                L.s(R.string.set_053),
                L.s(R.string.set_054),
                s.lowerQuality,
            ) { v -> Prefs.update { it.copy(lowerQuality = v) } }
            Text(
                L.s(R.string.set_055),
                fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- Звук ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_056))
            ToggleRow(
                L.s(R.string.set_057),
                L.s(R.string.set_058),
                s.leveler,
            ) { v -> Prefs.update { it.copy(leveler = v) } }
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(L.s(R.string.set_059), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val presets = listOf("off" to L.s(R.string.set_036), "bass" to L.s(R.string.set_060), "voice" to L.s(R.string.set_061), "car" to L.s(R.string.set_016), "soft" to L.s(R.string.set_062), "custom" to L.s(R.string.set_063))
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { (id, label) -> Chip(label, s.eqPreset == id) { Prefs.update { it.copy(eqPreset = id) } } }
                    }
                }
                if (s.eqPreset == "custom") {
                    listOf(L.s(R.string.set_064), L.s(R.string.set_065), L.s(R.string.set_066), L.s(R.string.set_067), L.s(R.string.set_068)).forEachIndexed { i, label ->
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
                    L.s(R.string.set_069),
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp,
                )
            }
            Divider()
            ToggleRow(
                L.s(R.string.set_070),
                L.s(R.string.set_071),
                s.showTrack, divider = false,
            ) { v -> Prefs.update { it.copy(showTrack = v) } }
        }

        // ---- Трафик ----
        Spacer(Modifier.height(10.dp))
        TrafficCard()

        // ---- Обновления (в версии для Play их делает сам Play) ----
        if (BuildConfig.IN_APP_UPDATES) {
            Spacer(Modifier.height(10.dp))
            UpdatesCard(onCheckUpdate, onDownloadUpdate, onInstallUpdate)
        }

        // ---- Станции ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_072))
            ActionRow(
                title = L.s(R.string.set_073),
                hint = L.s(R.string.set_074),
                icon = Ic.chevronRight,
                onClick = onExport,
            )
            ActionRow(
                title = L.s(R.string.set_075),
                hint = L.s(R.string.set_076),
                icon = Ic.chevronRight,
                divider = false,
                onClick = onImport,
            )
        }

        // ---- Фон ----
        Spacer(Modifier.height(10.dp))
        Card(padding = 16) {
            CardTitle(L.s(R.string.set_077))
            ActionRow(
                title = L.s(R.string.set_078),
                hint = if (sys.batteryOk) L.s(R.string.set_079)
                else L.s(R.string.set_080),
                icon = if (sys.batteryOk) Ic.check else Ic.alert,
                iconTint = if (sys.batteryOk) C.accent else C.amber,
                onClick = onFixBattery,
            )
            ActionRow(
                title = L.s(R.string.set_081),
                hint = if (sys.notificationsOk) L.s(R.string.set_082)
                else L.s(R.string.set_083),
                icon = if (sys.notificationsOk) Ic.check else Ic.alert,
                iconTint = if (sys.notificationsOk) C.accent else C.amber,
                onClick = onFixNotifications,
            )
            if (s.carMac != null && !sys.bluetoothOk) {
                ActionRow(
                    title = L.s(R.string.set_084),
                    hint = L.s(R.string.set_085),
                    icon = Ic.alert, iconTint = C.amber,
                    onClick = onPickCar,
                )
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (DeviceGuide.hasExtra) {
                ActionRow(
                    title = L.s(R.string.dev_extra, DeviceGuide.brand),
                    hint = DeviceGuide.steps(),
                    icon = Ic.chevronRight,
                    divider = false,
                    onClick = { DeviceGuide.open(ctx) },
                )
            } else {
                ActionRow(
                    title = L.s(R.string.dev_open),
                    hint = DeviceGuide.steps(),
                    icon = Ic.chevronRight,
                    divider = false,
                    onClick = onAppSettings,
                )
            }
        }

        Text(
            L.s(R.string.set_088, BuildConfig.VERSION_NAME),
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
fun Segmented(options: List<Pair<Int, String>>, selected: Int, small: Boolean = false, onSelect: (Int) -> Unit) {
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

/** Язык приложения: как в системе или свой. Названия — на своих языках, чтобы их узнавали. */
val LANGUAGES = listOf("" to null, "uk" to "Українська", "en" to "English", "ru" to "Русский")

@Composable
fun LanguagePicker() {
    val current = L.appLanguage()
    Text(L.s(R.string.set_lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Segmented(
        LANGUAGES.mapIndexed { i, (_, name) -> i to (name ?: L.s(R.string.set_005)) },
        LANGUAGES.indexOfFirst { it.first == current }.coerceAtLeast(0),
        small = true,
    ) { i -> L.setAppLanguage(LANGUAGES[i].first) }
}

@Composable
fun CountryRow(s: Settings) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val code = remember(s.country) { com.gaifd.carradio.data.Country.current(ctx) }
    var open by remember { mutableStateOf(false) }
    ActionRow(
        title = L.s(R.string.country_title),
        hint = L.s(R.string.country_hint, com.gaifd.carradio.data.Country.name(code)),
        icon = Ic.chevronRight,
        divider = false,
        onClick = { open = true },
    )
    if (open) CountryPickerDialog(code, onPick = { c ->
        open = false
        Prefs.update { it.copy(country = c, searchUkraine = true) }
    }, onDismiss = { open = false })
}

/** Выбор цвета приложения: готовые, свой (оттенок + яркость) и цвет обоев. */
@Composable
private fun AccentPicker(s: Settings) {
    val wallpaper = s.accentSource == Settings.ACCENT_WALLPAPER
    val isPreset = !wallpaper && Accent.PRESETS.contains(s.accent)
    var customOpen by remember { mutableStateOf(!wallpaper && !isPreset) }
    val custom = customOpen && !wallpaper

    Text(L.s(R.string.set_089), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Accent.PRESETS.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { c ->
                Swatch(
                    fill = Brush.linearGradient(listOf(Color(c), Color(c))),
                    selected = !wallpaper && !custom && s.accent == c,
                    desc = L.s(R.string.set_089),
                ) {
                    customOpen = false
                    Prefs.update { it.copy(accent = c, accentSource = Settings.ACCENT_FIXED) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(L.s(R.string.set_090), custom) {
            customOpen = true
            Prefs.update { it.copy(accentSource = Settings.ACCENT_FIXED) }
        }
        if (Accent.wallpaperAvailable()) {
            Chip(L.s(R.string.set_091), wallpaper) {
                customOpen = false
                Prefs.update { it.copy(accentSource = Settings.ACCENT_WALLPAPER) }
            }
        }
    }

    if (custom) {
        val hsl = remember(s.accent) { Accent.hsl(s.accent) }
        Spacer(Modifier.height(12.dp))
        Text(L.s(R.string.set_092), fontSize = 12.sp, color = C.muted)
        GradientSlider(
            value = hsl[0] / 360f,
            brush = Brush.horizontalGradient((0..6).map { Color(Accent.fromHsl(it * 60f, 0.85f, 0.6f)) }),
        ) { v -> Prefs.update { it.copy(accent = Accent.fromHsl(v * 360f, maxOf(hsl[1], 0.6f), hsl[2])) } }
        Spacer(Modifier.height(8.dp))
        Text(L.s(R.string.set_093), fontSize = 12.sp, color = C.muted)
        GradientSlider(
            value = ((hsl[2] - 0.35f) / 0.45f).coerceIn(0f, 1f),
            brush = Brush.horizontalGradient(listOf(0.35f, 0.575f, 0.8f).map { Color(Accent.fromHsl(hsl[0], maxOf(hsl[1], 0.6f), it)) }),
        ) { v -> Prefs.update { it.copy(accent = Accent.fromHsl(hsl[0], maxOf(hsl[1], 0.6f), 0.35f + v * 0.45f)) } }
    }
    if (wallpaper) {
        Text(
            L.s(R.string.set_094),
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
    val name = stations.firstOrNull { it.id == stationId }?.name ?: L.s(R.string.set_095)
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
            DropdownMenuItem(text = { Text(L.s(R.string.set_096), color = C.text) }, onClick = { onPick(null); open = false })
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
        CardTitle(L.s(R.string.set_097))
        Text(L.s(R.string.set_098, BuildConfig.VERSION_NAME), fontSize = 13.sp, color = C.soft)
        Spacer(Modifier.height(10.dp))
        when (val u = st) {
            is UpdateState.Checking -> Text(L.s(R.string.songs_012), fontSize = 13.sp, color = C.muted)
            is UpdateState.Available -> {
                Text(L.s(R.string.set_099, u.info.version), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.accent)
                if (u.info.notes.isNotBlank()) {
                    Text(u.info.notes, fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, maxLines = 8, modifier = Modifier.padding(vertical = 6.dp))
                }
                PillButton(L.s(R.string.set_100), filled = true) { onDownload(u.info) }
            }
            is UpdateState.Downloading -> {
                Text(L.s(R.string.set_101, u.info.version, (u.progress * 100).toInt()), fontSize = 13.sp, color = C.soft)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.line)) {
                    Box(Modifier.fillMaxWidth(u.progress.coerceAtLeast(0.02f)).height(6.dp).background(C.accent))
                }
            }
            is UpdateState.Ready -> {
                Text(L.s(R.string.set_102, u.info.version), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    L.s(R.string.set_103),
                    fontSize = 12.sp, color = C.soft, lineHeight = 17.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
                PillButton(L.s(R.string.set_104), filled = true) { onInstall(u.file) }
            }
            is UpdateState.UpToDate -> {
                Text(L.s(R.string.set_105), fontSize = 13.sp, color = C.muted)
                Spacer(Modifier.height(8.dp))
                PillButton(L.s(R.string.set_106), filled = false, onClick = onCheck)
            }
            is UpdateState.Failed -> {
                Text(u.message, fontSize = 13.sp, color = C.amber)
                Spacer(Modifier.height(8.dp))
                PillButton(L.s(R.string.set_107), filled = false, onClick = onCheck)
            }
            is UpdateState.Idle -> PillButton(L.s(R.string.set_108), filled = false, onClick = onCheck)
        }
    }
}

@Composable
private fun TrafficCard() {
    val t by Traffic.stats.collectAsState()
    Card(padding = 16) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(L.s(R.string.set_109), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                L.s(R.string.set_110), fontSize = 12.sp, color = C.muted,
                modifier = Modifier.clickable { Traffic.reset() }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficCell(L.s(R.string.songs_035), t.todayTotal, t.todayMobile, Modifier.weight(1f))
            TrafficCell(L.s(R.string.songs_028), t.monthTotal, t.monthMobile, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L.s(R.string.set_111),
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
        Text(L.s(R.string.set_112, mb(mobile)), fontSize = 12.sp, color = C.soft)
    }
}

private fun mb(bytes: Long): String {
    val m = bytes / 1_048_576.0
    return when {
        m >= 1024 -> L.s(R.string.set_113).format(m / 1024)
        m >= 10 -> L.s(R.string.set_114).format(m)
        else -> L.s(R.string.set_115).format(m)
    }
}

@Composable
fun CarPickerDialog(devices: List<Pair<String, String>>, current: String?, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        title = { Text(L.s(R.string.set_116), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            if (devices.isEmpty()) {
                Text(
                    L.s(R.string.set_117),
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
            TextButton(onClick = onDismiss) { Text(L.s(R.string.songs_034), color = C.muted) }
        },
    )
}
