package com.gaifd.carradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaifd.carradio.L
import com.gaifd.carradio.R
import com.gaifd.carradio.data.Country

/** Выбор страны: поиск по названию на языке приложения, популярные сверху. */
@Composable
fun CountryPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember { Country.all().map { it to Country.name(it) } }
    val list = if (q.isBlank()) all else all.filter { (code, name) ->
        name.contains(q.trim(), ignoreCase = true) || code.equals(q.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        titleContentColor = C.text,
        title = { Text(L.s(R.string.country_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.bg)
                        .border(1.dp, C.border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (q.isEmpty()) Text(L.s(R.string.country_search), fontSize = 15.sp, color = C.faint)
                    BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Manrope, fontSize = 15.sp, color = C.text),
                        cursorBrush = SolidColor(C.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.first }) { (code, name) ->
                        val sel = code == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) C.accentBg else C.card)
                                .clickable { onPick(code) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(code, fontSize = 12.sp, fontFamily = Chakra, fontWeight = FontWeight.Bold, color = C.muted, modifier = Modifier.width(32.dp))
                            Text(name, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) C.accent else C.text)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L.s(R.string.songs_034), color = C.muted) } },
    )
}
