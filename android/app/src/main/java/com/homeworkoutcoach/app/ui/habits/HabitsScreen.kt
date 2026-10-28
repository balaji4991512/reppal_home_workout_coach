package com.homeworkoutcoach.app.ui.habits

import com.homeworkoutcoach.app.ui.components.Chip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.CalendarStrip
import com.homeworkoutcoach.app.ui.components.StripDays
import com.homeworkoutcoach.engine.Habits
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.HabitKind

/** The Habits list: tick what you did today, open a habit for its calendar, add new ones. */
@Composable
fun HabitsScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    vm: HabitsViewModel = viewModel(factory = Factories.HabitList),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    // The habit whose amount box is open, and whether archived habits are showing.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun rows(list: List<HabitRowUi>, dueToday: Boolean) = list.forEachIndexed { i, row ->
        HabitRow(
            row = row,
            dueToday = dueToday,
            expanded = open == row.id && !s.isFuture,
            onBadge = {
                if (row.kind == HabitKind.DONE) vm.record(row, if (row.done) 0 else 1)
                else open = if (open == row.id) null else row.id
            },
            onOpen = { onOpen(row.id) },
            onSet = { vm.record(row, it) },
            card = true,
            enter = i,
        )
    }

    val today = LocalDate.now()
    Page {
        CalendarStrip(
            date = s.date,
            days = StripDays.week(s.date, today, s.badges, lastSelectable = today),
            onSelect = vm::select, onPreviousWeek = vm::previousWeek, onNextWeek = vm::nextWeek,
            canGoNext = Habits.weekStart(s.date).plusWeeks(1) <= today,
            eyebrow = "Habits",
        )
        Column(Modifier.padding(top = 22.dp)) {
            Text(s.title, color = AppTheme.colors.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp))
            Small(s.summary)
            if (!s.isToday) Chip("Back to today", false, vm::goToToday, Modifier.padding(top = 8.dp), small = true)
            if (s.isFuture) Small("This day has not happened yet, so nothing can be ticked.", Modifier.padding(top = 6.dp))
        }
        if (s.isToday && s.today.isNotEmpty() && s.today.none { it.done }) TickHint(Modifier.padding(top = 14.dp))
        if (s.today.isNotEmpty()) rows(s.today, dueToday = true)
        if (s.other.isNotEmpty()) {
            H2(if (s.isToday) "Not due today" else "Not due this day", top = 28.dp)
            rows(s.other, dueToday = false)
        }
        PrimaryButton("Add habit", onAdd)
        if (s.archived.isNotEmpty()) {
            Chip(if (showArchived) "Hide archived" else "Archived (${s.archived.size})", false, { showArchived = !showArchived }, Modifier.padding(top = 12.dp), small = true)
            if (showArchived) {
                Hairline()
                s.archived.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(row.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Body(row.name, Modifier.weight(1f))
                        Small("Archived")
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
internal fun HabitRow(
    row: HabitRowUi,
    dueToday: Boolean,
    expanded: Boolean,
    onBadge: () -> Unit,
    onOpen: () -> Unit,
    onSet: (Int) -> Unit,
    /** A soft white card with room around it instead of a line below. */
    card: Boolean = false,
    enter: Int = 0,
) {
    val c = AppTheme.colors
    val sub = when {
        row.kind == HabitKind.DONE -> row.week ?: row.progress
        else -> listOfNotNull(row.progress, row.week).joinToString(" · ")
    }.let { if (!dueToday && row.week == null) "Not due today" else it }
    val outer = if (card) {
        Modifier.fadeLiftIn(enter * 60).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.soft).padding(horizontal = 12.dp)
    } else Modifier.fillMaxWidth()
    Column(outer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp, horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            // The circle on the left is what marks it done. The emoji is only a picture; the rest of the card opens the habit.
            HabitTick(
                done = row.done,
                partial = if (row.kind == HabitKind.DONE) 0f else row.fraction,
                amountHabit = row.kind != HabitKind.DONE,
                label = if (row.kind == HabitKind.DONE) {
                    if (row.done) "Mark ${row.name} not done" else "Mark ${row.name} done"
                } else "Change today's amount for ${row.name}",
                onClick = onBadge,
            )
            HabitBadge(row.icon, row.color, row.fraction, row.done, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Body(row.name, strong = true, color = if (row.done) AppTheme.colors.mut else AppTheme.colors.fg)
                Small(sub)
            }
            Icon(AppIcons.Forward, null, Modifier.size(20.dp), tint = c.mut)
        }
        if (expanded) {
            AmountPanel(row.kind, row.target, row.amount, onSet, Modifier.padding(bottom = 10.dp))
        }
        if (!card) Hairline()
    }
}

/**
 * A one-time note that says where to tap, until it is dismissed or a habit gets ticked: the circle on the left marks a
 * habit done, and the rest of the card opens it.
 */
@Composable
fun TickHint(modifier: Modifier = Modifier, vm: TickHintViewModel = viewModel(factory = Factories.TickHint)) {
    val seen by vm.seen.collectAsStateWithLifecycle()
    if (seen != false) return
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accentSoft).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        // a picture of the circle being talked about
        Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.bg), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).border(2.dp, c.mut, androidx.compose.foundation.shape.CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Body("Tap the circle to mark it done", strong = true)
            Small("Tap the rest of the card to see its history.", color = c.fg)
        }
        Chip("Got it", false, vm::dismiss, small = true)
    }
}
