package com.homeworkoutcoach.app.ui.body

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen weigh-in: a scale dial whose needle wanders while waiting, follows the live weight while measuring,
 * and rests on the saved reading.
 */
@Composable
fun WeighInDialog(
    state: WeighInUi,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onBluetoothSettings: () -> Unit,
) {
    if (state == WeighInUi.Idle) return
    val c = AppTheme.colors
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state is WeighInUi.Saved) {
        if (state is WeighInUi.Saved) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
            Icon(
                AppIcons.Close,
                contentDescription = "Close",
                tint = c.fg,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onClose).padding(11.dp),
            )
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val weight = when (state) {
                    is WeighInUi.Listening -> state.weightKg
                    is WeighInUi.Saved -> state.weightKg
                    else -> null
                }
                ScaleDial(
                    weightKg = weight,
                    settled = state is WeighInUi.Saved,
                    modifier = Modifier.fillMaxWidth(),
                )

                // The big number under the dial.
                Text(
                    weight?.let { BodyPresenter.weight(it) } ?: "—",
                    color = if (weight == null) c.bd else c.fg,
                    style = AppTheme.type.tabular(AppTheme.type.big),
                    modifier = Modifier.semantics { contentDescription = weight?.let { "${BodyPresenter.weight(it)} kilograms" } ?: "No reading yet" },
                )
                Text("kg", color = c.mut, style = AppTheme.type.body.copy(fontSize = 17.sp))
                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = state,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "weigh-in message",
                ) { s ->
                    Column(
                        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (s) {
                            is WeighInUi.Listening -> if (s.weightKg == null) {
                                Message("Step on the scale", "Waiting for your scale. Tap it with your foot to wake it.")
                                Dots()
                            } else {
                                Message("Measuring…", "Please stay still on the scale. It saves by itself when the reading settles.")
                                Dots()
                            }
                            is WeighInUi.Saved -> {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(c.oks), contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Check, null, Modifier.size(24.dp), tint = c.ok)
                                }
                                Message("Saved", "You can step off the scale.")
                                PrimaryButton("Done", onClose)
                                PrimaryButton("Undo", onUndo, alt = true)
                            }
                            is WeighInUi.Problem -> {
                                Message("No reading", s.message, bad = true)
                                if (s.bluetoothOff) PrimaryButton("Open Bluetooth settings", onBluetoothSettings, alt = true)
                                PrimaryButton("Try again", onRetry)
                            }
                            WeighInUi.Idle -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, text: String, bad: Boolean = false) {
    val c = AppTheme.colors
    Text(title, color = if (bad) c.bad else c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp), textAlign = TextAlign.Center)
    Text(
        text,
        Modifier.padding(top = 6.dp),
        color = c.mut,
        style = AppTheme.type.body,
        textAlign = TextAlign.Center,
    )
}

/** Three dots that pulse in turn while waiting or measuring. */
@Composable
private fun Dots() {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200)), label = "phase")
    Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.fg else c.bd))
        }
    }
}

/**
 * A bathroom-scale dial: ticks every 5 kg over the top half circle, a filled arc up to the needle, and the needle.
 * Waiting: the needle wanders near zero. Measuring: it springs to the live weight. Saved: it rests there.
 */
@Composable
fun ScaleDial(weightKg: Double?, settled: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val maxKg = if (weightKg != null && weightKg > 140) 200f else 150f
    val wander = rememberInfiniteTransition(label = "wander").animateFloat(
        initialValue = 2f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wander",
    )
    val needle = remember { Animatable(0f) }
    LaunchedEffect(weightKg, settled) {
        if (weightKg != null) {
            needle.animateTo(
                weightKg.toFloat().coerceIn(0f, maxKg),
                spring(dampingRatio = if (settled) Spring.DampingRatioLowBouncy else 0.55f, stiffness = Spring.StiffnessLow),
            )
        }
    }
    val kg = if (weightKg == null) wander.value else needle.value

    Canvas(modifier.aspectRatio(2f).padding(horizontal = 8.dp)) {
        val stroke = 10.dp.toPx()
        val r = size.width / 2 - stroke
        val center = Offset(size.width / 2, size.height - 6.dp.toPx())
        val arcTopLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)
        fun angle(v: Float) = Math.toRadians((180.0 + 180.0 * (v / maxKg))).toFloat()

        // Track and progress.
        drawArc(c.bd, 180f, 180f, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(c.fg, 180f, 180f * (kg / maxKg), false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

        // Ticks inside the arc: long every 50 kg, medium every 10, short every 5.
        var v = 0
        while (v <= maxKg) {
            val a = angle(v.toFloat())
            val len = when {
                v % 50 == 0 -> 16.dp.toPx()
                v % 10 == 0 -> 10.dp.toPx()
                else -> 5.dp.toPx()
            }
            val outer = r - stroke
            val p1 = Offset(center.x + outer * cos(a), center.y + outer * sin(a))
            val p2 = Offset(center.x + (outer - len) * cos(a), center.y + (outer - len) * sin(a))
            drawLine(if (v <= kg) c.fg else c.mut.copy(alpha = 0.5f), p1, p2, strokeWidth = if (v % 10 == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            v += 5
        }

        // Needle and hub.
        val a = angle(kg)
        val tip = Offset(center.x + (r - stroke - 22.dp.toPx()) * cos(a), center.y + (r - stroke - 22.dp.toPx()) * sin(a))
        drawLine(if (settled) c.ok else c.fg, center, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(if (settled) c.ok else c.fg, radius = 9.dp.toPx(), center = center)
        drawCircle(c.bg, radius = 3.5.dp.toPx(), center = center)
    }
}
