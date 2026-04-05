package com.dark.tool_neuron.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object TnIcons {
    val Menu by lazy { icon("M4 6h16", "M4 12h16", "M4 18h16") }
    val More by lazy {
        icon(
            "M 11 12 A 1 1 0 1 0 13 12 A 1 1 0 1 0 11 12 Z",
            "M 11 5 A 1 1 0 1 0 13 5 A 1 1 0 1 0 11 5 Z",
            "M 11 19 A 1 1 0 1 0 13 19 A 1 1 0 1 0 11 19 Z",
        )
    }
    val ChevronUp by lazy { icon("M6 15L12 9L18 15") }
    val ChevronDown by lazy { icon("M6 9L12 15L18 9") }
    val X by lazy { icon("M6 6L18 18", "M18 6L6 18") }
    val Lock by lazy {
        icon(
            "M8 11V7a4 4 0 0 1 8 0v4",
            "M5 11h14v10H5z"
        )
    }
    val Eye by lazy {
        icon(
            "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z",
            "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"
        )
    }
    val EyeOff by lazy {
        icon(
            "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94",
            "M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24",
            "M1 1l22 22"
        )
    }
    val Wrench by lazy {
        icon("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z")
    }
    val PlayerStop by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString("M7 7h10v10H7z").toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
    }
}

private fun icon(vararg paths: String): ImageVector {
    return ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        paths.forEach { svgPath ->
            addPath(
                pathData = PathParser().parsePathString(svgPath).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}
