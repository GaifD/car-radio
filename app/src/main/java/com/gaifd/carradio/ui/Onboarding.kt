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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.L
import com.gaifd.carradio.R
import com.gaifd.carradio.data.Country
import com.gaifd.carradio.data.Prefs
import com.gaifd.carradio.data.Settings

/**
 * Первый запуск: язык → страна → работа в фоне → машина. Любой шаг можно пропустить.
 * Номер шага переживает смену языка (экран при этом пересоздаётся).
 */
@Composable
fun Onboarding(
    settings: Settings,
    sys: SystemStatus,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onPickCar: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 3
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                for (i in 0..last) {
                    Box(
                        Modifier
                            .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i <= step) C.accent else C.border)
                    )
                }
            }
            Text(
                L.s(R.string.ob_skip), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinish).padding(10.dp),
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    StationLogoBig()
                    Title(L.s(R.string.ob_welcome_title))
                    Body(L.s(R.string.ob_welcome_text))
                    Spacer(Modifier.height(4.dp))
                    Card(padding = 16) { LanguagePicker() }
                }
                1 -> {
                    Title(L.s(R.string.ob_country_title))
                    Body(L.s(R.string.ob_country_text))
                    var picking by remember { mutableStateOf(false) }
                    val code = Country.current(ctx)
                    Card(padding = 16) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(code, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.accent, modifier = Modifier.width(40.dp))
                            Text(Country.name(code), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                L.s(R.string.ob_change), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.accent,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }.padding(8.dp),
                            )
                        }
                    }
                    if (picking) CountryPickerDialog(code, onPick = { c ->
                        picking = false
                        Prefs.update { it.copy(country = c, searchUkraine = true) }
                    }, onDismiss = { picking = false })
                }
                2 -> {
                    Title(L.s(R.string.ob_bg_title))
                    Body(L.s(R.string.ob_bg_text))
                    CheckButton(L.s(R.string.ob_notif_btn), sys.notificationsOk, onNotifications)
                    CheckButton(L.s(R.string.ob_battery_btn), sys.batteryOk, onBattery)
                    if (DeviceGuide.hasExtra) {
                        Card(padding = 16) {
                            Text(L.s(R.string.dev_extra, DeviceGuide.brand), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(DeviceGuide.steps(), fontSize = 13.sp, color = C.soft, lineHeight = 19.sp)
                            Spacer(Modifier.height(10.dp))
                            PillButton(L.s(R.string.dev_open), filled = false) { DeviceGuide.open(ctx) }
                        }
                    }
                }
                else -> {
                    Title(L.s(R.string.ob_car_title))
                    Body(L.s(R.string.ob_car_text))
                    if (settings.carName != null) {
                        CheckButton(settings.carName, true, onPickCar)
                    } else {
                        PillButton(L.s(R.string.ob_car_pick), filled = false, onClick = onPickCar)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) {
                Box(Modifier.weight(1f)) { PillButton(L.s(R.string.ob_back), filled = false) { step-- } }
            }
            Box(Modifier.weight(if (step > 0) 2f else 1f)) {
                PillButton(if (step < last) L.s(R.string.ob_next) else L.s(R.string.ob_done), filled = true) {
                    if (step == 1 && settings.country.isBlank()) {
                        // Запоминаем определённую страну, чтобы она не «плавала» при смене SIM.
                        val c = Country.current(ctx)
                        Prefs.update { it.copy(country = c) }
                    }
                    if (step < last) step++ else onFinish()
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 15.sp, color = C.soft, lineHeight = 22.sp)
}

/** Значок приложения крупно — шкала FM. */
@Composable
private fun StationLogoBig() {
    Box(
        Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(C.card),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_fg),
            contentDescription = null,
            tint = C.accent,
            modifier = Modifier.size(88.dp),
        )
    }
}

/** Кнопка-действие с отметкой «сделано». */
@Composable
private fun CheckButton(text: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (done) C.accentBg else C.card)
            .border(1.dp, if (done) C.accent else C.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done) C.accent else C.border),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}
