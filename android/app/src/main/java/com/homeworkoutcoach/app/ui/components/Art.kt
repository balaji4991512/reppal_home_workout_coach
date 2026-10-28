package com.homeworkoutcoach.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.theme.AppTheme

/**
 * The app's flame in a soft lavender glow, for an empty page. It floats a little and the glow breathes, so a quiet
 * screen is not dead.
 */
@Composable
fun FlameArt(modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "flame")
    val float by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )
    val glow by t.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val flame = Path().apply {
        moveTo(54.75f, 20f); cubicTo(55.88f, 26f, 63.75f, 30.5f, 63.75f, 38.75f); cubicTo(63.75f, 43.62f, 59.62f, 47f, 54f, 47f)
        cubicTo(48.38f, 47f, 44.25f, 43.62f, 44.25f, 38.75f); cubicTo(44.25f, 34.62f, 46.88f, 31.62f, 48.75f, 29f)
        cubicTo(49.12f, 31.25f, 50.62f, 32.75f, 52.12f, 32.75f); cubicTo(52.88f, 29f, 52.12f, 24.12f, 54.75f, 20f); close()
    }
    Canvas(modifier.size(size).graphicsLayer { translationY = -6.dp.toPx() * float }) {
        val u = this.size.width / 108f
        drawCircle(c.accentSoft, radius = 50f * u, center = center)
        drawCircle(c.accent.copy(alpha = 0.10f * glow), radius = 44f * u * (0.9f + 0.1f * glow), center = center)
        drawCircle(c.accent.copy(alpha = 0.14f * glow), radius = 30f * u, center = center)
        withTransform({
            scale(u * 1.9f, u * 1.9f, Offset.Zero)
            translate(-25.6f, -8.4f)
        }) { drawPath(flame, c.accent) }
        // a short wick under the flame
        drawRect(c.accent, Offset(52.2f * u, 73.3f * u), androidx.compose.ui.geometry.Size(3.6f * u, 6.5f * u))
    }
}

/** Fades and lifts into place [delayMillis] after it first appears, so a list settles in one card at a time. */
@Composable
fun Modifier.fadeLiftIn(delayMillis: Int = 0): Modifier {
    val p = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 18.dp.toPx()
    }
}
