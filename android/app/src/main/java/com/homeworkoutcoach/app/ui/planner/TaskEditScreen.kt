package com.homeworkoutcoach.app.ui.planner

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

/** Edit one task: its text, note, day and time, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    id: String,
    onDone: () -> Unit,
    vm: TaskEditViewModel = viewModel(key = "task-$id", factory = Factories.taskEdit(id)),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val c = AppTheme.colors
    val today = LocalDate.now()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Page {
        H1("Edit task")

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

        Label("Time")
        Chips {
            Chip("No time", s.time == null, { vm.edit { it.copy(time = null) } })
            Chip(s.time?.let(PlannerFormat::time) ?: "Set a time", s.time != null, { pickTime = true })
        }

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton("Save", { vm.save(onDone) })
        TextLink("Cancel", onDone)

        if (confirmDelete) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(12.dp)) {
                Text("Delete this task? This can’t be undone.", color = c.fg, style = AppTheme.type.bodyStrong)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { PrimaryButton("Keep it", { confirmDelete = false }, alt = true, compact = true) }
                    Box(Modifier.weight(1f)) { PrimaryButton("Delete", { vm.delete(onDone) }, compact = true) }
                }
            }
        } else {
            TextLink("Delete task", { confirmDelete = true })
        }
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

    if (pickTime) {
        val start = s.time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton({
                    vm.edit { it.copy(time = LocalTime.of(state.hour, state.minute)) }
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))
