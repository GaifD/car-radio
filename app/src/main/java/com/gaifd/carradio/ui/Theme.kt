package com.gaifd.carradio.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gaifd.carradio.R

@Immutable
data class Palette(
    val bg: Color,
    val bar: Color,
    val card: Color,
    val line: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val soft: Color,
    val faint: Color,
    val accent: Color,
    val accentBg: Color,
    val onAccent: Color,
    val amber: Color,
    val amberBg: Color,
    val logoBg: Color,
)

/** Тёмная — как в приложении для самоката. */
val DarkPalette = Palette(
    bg = Color(0xFF0A0E11),
    bar = Color(0xFF0D1215),
    card = Color(0xFF12181C),
    line = Color(0xFF1A242B),
    border = Color(0xFF24303A),
    text = Color(0xFFE9F2F3),
    muted = Color(0xFF8C9DA6),
    soft = Color(0xFFB9C7CD),
    faint = Color(0xFF6F8089),
    accent = Color(0xFF2DE1D3),
    accentBg = Color(0xFF0F2B2A),
    onAccent = Color(0xFF0A0E11),
    amber = Color(0xFFFFB54A),
    amberBg = Color(0xFF2B2213),
    logoBg = Color(0xFFF4F7F8),
)

/** Светлая — для яркого солнца; бирюза темнее, чтобы читалась на белом. */
val LightPalette = Palette(
    bg = Color(0xFFF2F5F6),
    bar = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFE2E8EB),
    border = Color(0xFFCDD6DB),
    text = Color(0xFF0E1417),
    muted = Color(0xFF55656E),
    soft = Color(0xFF34434B),
    faint = Color(0xFF7E8D95),
    accent = Color(0xFF00897F),
    accentBg = Color(0xFFD7F2EF),
    onAccent = Color(0xFFFFFFFF),
    amber = Color(0xFFA35F00),
    amberBg = Color(0xFFFFEFD6),
    logoBg = Color(0xFFFFFFFF),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** Цвета текущей темы. Короткое имя — потому что используется повсюду. */
object C {
    val bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
    val bar: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bar
    val card: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
    val line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
    val border: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.border
    val text: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val soft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.soft
    val faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
    val accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val accentBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentBg
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
    val amber: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amber
    val amberBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.amberBg
    val logoBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.logoBg
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
fun CarRadioTheme(light: Boolean, accent: Int, content: @Composable () -> Unit) {
    val a = if (light) Accent.forLight(accent) else Accent.forDark(accent)
    val p = (if (light) LightPalette else DarkPalette).copy(
        accent = Color(a.accent),
        accentBg = Color(a.accentBg),
        onAccent = Color(a.onAccent),
    )
    val scheme = if (light) {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.muted, outline = p.border,
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.muted, outline = p.border,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides p,
            LocalTextStyle provides TextStyle(fontFamily = Manrope, color = p.text),
        ) {
            content()
        }
    }
}
