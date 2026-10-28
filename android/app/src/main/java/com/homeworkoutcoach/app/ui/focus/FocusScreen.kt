package com.homeworkoutcoach.app.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.theme.AppIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.focus.FocusNotifications
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Focus
import com.homeworkoutcoach.engine.FocusRun

/** The focus timer: choose a length (and a task), start, and watch it count down. */
@Composable
fun FocusScreen(vm: FocusViewModel = viewModel(factory = Factories.Focus)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    val context = LocalContext.current

    // What is being set up for the next session.
    var minutes by rememberSaveable { mutableStateOf(25) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var task by rememberSaveable { mutableStateOf<String?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    fun begin() = vm.start(minutes, task)
    // Android 13 and up asks before a notification can show. The timer starts either way.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }
    fun startTimer() {
        val length = if (custom) customText.trim().toIntOrNull() else minutes
        problem = Focus.problem(length)
        if (problem != null) return
        minutes = length!!
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else begin()
    }

    // Tell the alarm whether the screen is being looked at, so it knows whether a notification is needed.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> FocusNotifications.screenVisible = true
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> FocusNotifications.screenVisible = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        FocusNotifications.screenVisible = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        onDispose {
            lifecycle.removeObserver(observer)
            FocusNotifications.screenVisible = false
        }
    }

    val running = s.running
    // Keep the screen on while counting down.
    val view = LocalView.current
    DisposableEffect(running != null) {
        view.keepScreenOn = running != null
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = LocalHapticFeedback.current
    // The countdown only runs (and only settles the session) while the screen is on show. In the background the alarm
    // does it, so it can tell the user; settling here would cancel the alarm.
    LaunchedEffect(running?.startMillis, lifecycle) {
        val r = running ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                if (now >= r.endMillis) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.settle(r.startMillis)
                    break
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    Page {
        H1("Focus")
        if (running != null) {
            val remaining = (running.endMillis - now).coerceAtLeast(0)
            val total = running.endMillis - running.startMillis
            Hero(Modifier.padding(top = 16.dp)) {
                Pill(running.title ?: "A focus session")
                Dial(((total - remaining).toFloat() / total).coerceIn(0f, 1f), 260.dp, 16.dp, Modifier.padding(top = 20.dp)) {
                    Text(
                        Focus.format(remaining), color = c.fg, textAlign = TextAlign.Center,
                        style = AppTheme.type.tabular(AppTheme.type.big.copy(fontSize = 56.sp, lineHeight = 60.sp)),
                    )
                    Small("of ${running.plannedText}", color = c.mut)
                }
                SoftButton("End session", vm::stop, Modifier.padding(top = 24.dp))
                Small("The time you have spent so far is kept.", Modifier.padding(top = 10.dp), color = c.mut)
            }
        } else {
            finished?.let { f ->
                Row(
                    Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.tintMint).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Check, null, Modifier.size(24.dp), tint = c.bg)
                    }
                    Column(Modifier.weight(1f)) {
                        Body(if (f.completed) "Session finished" else "Session ended", strong = true)
                        Small(
                            listOfNotNull(f.taskTitle, "${f.actualMinutes} ${if (f.actualMinutes == 1) "minute" else "minutes"}")
                                .joinToString(" · "),
                            color = c.fg,
                        )
                    }
                    Chip("Done", false, vm::dismissFinished, small = true)
                }
            }

            Hero(Modifier.padding(top = 16.dp)) {
                val shown = if (custom) customText.toIntOrNull() ?: 0 else minutes
                val fill by animateFloatAsState((shown / 60f).coerceIn(0.08f, 1f), label = "dial")
                Dial(fill, 220.dp, 14.dp) {
                    Text(
                        if (shown > 0) "$shown" else "–", color = c.fg,
                        style = AppTheme.type.tabular(AppTheme.type.big.copy(fontSize = 64.sp, lineHeight = 68.sp)),
                    )
                    Small("minutes", color = c.mut)
                }
                DurationChips(
                    presets = s.presets, minutes = minutes, custom = custom,
                    onPreset = { custom = false; minutes = it; problem = null },
                    onOther = { custom = true; problem = null },
                    modifier = Modifier.padding(top = 20.dp),
                )
                if (custom) {
                    Field(
                        customText, { customText = it.filter(Char::isDigit).take(3); problem = null }, "Minutes, 1 to 180",
                        label = "Minutes", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (s.taskChoices.isNotEmpty()) {
                    Small("Working on (optional)", Modifier.padding(top = 16.dp, bottom = 4.dp), color = c.mut)
                    TaskChips(s.taskChoices, task) { task = if (task == it) null else it }
                }
                problem?.let { Text(it, Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.small) }
                AccentButton("Start", ::startTimer, Modifier.padding(top = 20.dp))
            }

            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("TODAY", s.todayValue, s.todayNote, c.tintMint, Modifier.weight(1f))
                StatCard("THIS WEEK", s.weekValue, null, c.tintPeach, Modifier.weight(1f))
            }

            if (s.recent.isNotEmpty()) {
                H2("Recent", top = 28.dp)
                val tints = listOf(c.tintMint, c.tintPeach, c.tintRose, c.accentSoft)
                s.recent.forEachIndexed { i, r ->
                    Row(
                        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(tints[i % tints.size]), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Timer, null, Modifier.size(20.dp), tint = c.fg)
                        }
                        Column(Modifier.weight(1f)) {
                            Body(r.lengthText, strong = true)
                            Small(listOfNotNull(r.whenText, r.note).joinToString(" · "))
                        }
                    }
                }
            }
        }
    }
}

/** A rounded card with a soft lavender-to-peach wash, holding the dial and its controls. */
@Composable
private fun Hero(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 1400f)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** A ring that fills from the top by [fraction], in a sweep from indigo to coral, with [content] in the middle. */
@Composable
private fun Dial(fraction: Float, size: Dp, stroke: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(c.bg.copy(alpha = 0.65f), 0f, 360f, false, topLeft, arc, style = Stroke(w))
            if (fraction > 0f) {
                val sweep = Brush.linearGradient(listOf(c.accent, c.coral), start = Offset.Zero, end = Offset(this.size.width, this.size.height))
                drawArc(sweep, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun Pill(text: String) {
    val c = AppTheme.colors
    Text(
        text, Modifier.clip(RoundedCornerShape(99.dp)).background(c.bg.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 6.dp),
        color = c.fg, style = AppTheme.type.small.copy(fontWeight = FontWeight.Medium),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationChips(presets: List<Int>, minutes: Int, custom: Boolean, onPreset: (Int) -> Unit, onOther: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { m -> ColorChip("$m min", !custom && minutes == m) { onPreset(m) } }
        ColorChip("Other", custom, onClick = onOther)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskChips(choices: List<String>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { t -> ColorChip(t, selected == t, small = true) { onPick(t) } }
    }
}

/** Selected is filled indigo; the rest are white on the wash. */
@Composable
private fun ColorChip(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(99.dp)
    Box(
        Modifier.heightIn(min = if (small) 38.dp else 44.dp).clip(shape)
            .background(if (selected) c.accent else c.bg.copy(alpha = 0.8f))
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) c.onAccent else c.fg,
            style = AppTheme.type.label.copy(fontSize = if (small) 14.sp else 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        )
    }
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(c.accent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Timer, null, Modifier.size(20.dp), tint = c.onAccent)
        Text(text, Modifier.padding(start = 10.dp), color = c.onAccent, style = AppTheme.type.bodyStrong)
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(c.bg.copy(alpha = 0.8f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.fg, style = AppTheme.type.bodyStrong) }
}

@Composable
private fun StatCard(label: String, value: String, note: String?, tint: Color, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(tint).padding(16.dp)) {
        Text(label, color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.06.em))
        Text(value, Modifier.padding(top = 4.dp), color = c.fg, style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)))
        Small(note ?: " ", color = c.mut)
    }
}
