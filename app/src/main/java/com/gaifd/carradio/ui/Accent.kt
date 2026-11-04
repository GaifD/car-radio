package com.gaifd.carradio.ui

import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.gaifd.carradio.data.Settings

/** Цвет приложения и производные от него: подстраиваются под тёмную и светлую тему, чтобы всё читалось. */
object Accent {
    /** Готовые цвета: бирюза (по умолчанию), синий, фиолетовый, розовый, красный, оранжевый, янтарный, зелёный. */
    val PRESETS = listOf(
        0xFF2DE1D3.toInt(), 0xFF4C9AFF.toInt(), 0xFFA78BFA.toInt(), 0xFFF472B6.toInt(),
        0xFFF87171.toInt(), 0xFFFB923C.toInt(), 0xFFFBBF24.toInt(), 0xFF4ADE80.toInt(),
    )

    fun wallpaperAvailable() = Build.VERSION.SDK_INT >= 31

    /** Базовый цвет: выбранный или из обоев (Android 12+). */
    fun base(ctx: Context, s: Settings): Int =
        if (s.accentSource == Settings.ACCENT_WALLPAPER && wallpaperAvailable()) {
            try {
                ctx.getColor(android.R.color.system_accent1_200)
            } catch (_: Exception) {
                s.accent
            }
        } else s.accent

    data class Set(val accent: Int, val accentBg: Int, val onAccent: Int)

    private const val DARK_BG = 0xFF0A0E11.toInt()
    private const val DARK_CARD = 0xFF12181C.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF0A0E11.toInt()

    /** На тёмном фоне цвет должен быть достаточно светлым. */
    fun forDark(base: Int): Set {
        var c = base
        var guard = 0
        while (ColorUtils.calculateContrast(c, DARK_CARD) < 4.5 && guard++ < 20) c = ColorUtils.blendARGB(c, WHITE, 0.08f)
        return Set(c, ColorUtils.blendARGB(DARK_BG, c, 0.16f), onColor(c))
    }

    /** На светлом фоне — наоборот, темнее (бирюза → #00897F и т.п.). */
    fun forLight(base: Int): Set {
        var c = base
        var guard = 0
        while (ColorUtils.calculateContrast(c, WHITE) < 4.5 && guard++ < 30) c = ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.07f)
        return Set(c, ColorUtils.blendARGB(WHITE, base, 0.18f), onColor(c))
    }

    /** Текст и значки на цветной кнопке: чёрные или белые — что лучше читается. */
    fun onColor(c: Int): Int =
        if (ColorUtils.calculateContrast(BLACK, c) >= ColorUtils.calculateContrast(WHITE, c)) BLACK else WHITE

    fun hsl(c: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(c, it) }

    fun fromHsl(h: Float, s: Float, l: Float): Int = ColorUtils.HSLToColor(floatArrayOf(h, s, l))
}
