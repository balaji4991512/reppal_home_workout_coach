package com.homeworkoutcoach.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Volume
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Volume per session line chart on a 360 x 210 grid with nice-step axis [chart]. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    unit: String,
    zone: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
    /** False: the axis starts near the lowest value (body weight), not at zero (volume). */
    fromZero: Boolean = true,
    /** Axis label format. */
    label: (Double) -> String = { "${it.roundToLong()}" },
    /** Soft fill under the line, and the latest point drawn larger. */
    emphasis: Boolean = false,
) {
    val c = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = c.mut, fontSize = 12.sp)
    val description = if (points.isEmpty()) "" else
        "$unit per session, from ${points.first().value.roundToLong()} on ${Copy.dayMonth(points.first().dateMillis, zone)} " +
            "to ${points.last().value.roundToLong()} on ${Copy.dayMonth(points.last().dateMillis, zone)}"

    Canvas(
        modifier.fillMaxWidth().aspectRatio(360f / 210f).padding(top = 6.dp).semantics { contentDescription = description },
    ) {
        val u = size.width / W
        val max = points.maxOf { it.value }
        val min = points.minOf { it.value }
        val step = if (fromZero) Volume.niceStep(max / 4) else Volume.niceStep(maxOf(max - min, 1.0) / 4)
        val bottom = if (fromZero) 0.0 else floor(min / step) * step
        val top = maxOf(bottom + step, ceil(max / step) * step)
        val x0 = points.first().dateMillis.toDouble()
        val x1 = points.last().dateMillis.toDouble()
        val cw = W - L - R
        fun xs(d: Long) = (if (x1 == x0) L + cw / 2 else L + ((d - x0) / (x1 - x0)).toFloat() * cw) * u
        fun ys(v: Double) = (T + (1 - ((v - bottom) / (top - bottom)).toFloat()) * (H - T - B)) * u

        fun text(s: String, x: Float, y: Float, align: Align) {
            val m = measurer.measure(s, labelStyle)
            val left = when (align) {
                Align.START -> x
                Align.MIDDLE -> x - m.size.width / 2f
                Align.END -> x - m.size.width
            }
            // y is the baseline, as in SVG.
            drawText(m, topLeft = Offset(left, y - m.firstBaseline))
        }

        var v = bottom
        while (v <= top + 0.001) {
            val y = ys(v)
            drawLine(c.bd, Offset(L * u, y), Offset((W - R) * u, y), strokeWidth = 1f * u)
            text(label(v), (L - 8) * u, y + 4 * u, Align.END)
            v += step
        }

        val path = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(xs(p.dateMillis), ys(p.value)) else lineTo(xs(p.dateMillis), ys(p.value))
            }
        }
        if (emphasis && points.size > 1) {
            val area = Path().apply {
                addPath(path)
                lineTo(xs(points.last().dateMillis), ys(bottom))
                lineTo(xs(points.first().dateMillis), ys(bottom))
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(c.fg.copy(alpha = 0.12f), c.fg.copy(alpha = 0f)), startY = T * u, endY = (H - B) * u))
        }
        drawPath(path, c.fg, style = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (emphasis) {
            val last = points.last()
            val p = Offset(xs(last.dateMillis), ys(last.value))
            drawCircle(c.fg.copy(alpha = 0.15f), radius = 9f * u, center = p)
            drawCircle(c.fg, radius = 5f * u, center = p)
        }
        points.forEach { drawCircle(c.fg, radius = 3.6f * u, center = Offset(xs(it.dateMillis), ys(it.value))) }

        val baseline = (H - 8) * u
        val first = points.first()
        if (x1 == x0) {
            text(Copy.dayMonth(first.dateMillis, zone), xs(first.dateMillis), baseline, Align.MIDDLE)
        } else {
            text(Copy.dayMonth(first.dateMillis, zone), L * u, baseline, Align.START)
            text(Copy.dayMonth(points.last().dateMillis, zone), (W - R) * u, baseline, Align.END)
            if (points.size >= 3) {
                val m = points[points.size / 2]
                if (m.dateMillis > x0 + (x1 - x0) * 0.25 && m.dateMillis < x1 - (x1 - x0) * 0.25) {
                    text(Copy.dayMonth(m.dateMillis, zone), xs(m.dateMillis), baseline, Align.MIDDLE)
                }
            }
        }
    }
}

private enum class Align { START, MIDDLE, END }

private const val W = 360f
private const val H = 210f
private const val L = 40f
private const val R = 16f
private const val T = 12f
private const val B = 30f
