package com.homeworkoutcoach.app.ui.routines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.CalendarStrip
import com.homeworkoutcoach.app.ui.components.StripDays
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import com.homeworkoutcoach.engine.Habits
import java.time.LocalDate
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
    val today = LocalDate.now()
    Page {
        CalendarStrip(
            date = s.date,
            days = StripDays.week(s.date, today, s.badges, lastSelectable = today),
            onSelect = vm::select, onPreviousWeek = vm::previousWeek, onNextWeek = vm::nextWeek,
            canGoNext = Habits.weekStart(s.date).plusWeeks(1) <= today,
            eyebrow = "Routines",
        )
        Column(Modifier.padding(top = 22.dp)) {
            Text(s.title, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp))
            Small(s.summary)
            if (!s.isToday) TextLink("Back to today", vm::goToToday)
            if (s.isFuture) Small("This day has not happened yet, so nothing can be marked.", Modifier.padding(top = 6.dp))
        }
        s.routines.forEachIndexed { i, r ->
            Row(
                Modifier.fadeLiftIn(i * 60).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.soft)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (r.doneToday) c.tintMint else c.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(if (r.doneToday) AppIcons.Check else AppIcons.Routine, null, Modifier.size(22.dp), tint = if (r.doneToday) c.ok else c.accent)
                }
                Column(
                    Modifier.weight(1f).clickable(enabled = s.isToday && r.canRun, role = Role.Button) { onRun(r.id) }.padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Body(r.name, strong = true)
                        if (r.isNow && !r.doneToday) Pill("Now", c.accentSoft, c.accent)
                    }
                    Small(if (r.canRun) "${r.part} · ${r.steps}" else "${r.part} · add steps to run it")
                }
                when {
                    s.isToday && r.doneToday -> DonePill()
                    s.isToday && r.canRun -> StartPill { onRun(r.id) }
                    // another day: done can be set or taken back; a day ahead has nothing to mark
                    !s.isToday && !s.isFuture -> {
                        if (r.doneToday) DonePill(Modifier.clickable(role = Role.Button) { vm.setDone(r.id, false) })
                        else Chip("Mark done", false, { vm.setDone(r.id, true) }, small = true)
                    }
                }
                Chip("Edit", false, { onEdit(r.id) }, small = true)
            }
        }
        PrimaryButton("Add routine", onAdd)
    }
}

@Composable
private fun DonePill(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).background(c.tintMint).heightIn(min = 36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = c.ok)
        Text("Done", color = c.ok, style = AppTheme.type.label)
    }
}

@Composable
private fun StartPill(onClick: () -> Unit) {
    val c = AppTheme.colors
    Text(
        "Start", color = c.onAccent, style = AppTheme.type.label,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.accent).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
