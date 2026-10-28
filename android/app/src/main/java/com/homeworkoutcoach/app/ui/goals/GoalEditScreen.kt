package com.homeworkoutcoach.app.ui.goals

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.EndPreset
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.Goals
import java.time.Instant
import java.time.ZoneOffset

/** Add or edit a goal. [id] is null for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    id: String?,
    onDone: () -> Unit,
    vm: GoalEditViewModel = viewModel(key = "goal-edit-${id ?: "new"}", factory = Factories.goalEdit(id)),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val d = s.draft
    val c = AppTheme.colors
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val weekEnd = Goals.endFor(EndPreset.THIS_WEEK, s.today)
    val monthEnd = Goals.endFor(EndPreset.THIS_MONTH, s.today)
    val thirty = Goals.endFor(EndPreset.THIRTY_DAYS, s.today)

    Page {
        H1(if (id == null) "New goal" else "Edit goal")

        Label("What do you want to reach?")
        Chips {
            Chip("Workouts", d.kind == GoalKind.WORKOUTS, { vm.edit { it.copy(kind = GoalKind.WORKOUTS) } })
            Chip("A habit", d.kind == GoalKind.HABIT, { vm.edit { it.copy(kind = GoalKind.HABIT) } })
            Chip("Weight", d.kind == GoalKind.WEIGHT, { vm.edit { it.copy(kind = GoalKind.WEIGHT) } })
        }

        if (d.kind == GoalKind.HABIT) {
            Label("Which habit?")
            if (s.habits.isEmpty()) Muted("Add a habit first, then you can set a goal for it.")
            Chips { s.habits.forEach { h -> Chip(h.habit.name, d.habitId == h.habit.id, { vm.edit { it.copy(habitId = h.habit.id) } }, small = true) } }
        }

        Label(
            when (d.kind) {
                GoalKind.WORKOUTS -> "How many workouts?"
                GoalKind.HABIT -> "On how many days?"
                GoalKind.WEIGHT -> "Goal weight (kg)"
            },
        )
        Field(
            d.targetText, { v -> vm.edit { it.copy(targetText = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6)) } },
            if (d.kind == GoalKind.WEIGHT) "80" else "20",
            label = "Target",
            keyboardOptions = KeyboardOptions(keyboardType = if (d.kind == GoalKind.WEIGHT) KeyboardType.Decimal else KeyboardType.Number),
        )

        if (d.kind == GoalKind.WEIGHT) {
            if (d.startWeightKg != null || s.latestWeightKg != null) {
                val from = d.startWeightKg ?: s.latestWeightKg
                Small(
                    (if (d.startWeightKg != null) "Counting from " else "Counting from your latest weigh-in: ") + GoalFormat.number(from!!) + " kg",
                    Modifier.padding(top = 8.dp),
                )
            } else {
                Label("Your weight now (kg)")
                Field(
                    d.weightNowText, { v -> vm.edit { it.copy(weightNowText = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6)) } },
                    "84.5", label = "Your weight now",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }

        Label("Name")
        Field(
            d.effectiveTitle(s.habitName), { v -> vm.edit { it.copy(title = v, titleEdited = true) } }, "For example, Finish 20 workouts",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Label("By when?")
        Chips {
            Chip("This week", d.endDate == weekEnd, { vm.edit { it.copy(endDate = weekEnd) } })
            Chip("This month", d.endDate == monthEnd, { vm.edit { it.copy(endDate = monthEnd) } })
            Chip("30 days", d.endDate == thirty, { vm.edit { it.copy(endDate = thirty) } })
            val other = d.endDate != weekEnd && d.endDate != monthEnd && d.endDate != thirty
            Chip(if (other) PlannerFormat.dayShort(d.endDate, s.today) else "Pick a day", other, { pickDate = true })
        }
        Small("Ends ${PlannerFormat.dayLong(d.endDate)}.")

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton("Save", { vm.save(onDone) })
        FormButtons(onDone)
        if (id != null) DeleteAction("Delete goal", "Delete this goal? This can’t be undone.", { vm.delete(onDone) })
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = d.endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.edit { it.copy(endDate = picked) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))
