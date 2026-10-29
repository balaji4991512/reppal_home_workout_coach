package com.homeworkoutcoach.app.ui.planner

import com.homeworkoutcoach.app.ui.components.DeleteAction
import com.homeworkoutcoach.app.ui.components.FormButtons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Edit one task: its text, note, day, start and end, or delete it. */
@Composable
fun TaskEditScreen(
    id: String,
    onDone: () -> Unit,
    vm: TaskEditViewModel = viewModel(key = "task-$id", factory = Factories.taskEdit(id)),
) = TaskForm(isNew = false, vm = vm, onDone = onDone)

/** The form for a new task on [day], with a suggested start from the timeline if there is one. */
@Composable
fun TaskNewScreen(
    day: LocalDate,
    startMinutes: Int?,
    onDone: () -> Unit,
    vm: TaskEditViewModel = viewModel(key = "task-new-$day-$startMinutes", factory = Factories.taskNew(day, startMinutes)),
) = TaskForm(isNew = true, vm = vm, onDone = onDone)

private enum class Picking { NONE, START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskForm(isNew: Boolean, vm: TaskEditViewModel, onDone: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val c = AppTheme.colors
    val today = LocalDate.now()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(Picking.NONE) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android 13 and up ask before notifications show; the time is kept either way.
    val askForNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    Page {
        H1(if (isNew) "New task" else "Edit task")

        Label("What do you need to do?")
        Field(
            s.title, { v -> vm.edit { it.copy(title = v) } }, "For example, Call the bank",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Label("Note")
        Field(
            s.note, { v -> vm.edit { it.copy(note = v) } }, "Anything to remember (optional)", minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Label("Day")
        Chips {
            Chip("Today", s.date == today, { vm.edit { it.copy(date = today) } })
            Chip("Tomorrow", s.date == today.plusDays(1), { vm.edit { it.copy(date = today.plusDays(1)) } })
            val other = s.date != today && s.date != today.plusDays(1)
            Chip(if (other) PlannerFormat.dayShort(s.date, today) else "Pick a day", other, { pickDate = true })
        }

        Label("Starts")
        Chips {
            Chip("No start", s.time == null, { vm.edit { it.copy(time = null) } })
            Chip(s.time?.let(PlannerFormat::time) ?: "Set a start", s.time != null, { picking = Picking.START })
        }

        Label("Ends")
        Chips {
            Chip("No end", s.endTime == null, { vm.edit { it.copy(endTime = null) } })
            Chip(s.endTime?.let(PlannerFormat::time) ?: "Set an end", s.endTime != null, { picking = Picking.END })
        }
        if (s.time != null || s.endTime != null) {
            Small(if (s.time != null) "You get a reminder when it is time to start." else "You get a reminder at the deadline.", Modifier.padding(top = 8.dp))
        }
        if (s.time != null) {
            Chips {
                listOf(30 to "30 min", 60 to "1 hour", 120 to "2 hours").forEach { (minutes, label) ->
                    val end = s.time.plusMinutes(minutes.toLong())
                    // not past midnight: a block stays within its day
                    if (end > s.time) Chip(label, s.endTime == end, { vm.edit { it.copy(endTime = end) } }, small = true)
                }
            }
        }
        Small(
            if (s.time == null) "An end on its own is a deadline: finish by then."
            else "A start and an end make a block, like deep work from 3 to 5 pm.",
            Modifier.padding(top = 4.dp),
        )
        if (s.time != null && s.endTime != null && s.endTime <= s.time) {
            Text(s.problem.orEmpty(), Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.small)
        }

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton(if (isNew) "Add task" else "Save", { vm.save(onDone) })
        FormButtons(onDone)
        if (!isNew) DeleteAction("Delete task", "Delete this task? This can’t be undone.", { vm.delete(onDone) })
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.edit { it.copy(date = picked) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (picking != Picking.NONE) {
        val ending = picking == Picking.END
        val start = (if (ending) s.endTime ?: s.time?.plusHours(1)?.takeIf { it > s.time } ?: LocalTime.of(17, 0) else s.time) ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = Picking.NONE },
            confirmButton = {
                TextButton({
                    val chosen = LocalTime.of(state.hour, state.minute)
                    vm.edit { if (ending) it.copy(endTime = chosen) else it.copy(time = chosen) }
                    picking = Picking.NONE
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = Picking.NONE }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))
