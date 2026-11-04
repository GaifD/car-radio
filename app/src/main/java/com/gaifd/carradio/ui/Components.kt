package com.gaifd.carradio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Card(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(horizontal = padding.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 12.dp),
    )
}

@Composable
fun SectionTitle(text: String, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, fontSize = 12.sp, color = C.muted)
    }
}

/** Плитка без логотипа: у каждой станции свой цвет (из названия) и короткое имя крупно. */
@Composable
fun Monogram(text: String, size: Int, active: Boolean, big: Boolean = false, seed: String = text) {
    val light = C.bg == LightPalette.bg
    val hue = ((seed.lowercase().hashCode() and 0x7fffffff) % 360).toFloat()
    val bg = Color(Accent.fromHsl(hue, if (light) 0.55f else 0.42f, if (light) 0.88f else 0.2f))
    val fg = Color(Accent.fromHsl(hue, if (light) 0.6f else 0.75f, if (light) 0.32f else 0.74f))
    val shape = RoundedCornerShape((size / 3.5f).dp)
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(bg)
            .then(if (active && !big) Modifier.border(2.dp, C.accent, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val fs = when {
            big && text.length <= 3 -> 26.sp
            big -> 20.sp
            size <= 34 -> if (text.length <= 3) 11.sp else 9.sp
            text.length <= 3 -> 15.sp
            else -> 12.sp
        }
        Text(
            text,
            fontFamily = Chakra,
            fontWeight = FontWeight.Bold,
            fontSize = fs,
            color = fg,
            maxLines = 1,
        )
    }
}

/** Логотип станции, если есть, иначе цветная плитка. У подканала — подпись снизу («Disco»). */
@Composable
fun StationLogo(st: com.gaifd.carradio.data.Station, size: Int, active: Boolean, big: Boolean = false) {
    val ver by com.gaifd.carradio.data.Logos.version.collectAsState()
    val bmp = remember(st.id, ver) { com.gaifd.carradio.data.Logos.bitmap(st.id)?.asImageBitmap() }
    if (bmp == null) {
        Monogram(st.short, size, active, big, seed = st.name)
        return
    }
    val shape = RoundedCornerShape((size / 3.5f).dp)
    val badge = st.logoBadge.takeIf { it.isNotBlank() && size >= 40 }
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(C.logoBg)
            .then(if (active && !big) Modifier.border(2.dp, C.accent, shape) else Modifier),
    ) {
        Image(
            bmp, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = (size / 10).dp, end = (size / 10).dp, top = (size / 10).dp,
                    bottom = if (badge != null) (size / 4).dp else (size / 10).dp,
                ),
        )
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE60A0E11))
                    .padding(vertical = (size / 40).coerceAtLeast(1).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge,
                    fontSize = (size / 6.5f).coerceIn(8f, 18f).sp,
                    lineHeight = (size / 6.5f).coerceIn(8f, 18f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, hint: String, checked: Boolean, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onChange(!checked) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, fontSize = 12.sp, color = C.muted, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.onAccent,
                    checkedTrackColor = C.accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = C.muted,
                    uncheckedTrackColor = C.border,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(0.dp).heightIn(min = 1.dp, max = 1.dp).background(C.line))
    }
}

@Composable
fun ActionRow(
    title: String,
    hint: String,
    icon: ImageVector? = null,
    iconTint: Color = C.muted,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, fontSize = 12.sp, color = C.muted, lineHeight = 16.sp)
            }
            if (icon != null) {
                Spacer(Modifier.width(12.dp))
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(C.line))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(99.dp))
            .then(
                if (selected) Modifier.background(C.accentBg)
                else Modifier.border(1.dp, C.border, RoundedCornerShape(99.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) C.accent else C.soft,
        )
    }
}

@Composable
fun Banner(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.amberBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.alert, contentDescription = null, tint = C.amber, modifier = Modifier.size(22.dp))
        Text(text, fontSize = 13.sp, color = C.text, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Icon(Ic.chevronRight, contentDescription = null, tint = C.amber, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun OneLine(text: String, size: Int, color: Color = C.text, weight: FontWeight = FontWeight.Normal) {
    Text(text, fontSize = size.sp, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
