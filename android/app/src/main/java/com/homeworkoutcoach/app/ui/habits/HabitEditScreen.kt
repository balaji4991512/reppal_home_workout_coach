package com.homeworkoutcoach.app.ui.habits

import com.homeworkoutcoach.app.ui.components.DeleteAction
import com.homeworkoutcoach.app.ui.components.FormButtons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.HabitKind
import java.time.DayOfWeek

/**
 * Add or edit a habit. [id] is null for a new one. [onDone] closes the form; [onGone] runs after the habit is archived
 * or deleted, so the caller can leave the habit's own page too.
 */
@Composable
fun HabitEditScreen(
    id: String?,
    onDone: () -> Unit,
    onGone: () -> Unit,
    vm: HabitEditViewModel = viewModel(key = "habit-edit-${id ?: "new"}", factory = Factories.habitEdit(id)),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val d = s.draft
    val c = AppTheme.colors

    Page {
        H1(if (id == null) "New habit" else "Edit habit")

        if (s.suggestions.isNotEmpty()) {
            Label("Start from a suggestion")
            Chips { s.suggestions.forEach { Chip(it.name, d.name == it.name, { vm.pick(it) }, small = true) } }
        }

        Label("Name")
        Field(d.name, { v -> vm.edit { it.copy(name = v) } }, "For example, Drink water")

        Label("Icon")
        IconPicker(d.icon, d.color) { v -> vm.edit { it.copy(icon = v, color = HabitStyles.colorFor(v)) } }

        Label("How do you track it?")
        Chips {
            Chip("Done or not", d.kind == HabitKind.DONE, { vm.edit { it.withKind(HabitKind.DONE) } })
            Chip("A number", d.kind == HabitKind.COUNT, { vm.edit { it.withKind(HabitKind.COUNT) } })
            Chip("Minutes", d.kind == HabitKind.TIME, { vm.edit { it.withKind(HabitKind.TIME) } })
        }
        if (d.kind != HabitKind.DONE) {
            Label(HabitFormat.targetLabel(d.kind))
            Field(
                d.targetText,
                { v -> vm.edit { it.copy(targetText = v.filter(Char::isDigit).take(4)) } },
                if (d.kind == HabitKind.COUNT) "8" else "20",
                label = HabitFormat.targetLabel(d.kind),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            d.target?.takeIf { d.kind == HabitKind.TIME && it >= 60 }?.let { Small(HabitFormat.minutes(it), Modifier.padding(top = 4.dp)) }
        }

        Label("When?")
        Chips {
            Chip("Every day", d.scheduleType == ScheduleType.DAILY, { vm.edit { it.copy(scheduleType = ScheduleType.DAILY) } })
            Chip("Certain days", d.scheduleType == ScheduleType.WEEKDAYS, { vm.edit { it.copy(scheduleType = ScheduleType.WEEKDAYS) } })
            Chip("Times a week", d.scheduleType == ScheduleType.TIMES, { vm.edit { it.copy(scheduleType = ScheduleType.TIMES) } })
        }
        when (d.scheduleType) {
            ScheduleType.WEEKDAYS -> Chips {
                DayOfWeek.entries.forEach { day ->
                    Chip(
                        HabitFormat.dayShort(day), day in d.weekdays,
                        { vm.edit { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) } },
                        small = true,
                    )
                }
            }
            ScheduleType.TIMES -> {
                Chips {
                    (1..7).forEach { n -> Chip("$n", d.times == n, { vm.edit { it.copy(times = n) } }, small = true) }
                }
                Small("On any days; it stays on your list until the week has that many.")
            }
            ScheduleType.DAILY -> Unit
        }

        if (d.auto == AutoSource.WORKOUT) Muted("This one ticks itself on days you finish a workout. You can still change it by hand.")

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton("Save", { vm.save(onDone) })
        if (id == null) {
            FormButtons(onDone)
        } else {
            // Cancel and Archive share a row; Delete sits well apart so it is not hit by accident.
            FormButtons(
                onDone,
                extraLabel = if (d.archived) "Bring back" else "Archive",
                onExtra = { vm.setArchived(!d.archived, onGone) },
            )
            if (!d.archived) Small("Archive hides a habit and keeps its history.", Modifier.padding(top = 8.dp))
            DeleteAction("Delete habit", "Delete ${d.name.ifBlank { "this habit" }} and all its history? This can’t be undone.", { vm.delete(onGone) })
        }
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, color: String, onPick: (String) -> Unit) {
    val c = AppTheme.colors
    val tint = HabitLook.color(color)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitStyles.icons.forEach { key ->
            val on = key == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.size(48.dp).clip(shape)
                    .background(if (on) c.accs else c.bg)
                    .border(if (on) BorderStroke(2.dp, tint) else BorderStroke(1.dp, c.bd), shape)
                    .clickable(role = Role.RadioButton) { onPick(key) }
                    .semantics { this.selected = on; contentDescription = "Icon $key" },
                contentAlignment = Alignment.Center,
            ) { Text(HabitLook.emoji(key), fontSize = 22.sp) }
        }
    }
}

