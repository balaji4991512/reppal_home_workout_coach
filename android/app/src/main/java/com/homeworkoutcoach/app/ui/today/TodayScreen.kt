package com.homeworkoutcoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.habits.HabitRow
import com.homeworkoutcoach.app.ui.planner.CarriedOver
import com.homeworkoutcoach.app.ui.planner.TaskRow
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.HabitKind

/** The home screen: today's workout, the routines for now, the habits due and the tasks. Nothing is flagged red. */
@Composable
fun TodayScreen(
    onStartWorkout: () -> Unit,
    onSetup: () -> Unit,
    onRunRoutine: (String) -> Unit,
    onRoutines: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onHabits: () -> Unit,
    onAddHabit: () -> Unit,
    onOpenTask: (String) -> Unit,
    onPlanner: () -> Unit,
    vm: TodayViewModel = viewModel(factory = Factories.Today),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    // The habit whose amount box is open.
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Page {
        Small(s.date)
        H1(s.greeting, Modifier.padding(top = 4.dp))
        Muted(s.summary, Modifier.padding(top = 4.dp))

        Section("Workout", null, {})
        WorkoutCard(s.workout, onStartWorkout, onSetup)

        if (s.routines.isNotEmpty()) {
            Section("Routines", "All routines", onRoutines)
            Hairline()
            s.routines.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f).clickable(enabled = r.canRun, role = Role.Button) { onRunRoutine(r.id) }.padding(vertical = 10.dp)) {
                        Body(r.name, strong = true)
                        Small(if (r.canRun) r.steps else "Add steps to run it")
                    }
                    if (r.doneToday) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = c.ok)
                            Text("Done", color = c.ok, style = AppTheme.type.label)
                        }
                    } else if (r.canRun) {
                        Chip("Start", false, { onRunRoutine(r.id) }, small = true)
                    }
                }
                Hairline()
            }
        }

        if (s.habits.isNotEmpty()) {
            Section("Habits", "All habits", onHabits)
            Hairline()
            s.habits.forEach { row ->
                HabitRow(
                    row = row,
                    dueToday = true,
                    expanded = open == row.id,
                    onBadge = {
                        if (row.kind == HabitKind.DONE) vm.recordHabit(row, if (row.done) 0 else 1)
                        else open = if (open == row.id) null else row.id
                    },
                    onOpen = { onOpenHabit(row.id) },
                    onSet = { vm.recordHabit(row, it) },
                )
            }
        } else if (!s.hasHabits) {
            Section("Habits", null, {})
            Muted("Track the things you want to do every day or every week.")
            TextLink("Add a habit", onAddHabit)
        }

        Section("Tasks", "Planner", onPlanner)
        if (s.tasks.tasks.isEmpty() && s.tasks.carriedOver.isEmpty()) {
            Muted("Nothing planned for today.")
            TextLink("Add a task", onPlanner)
        } else {
            Hairline()
            s.tasks.tasks.forEach { row ->
                TaskRow(row, onTick = { vm.toggleTask(row.id) }, onOpen = { onOpenTask(row.id) }, onTomorrow = { vm.moveToTomorrow(row.id) })
                Hairline()
            }
            CarriedOver(
                s.tasks.carriedOver,
                onTick = vm::toggleTask, onOpen = onOpenTask, onToday = vm::moveToToday, onTomorrow = vm::moveToTomorrow, onDelete = vm::deleteTask,
            )
        }
    }
}

/** A section heading with an optional link to the full screen. */
@Composable
private fun Section(title: String, link: String?, onLink: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        H2(title, Modifier.weight(1f), top = 0.dp)
        if (link != null) TextLink(link, onLink)
    }
}

@Composable
private fun WorkoutCard(card: WorkoutCardUi, onStart: () -> Unit, onSetup: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.soft).padding(16.dp)) {
        when (card) {
            WorkoutCardUi.NoPlan -> {
                Body("No workout plan yet", strong = true)
                Small("Tell me what equipment you have and I will plan your week.")
                PrimaryButton("Set up my plan", onSetup)
            }
            is WorkoutCardUi.Next -> {
                Body(card.title, strong = true)
                Small(card.detail)
                Small(card.week)
                if (card.canStart) PrimaryButton("Start workout", onStart) else Small("Nothing in your equipment covers this day.", Modifier.padding(top = 8.dp))
            }
            is WorkoutCardUi.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.Check, null, Modifier.size(22.dp), tint = c.ok)
                Column {
                    Body("Workout done", strong = true)
                    Small(card.title)
                }
            }
            is WorkoutCardUi.Rest -> {
                Body("Rest day", strong = true)
                Small("Next up: ${card.nextTitle} on ${card.nextDay}")
                if (card.canStart) TextLink("Start it anyway", onStart)
            }
        }
    }
}
