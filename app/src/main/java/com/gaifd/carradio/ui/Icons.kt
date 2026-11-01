package com.gaifd.carradio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Тонкие линейные иконки из макета (те же пути, что и в дизайне). */
private fun stroke(width: Float, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    )
    for (p in paths) {
        b.addPath(
            pathData = addPathNodes(p),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

private fun filled(vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    )
    for (p in paths) b.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
    return b.build()
}

object Ic {
    val play by lazy { filled("M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z") }
    val pause by lazy { filled("M7 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M14 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z") }
    val prev by lazy { stroke(2f, "M19 20L9 12l10-8v16z", "M5 19V5") }
    val next by lazy { stroke(2f, "M5 4l10 8-10 8V4z", "M19 5v14") }
    val radio by lazy {
        stroke(
            2f,
            "M5 8h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2z",
            "M7 4l10 4",
            "M13 14.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
            "M6 13h4", "M6 16h4",
        )
    }
    val search by lazy { stroke(2f, "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M21 21l-4.3-4.3") }
    val sliders by lazy {
        stroke(
            2f,
            "M4 6h10", "M18 6h2", "M14 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M4 12h4", "M12 12h8", "M8 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M4 18h12", "M16 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        )
    }
    val plus by lazy { stroke(2.5f, "M12 5v14", "M5 12h14") }
    val check by lazy { stroke(2.5f, "M5 12l5 5L20 7") }
    val chevronDown by lazy { stroke(2f, "M6 9l6 6 6-6") }
    val chevronRight by lazy { stroke(2f, "M9 6l6 6-6 6") }
    val alert by lazy {
        stroke(
            2f,
            "M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
            "M12 9v4", "M12 17h.01",
        )
    }
    val link by lazy {
        stroke(
            2f,
            "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7",
            "M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
        )
    }
    val close by lazy { stroke(2f, "M6 6l12 12", "M18 6L6 18") }
}
