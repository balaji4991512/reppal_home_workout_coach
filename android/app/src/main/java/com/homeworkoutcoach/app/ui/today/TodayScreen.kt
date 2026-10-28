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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.ProgressRing
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
    onJournal: () -> Unit,
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
        if (s.total > 0) ProgressCard(s.done, s.total) else Muted(s.summary, Modifier.padding(top = 4.dp))

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

        Section("Journal", null, {})
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onJournal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (s.journalWritten) {
                Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = c.ok)
                Body("Written today", Modifier.weight(1f))
                Small("Edit")
            } else {
                Body("Write two lines about today", Modifier.weight(1f))
                Icon(AppIcons.Forward, null, Modifier.size(20.dp), tint = c.mut)
            }
        }
    }
}

/** How far today is: a ring with the count, the percentage, and a gentle line. */
@Composable
private fun ProgressCard(done: Int, total: Int) {
    val c = AppTheme.colors
    val percent = done * 100 / total
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgressRing(done.toFloat() / total) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)) { append("$done") }
                        withStyle(SpanStyle(color = c.mut, fontSize = 15.sp)) { append("/$total") }
                    },
                )
                Text("DONE", color = c.mut, style = AppTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.08.em))
            }
        }
        Column(Modifier.weight(1f)) {
            if (done == total) {
                Text("All done.", color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp))
                Small("Nothing left for today.", Modifier.padding(top = 2.dp))
            } else {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.accent)) { append("$percent%") }
                        append(" there.")
                    },
                    color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp),
                )
                Small("${total - done} more to go today.", Modifier.padding(top = 2.dp))
            }
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
