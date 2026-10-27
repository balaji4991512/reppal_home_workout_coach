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
    // Habit icons and a few controls.
    val Plus = icon("plus", "M12 5v14M5 12h14", stroke = 2f)
    val ChevronLeft = icon("chevl", "M15 6l-6 6 6 6", stroke = 1.8f)
    val Drop = icon("drop", "M12 3.5s6 6.2 6 10.5a6 6 0 0 1-12 0c0-4.3 6-10.5 6-10.5z")
    val Moon = icon("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Sun = icon("sun", circle(12f, 12f, 3.8f), "M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M18.5 5.5l-1.6 1.6M7.1 16.9l-1.6 1.6")
    val Book = icon("book", "M12 6.5C10.5 5 8 4.5 4.5 4.5v13c3.5 0 6 .5 7.5 2 1.5-1.5 4-2 7.5-2v-13c-3.5 0-6 .5-7.5 2zM12 6.5v13")
    val Pen = icon("pen", "M4 20l1-4L16.5 4.5a2.1 2.1 0 0 1 3 3L8 19z", "M14.5 6.5l3 3")
    val Walk = icon("walk", circle(13.5f, 4.5f, 1.8f), "M11 21l1.8-5-2.3-2.4.8-4.2M12.8 16l3.2 5M10.8 9.4L8.5 11l-.6 3M10.8 9.4l3.6.2 2.1 2.6")
    val Lotus = icon("lotus", "M12 5.5c2 2 3 4.2 3 6.8S13.5 17 12 18.5C10.5 17 9 14.9 9 12.3S10 7.5 12 5.5zM12 18.5c-3.4 0-6.3-1.5-8-4.5 3 0 5 .9 6 2M12 18.5c3.4 0 6.3-1.5 8-4.5-3 0-5 .9-6 2")
    val Heart = icon("heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.1a4.3 4.3 0 0 1 7.5 2.7C19.5 15.4 12 20 12 20z")
    val Star = icon("star", "M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z")
    val List = icon("list", "M9.5 6h10.5M9.5 12h10.5M9.5 18h10.5M4 6l1.2 1.2L7.4 5M4 12l1.2 1.2L7.4 11M4 18l1.2 1.2L7.4 17")
    val Bolt = icon("bolt", "M13 3L5.5 13.2h5.8L10.5 21l7.5-10.2h-5.8z")
    /** Six dots: a handle to drag by. */
    val Grip = icon("grip", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01", stroke = 2.8f)
    val ChevronDown = icon("chev", "M6 9l6 6 6-6", stroke = 1.5f)
}
