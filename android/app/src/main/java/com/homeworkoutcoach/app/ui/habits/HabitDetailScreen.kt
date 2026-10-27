package com.homeworkoutcoach.app.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.LineRow
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Segments
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.DayState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One habit: this week, a month calendar you can change past days in, and the totals. */
@Composable
fun HabitDetailScreen(
    id: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    vm: HabitDetailViewModel = viewModel(key = "habit-$id", factory = Factories.habitDetail(id)),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    // The tapped day, as an epoch day (saved across rotation).
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = picked?.let(LocalDate::ofEpochDay)
    val cell = selected?.let { d -> s.weeks.flatten().firstOrNull { it?.date == d } }

    Page {
        Row(
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.ChevronLeft, null, Modifier.size(20.dp), tint = c.mut)
            Small("Habits")
        }

        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HabitBadge(s.row.icon, s.row.color, s.row.fraction, s.row.done, size = 56.dp)
            Column(Modifier.weight(1f)) {
                H1(s.row.name)
                Small(s.schedule + if (s.row.archived) " · Archived" else "")
            }
        }

        H2("This week", top = 24.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (s.weekTarget > 0) "${s.weekDone} of ${s.weekTarget}" else "Nothing scheduled",
                color = c.fg,
                style = AppTheme.type.tabular(AppTheme.type.h2),
            )
            if (s.weekComplete) Small("Done for the week", color = c.ok)
        }
        if (s.weekTarget > 0) Segments(s.weekTarget, minOf(s.weekDone, s.weekTarget), Modifier.padding(top = 8.dp))

        // Month calendar
        Row(Modifier.padding(top = 28.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthButton(AppIcons.ChevronLeft, "Previous month", s.canPrev, vm::previousMonth)
            Text(
                s.monthTitle, Modifier.weight(1f), color = c.fg, textAlign = TextAlign.Center,
                style = AppTheme.type.h2,
            )
            MonthButton(AppIcons.Forward, "Next month", s.canNext, vm::nextMonth)
        }
        Row(Modifier.padding(top = 6.dp)) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), Modifier.weight(1f),
                    color = c.mut, textAlign = TextAlign.Center, style = AppTheme.type.small,
                )
            }
        }
        s.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(day, s.row.color, day.date == selected) {
                                if (day.editable) picked = if (selected == day.date) null else day.date.toEpochDay()
                            }
                        }
                    }
                }
            }
        }

        if (cell != null && cell.editable) {
            val d = cell.date
            Small(
                "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
                Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            AmountPanel(s.row.kind, s.row.target, cell.amount, { vm.record(d, it) })
        } else {
            Small("Tap a day to change it.", Modifier.padding(top = 12.dp))
        }

        H2("Totals", top = 28.dp)
        LineRow(left = { Body("This month") }, right = { Body("${days(s.monthDays)}", strong = true) })
        LineRow(left = { Body("All time") }, right = { Body("${days(s.allTimeDays)}", strong = true) })
        LineRow(left = { Body("Started") }, right = { Body(s.since, strong = true) }, divider = false)

        PrimaryButton("Edit habit", onEdit, alt = true)
    }
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

@Composable
private fun MonthButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) Icon(icon, null, Modifier.size(22.dp), tint = AppTheme.colors.fg)
    }
}

/** A day: a solid disc when done, a part ring when partly done, a plain number otherwise. Future days are faint. */
@Composable
private fun DayCell(day: DayCellUi, color: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val tint = HabitLook.color(color)
    val done = day.state == DayState.DONE
    val faint = day.future || day.state == DayState.NOT_DUE || !day.editable
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, c.fg, CircleShape) else if (day.isToday) Modifier.border(1.dp, c.mut, CircleShape) else Modifier)
            .clickable(enabled = day.editable, role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "${day.date.dayOfMonth}, " + when (day.state) {
                    DayState.DONE -> "done"
                    DayState.PARTIAL -> "partly done"
                    DayState.OPEN -> "not done"
                    DayState.NOT_DUE -> "not scheduled"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp)) {
            if (done) {
                drawCircle(tint)
            } else if (day.state == DayState.PARTIAL && day.fraction > 0f) {
                val w = 2.5.dp.toPx()
                drawCircle(c.bd, style = Stroke(w), radius = (size.width - w) / 2)
                drawArc(
                    tint, -90f, 360f * day.fraction, false, topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "${day.date.dayOfMonth}",
            color = when {
                done -> c.on
                faint -> c.mut.copy(alpha = 0.55f)
                else -> c.fg
            },
            style = AppTheme.type.small.copy(fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal),
        )
    }
}
