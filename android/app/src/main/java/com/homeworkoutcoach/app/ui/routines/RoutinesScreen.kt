package com.homeworkoutcoach.app.ui.routines

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Pill
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** The Routines list: run one, edit one, add one. */
@Composable
fun RoutinesScreen(
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    vm: RoutinesViewModel = viewModel(factory = Factories.RoutineList),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    Page {
        H1("Routines")
        Muted(s.summary)
        if (s.routines.isNotEmpty()) {
            Hairline(Modifier.padding(top = 16.dp))
            s.routines.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier.weight(1f).clickable(enabled = r.canRun, role = Role.Button) { onRun(r.id) }.padding(vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Body(r.name, strong = true)
                            if (r.isNow && !r.doneToday) Pill("Now", c.accs, c.fg)
                        }
                        Small(if (r.canRun) "${r.part} · ${r.steps}" else "${r.part} · add steps to run it")
                    }
                    if (r.doneToday) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = c.ok)
                            Text("Done", color = c.ok, style = AppTheme.type.label)
                        }
                    } else if (r.canRun) {
                        Chip("Start", false, { onRun(r.id) }, small = true)
                    }
                    Chip("Edit", false, { onEdit(r.id) }, small = true)
                }
                Hairline()
            }
        }
        PrimaryButton("Add routine", onAdd)
    }
}

/** A routine to run through: tick the steps in any order, then finish. */
@Composable
fun RoutineRunScreen(
    id: String,
    onClose: () -> Unit,
    vm: RoutineRunViewModel = viewModel(key = "routine-run-$id", factory = Factories.routineRun(id)),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    Page {
        Row(
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClose),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.ChevronLeft, null, Modifier.size(20.dp), tint = c.mut)
            Small("Routines")
        }
        H1(s.name)
        Muted("${s.part} · ${s.progress} done")
        Hairline(Modifier.padding(top = 16.dp))
        s.steps.forEach { step ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Checkbox) { vm.toggle(step.id) }
                    .semantics { contentDescription = if (step.ticked) "Untick ${step.title}" else "Tick ${step.title}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(28.dp)) {
                        if (step.ticked) drawCircle(c.fg) else drawCircle(c.mut, style = Stroke(2.dp.toPx()), radius = size.width / 2 - 1.dp.toPx())
                    }
                    if (step.ticked) Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = c.on)
                }
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        step.title,
                        color = if (step.ticked) c.mut else c.fg,
                        style = AppTheme.type.body.copy(textDecoration = if (step.ticked) TextDecoration.LineThrough else TextDecoration.None),
                    )
                    step.habitName?.let { Small("Also ticks ${it}") }
                }
            }
            Hairline()
        }

        if (s.doneToday) {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Check, null, Modifier.size(22.dp), tint = c.ok)
                Text("Done today", color = c.ok, style = AppTheme.type.bodyStrong)
            }
            TextLink("Mark as not done", vm::undoFinish)
        } else {
            PrimaryButton("Finish", { if (s.canFinish) vm.finish(onClose) }, alt = !s.canFinish)
            if (!s.canFinish) Small("Tick every step to finish.", Modifier.padding(top = 8.dp))
        }
        TextLink("Close", onClose)
    }
}
