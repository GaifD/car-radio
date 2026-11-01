package com.gaifd.carradio.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gaifd.carradio.R

/** Палитра как в приложении для самоката. */
object C {
    val bg = Color(0xFF0A0E11)
    val bar = Color(0xFF0D1215)
    val card = Color(0xFF12181C)
    val line = Color(0xFF1A242B)
    val border = Color(0xFF24303A)
    val text = Color(0xFFE9F2F3)
    val muted = Color(0xFF8C9DA6)
    val soft = Color(0xFFB9C7CD)
    val faint = Color(0xFF6F8089)
    val accent = Color(0xFF2DE1D3)
    val accentBg = Color(0xFF0F2B2A)
    val amber = Color(0xFFFFB54A)
    val amberBg = Color(0xFF2B2213)
}

val Chakra = FontFamily(
    Font(R.font.chakra_medium, FontWeight.Medium),
    Font(R.font.chakra_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.manrope,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

@Composable
fun CarRadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = C.bg,
            background = C.bg,
            onBackground = C.text,
            surface = C.card,
            onSurface = C.text,
            surfaceVariant = C.card,
            onSurfaceVariant = C.muted,
            outline = C.border,
        ),
    ) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Manrope, color = C.text)) {
            content()
        }
    }
}
