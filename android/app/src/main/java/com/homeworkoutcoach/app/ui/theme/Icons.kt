package com.homeworkoutcoach.app.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

/** Line icons, using the prototype's SVG paths on a 24x24 grid. Tint them with Icon(tint = ...). */
object AppIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.7f) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Menu = icon("menu", "M4 7h16M4 12h16M4 17h16")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Calendar = icon("cal", "M6 5h12a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5v-10A2.5 2.5 0 0 1 6 5z", "M3.5 10h17M8 3v4M16 3v4")
    val Dumbbell = icon("db", "M6.5 8v8M3.5 10v4M17.5 8v8M20.5 10v4M6.5 12h11")
    val User = icon("user", circle(12f, 8.5f, 3.5f), "M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5")
    val Sliders = icon("sl", "M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2f), circle(8f, 17f, 2f))
    val Info = icon("i", circle(12f, 12f, 9f), "M12 11v5M12 7.8v.2")
    val Chart = icon("ch", "M4 19V5M4 19h16M8 15l4-5 3 3 4-6")
    val Back = icon("back", "M19 12H5M11 6l-6 6 6 6")
    val Forward = icon("go", "M9 6l6 6-6 6")
    val Swap = icon("swap", "M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7", stroke = 1.8f)
    /** Bathroom scale: platform with a dial. */
    val Scale = icon("scale", "M6 3.5h12a2.5 2.5 0 0 1 2.5 2.5v12a2.5 2.5 0 0 1-2.5 2.5H6a2.5 2.5 0 0 1-2.5-2.5V6A2.5 2.5 0 0 1 6 3.5z", "M8.5 9.5a3.5 3.5 0 0 1 7 0M12 9.5l1.4-2")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5", stroke = 2.4f)
    val SignInOut = icon("out", "M9 4H5.5A1.5 1.5 0 0 0 4 5.5v13A1.5 1.5 0 0 0 5.5 20H9M15 8l4 4-4 4M19 12H9")
    /** Six dots: a handle to drag by. */
    val Grip = icon("grip", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01", stroke = 2.8f)
    val ChevronDown = icon("chev", "M6 9l6 6 6-6", stroke = 1.5f)
}
