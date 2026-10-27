package com.homeworkoutcoach.app.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.habits.MonthButton
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** The daily planner: one day's tasks, a line to add more, and what was left over from earlier days. */
@Composable
fun PlannerScreen(
    onOpen: (String) -> Unit,
    vm: PlannerViewModel = viewModel(factory = Factories.Planner),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    // The carried-over task whose Delete was tapped once; a second tap deletes it.
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (text.isBlank()) return
        problem = vm.add(text)
        if (problem == null) text = ""
    }

    Page {
        H1("Planner")

        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthButton(AppIcons.ChevronLeft, "Previous day", true, vm::previousDay)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.title, color = c.fg, style = AppTheme.type.h2, textAlign = TextAlign.Center)
                Small(s.subtitle)
            }
            MonthButton(AppIcons.Forward, "Next day", true, vm::nextDay)
        }
        if (!s.isToday) TextLink("Back to today", vm::goToToday, Modifier.align(Alignment.CenterHorizontally))
        Muted(s.summary, Modifier.padding(top = 4.dp))

        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                text,
                { text = it; problem = null },
                "Add a task",
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.acc)
                    .clickable(role = Role.Button) { submit() }
                    .semantics { contentDescription = "Add task" },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, null, Modifier.size(24.dp), tint = c.on) }
        }
        problem?.let { Text(it, Modifier.padding(top = 8.dp), color = c.bad, style = AppTheme.type.small) }

        if (s.tasks.isNotEmpty()) {
            Hairline(Modifier.padding(top = 14.dp))
            s.tasks.forEach { row ->
                TaskRow(row, onTick = { vm.toggle(row.id) }, onOpen = { onOpen(row.id) }, onTomorrow = { vm.moveToTomorrow(row.id) })
                Hairline()
            }
        }

        if (s.carriedOver.isNotEmpty()) {
            H2("Carried over", top = 28.dp)
            Small("Left from earlier days. Nothing is lost; move it, finish it or delete it.", Modifier.padding(bottom = 6.dp))
            Hairline()
            s.carriedOver.forEach { row ->
                TaskRow(row, onTick = { vm.toggle(row.id) }, onOpen = { onOpen(row.id) }, onTomorrow = null) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Move to today", false, { vm.moveToToday(row.id) }, small = true)
                        Chip("Move to tomorrow", false, { vm.moveToTomorrow(row.id) }, small = true)
                        Chip(
                            if (confirm == row.id) "Sure? Delete" else "Delete", confirm == row.id,
                            { if (confirm == row.id) { confirm = null; vm.delete(row.id) } else confirm = row.id },
                            small = true,
                        )
                    }
                }
                Hairline()
            }
        }
    }
}

/**
 * A task: tick, title with its time and note, and "Tomorrow" for one with [onTomorrow]. [below] goes under the row
 * (the carried-over actions).
 */
@Composable
private fun TaskRow(
    row: TaskRowUi,
    onTick: () -> Unit,
    onOpen: () -> Unit,
    onTomorrow: (() -> Unit)?,
    below: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val sub = listOfNotNull(row.dayLabel?.let { "From ${it.replaceFirstChar(Char::lowercase)}" }, row.time, row.note.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onTick)
                    .semantics { contentDescription = if (row.done) "Mark ${row.title} not done" else "Mark ${row.title} done" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) {
                    if (row.done) drawCircle(c.fg) else drawCircle(c.mut, style = Stroke(2.dp.toPx()), radius = size.width / 2 - 1.dp.toPx())
                }
                if (row.done) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = c.on)
            }
            Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp)) {
                Text(
                    row.title,
                    color = if (row.done) c.mut else c.fg,
                    style = AppTheme.type.body.copy(textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None),
                )
                if (sub.isNotEmpty()) Text(sub, color = c.mut, style = AppTheme.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onTomorrow != null && !row.done) Chip("Tomorrow", false, onTomorrow, Modifier.padding(start = 8.dp), small = true)
        }
        if (below != null && !row.done) Box(Modifier.padding(start = 48.dp)) { below() }
    }
}
