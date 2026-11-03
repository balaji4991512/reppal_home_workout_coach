package com.homeworkoutcoach.app.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Figure
import com.homeworkoutcoach.engine.Motion
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.Part
import com.homeworkoutcoach.engine.Shape

/** The colours the figure and the muscle map share. */
private class FigureColors(val body: Color, val far: Color, val main: Color, val also: Color, val rest: Color, val held: Color, val prop: Color, val floor: Color)

@Composable
private fun figureColors(): FigureColors {
    val c = AppTheme.colors
    return FigureColors(
        body = lerp(c.bg, c.accent, 0.42f),
        far = lerp(c.bg, c.accent, 0.2f),
        main = c.coral,
        also = lerp(c.bg, c.coral, 0.45f),
        rest = lerp(c.bg, c.accent, 0.16f),
        held = c.fg,
        prop = lerp(c.bg, c.mut, 0.45f),
        floor = c.bd,
    )
}

/**
 * The exercise's figure moving in a loop, with the worked muscles lit: [primary] strongly, [secondary] lightly. It stands
 * still when [playing] is false. The whole loop is fitted to the space, so lying moves are drawn as large as standing ones.
 */
@Composable
fun ExerciseAnimation(motion: Motion, primary: Set<Muscle>, secondary: Set<Muscle>, playing: Boolean, modifier: Modifier = Modifier) {
    val colors = figureColors()
    val worked = primary + secondary
    val box = remember(motion) { Figure.bounds(motion, worked) }
    var t by remember(motion) { mutableFloatStateOf(0f) }
    LaunchedEffect(motion, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (t + (now - last) / 1e9f / motion.secondsPerRep) % 1f
            last = now
        }
    }
    Canvas(modifier) {
        val pad = 12f
        val w = box.maxX - box.minX
        val h = box.maxY - box.minY
        val scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val ox = (size.width - w * scale) / 2 - box.minX * scale
        val oy = (size.height + h * scale) / 2 + box.minY * scale
        fun px(x: Float, y: Float) = Offset(ox + x * scale, oy - y * scale)

        drawLine(colors.floor, px(box.minX - 20, 0f).copy(x = 0f), px(box.maxX, 0f).copy(x = size.width), strokeWidth = 2f)
        for (s in Figure.shapes(motion, motion.poseAt(t), worked)) {
            val color = when (val p = s.part) {
                Part.Far -> colors.far
                Part.Near, Part.Torso, Part.Head -> colors.body
                Part.Prop -> colors.prop
                Part.Dumbbell -> colors.held
                is Part.Worked -> if (p.muscle in primary) colors.main else colors.also
            }
            when (s) {
                is Shape.Line -> drawLine(color, px(s.a.x, s.a.y), px(s.b.x, s.b.y), strokeWidth = s.width * scale, cap = StrokeCap.Round)
                is Shape.Dot -> drawCircle(color, s.r * scale, px(s.c.x, s.c.y))
            }
        }
    }
}

/**
 * A front and a back outline of the body with each muscle group filled: [primary] strongly, [secondary] lightly, the rest
 * left plain. Drawn in a 210 by 200 box (front on the left, back on the right).
 */
@Composable
fun BodyMap(primary: Set<Muscle>, secondary: Set<Muscle>, modifier: Modifier = Modifier) {
    val colors = figureColors()
    Canvas(modifier) {
        val k = minOf(size.width / 210f, size.height / 200f)
        val left = (size.width - 210f * k) / 2
        fun tone(m: Muscle) = when (m) {
            in primary -> colors.main
            in secondary -> colors.also
            else -> null
        }
        drawFigure(left, k, colors.rest)
        drawFigure(left + 110f * k, k, colors.rest)
        // front
        muscles(left, k, front = true) { m -> tone(m) }
        // back
        muscles(left + 110f * k, k, front = false) { m -> tone(m) }
    }
}

private fun DrawScope.box(dx: Float, k: Float, color: Color, x1: Float, y1: Float, x2: Float, y2: Float, r: Float = 6f) =
    drawRoundRect(color, Offset(dx + x1 * k, y1 * k), Size((x2 - x1) * k, (y2 - y1) * k), CornerRadius(r * k, r * k))

/** A plain standing body in a 100 by 200 box at [dx]. */
private fun DrawScope.drawFigure(dx: Float, k: Float, color: Color) {
    drawCircle(color, 11f * k, Offset(dx + 50f * k, 15f * k))
    box(dx, k, color, 45f, 24f, 55f, 34f, 3f)
    box(dx, k, color, 29f, 32f, 71f, 102f, 12f)
    box(dx, k, color, 30f, 96f, 70f, 118f, 9f)
    for (x in listOf(16f, 73f)) {
        box(dx, k, color, x, 36f, x + 11f, 78f, 5.5f)
        box(dx, k, color, x - 1f, 76f, x + 10f, 112f, 5f)
    }
    for (x in listOf(31f, 51f)) {
        box(dx, k, color, x, 112f, x + 18f, 160f, 8f)
        box(dx, k, color, x + 2f, 158f, x + 16f, 196f, 6f)
    }
}

/** The muscle groups on a front or back body at [dx], each in the colour [tone] gives it (none for plain). */
private fun DrawScope.muscles(dx: Float, k: Float, front: Boolean, tone: (Muscle) -> Color?) {
    fun fill(m: Muscle, draw: (Color) -> Unit) = tone(m)?.let(draw)
    if (front) {
        fill(Muscle.SHOULDERS) { c -> for (x in listOf(15f, 70f)) drawOval(c, Offset(dx + x * k, 32f * k), Size(15f * k, 17f * k)) }
        fill(Muscle.CHEST) { c -> box(dx, k, c, 32f, 38f, 49f, 60f, 7f); box(dx, k, c, 51f, 38f, 68f, 60f, 7f) }
        fill(Muscle.BICEPS) { c -> for (x in listOf(17f, 74f)) box(dx, k, c, x, 50f, x + 9f, 76f, 4.5f) }
        fill(Muscle.ABS) { c -> for (i in 0 until 3) box(dx, k, c, 41f, 63f + i * 12.5f, 59f, 73f + i * 12.5f, 3f) }
        fill(Muscle.QUADS) { c -> for (x in listOf(32f, 52f)) box(dx, k, c, x, 117f, x + 16f, 156f, 7f) }
    } else {
        fill(Muscle.REAR_DELTS) { c -> for (x in listOf(15f, 70f)) drawOval(c, Offset(dx + x * k, 32f * k), Size(15f * k, 17f * k)) }
        fill(Muscle.LATS) { c -> box(dx, k, c, 31f, 48f, 47f, 88f, 8f); box(dx, k, c, 53f, 48f, 69f, 88f, 8f) }
        fill(Muscle.TRICEPS) { c -> for (x in listOf(17f, 74f)) box(dx, k, c, x, 46f, x + 9f, 76f, 4.5f) }
        fill(Muscle.GLUTES) { c -> drawOval(c, Offset(dx + 31f * k, 98f * k), Size(18f * k, 22f * k)); drawOval(c, Offset(dx + 51f * k, 98f * k), Size(18f * k, 22f * k)) }
        fill(Muscle.HAMS) { c -> for (x in listOf(32f, 52f)) box(dx, k, c, x, 123f, x + 16f, 157f, 7f) }
    }
}
