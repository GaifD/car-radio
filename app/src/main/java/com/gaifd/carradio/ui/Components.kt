package com.gaifd.carradio.ui

import androidx.compose.foundation.background
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

@Composable
fun Monogram(text: String, size: Int, active: Boolean, big: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.5f).dp))
            .background(if (big) C.accentBg else C.bg),
        contentAlignment = Alignment.Center,
    ) {
        val fs = when {
            big && text.length <= 3 -> 24.sp
            big -> 18.sp
            text.length <= 3 -> 14.sp
            else -> 12.sp
        }
        Text(
            text,
            fontFamily = Chakra,
            fontWeight = FontWeight.Bold,
            fontSize = fs,
            color = if (active || big) C.accent else C.soft,
            maxLines = 1,
        )
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
                    checkedThumbColor = C.bg,
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
