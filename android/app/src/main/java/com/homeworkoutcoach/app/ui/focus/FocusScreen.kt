package com.homeworkoutcoach.app.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
            Muted(running.title ?: "A focus session", Modifier.padding(top = 4.dp))
            Text(
                Focus.format(remaining), Modifier.fillMaxWidth().padding(top = 48.dp), color = c.fg,
                style = AppTheme.type.tabular(AppTheme.type.big), textAlign = TextAlign.Center,
            )
            Small("of ${running.plannedText}", Modifier.fillMaxWidth().padding(top = 8.dp), color = c.mut)
            Box(Modifier.padding(top = 32.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(((total - remaining).toFloat() / total).coerceIn(0f, 1f)).clip(RoundedCornerShape(2.dp)).background(c.fg))
            }
            PrimaryButton("End session", vm::stop, alt = true, modifier = Modifier.padding(top = 24.dp))
            Small("The time you have spent so far is kept.", Modifier.padding(top = 8.dp))
        } else {
            finished?.let { f ->
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft).padding(16.dp)) {
                    Body(if (f.completed) "Session finished" else "Session ended", strong = true)
                    Small(
                        listOfNotNull(f.taskTitle, "${f.actualMinutes} ${if (f.actualMinutes == 1) "minute" else "minutes"}")
                            .joinToString(" · "),
                    )
                    Chip("Done", false, vm::dismissFinished, Modifier.padding(top = 10.dp), small = true)
                }
            }
            Muted(s.today, Modifier.padding(top = 8.dp))
            Small(s.week)

            Label("How long?")
            Chips {
                s.presets.forEach { m -> Chip("$m min", !custom && minutes == m, { custom = false; minutes = m; problem = null }) }
                Chip("Other", custom, { custom = true; problem = null })
            }
            if (custom) {
                Field(
                    customText, { customText = it.filter(Char::isDigit).take(3); problem = null }, "Minutes, 1 to 180",
                    label = "Minutes", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (s.taskChoices.isNotEmpty()) {
                Label("Working on (optional)")
                Chips { s.taskChoices.forEach { t -> Chip(t, task == t, { task = if (task == t) null else t }, small = true) } }
            }
            problem?.let { Text(it, Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.small) }
            PrimaryButton("Start", ::startTimer)

            if (s.recent.isNotEmpty()) {
                H2("Recent", top = 32.dp)
                Hairline(Modifier.padding(top = 4.dp))
                s.recent.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Body(r.lengthText, strong = true)
                            Small(listOfNotNull(r.whenText, r.note).joinToString(" · "))
                        }
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 24.dp, bottom = 6.dp))
