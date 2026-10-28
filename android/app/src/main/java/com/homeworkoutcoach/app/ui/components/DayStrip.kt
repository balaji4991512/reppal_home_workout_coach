package com.homeworkoutcoach.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Habits
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A small note under a day in the strip ("2/4", "25m", a tick); [complete] gives it the green look. */
data class DayBadge(val text: String, val complete: Boolean = false)

/** One day in the strip. A day that is not [enabled] (one that has not happened, on pages that record the past) is faint. */
data class StripDay(
    val date: LocalDate,
    /** "Mon". */
    val label: String,
    val number: Int,
    val selected: Boolean,
    val isToday: Boolean,
    val enabled: Boolean = true,
    val badge: DayBadge? = null,
)

/** The week around a day, Monday to Sunday, for the strip every day-based page shares. */
object StripDays {
    /**
     * The seven days of the week [selected] is in. [badges] says what to show under each day, and [lastSelectable] is
     * the last day that can be chosen (null for any), so a page that records the past can leave the future faint.
     */
    fun week(
        selected: LocalDate,
        today: LocalDate,
        badges: Map<LocalDate, DayBadge> = emptyMap(),
        lastSelectable: LocalDate? = null,
    ): List<StripDay> {
        val monday = Habits.weekStart(selected)
        return (0L..6L).map { monday.plusDays(it) }.map { d ->
            StripDay(
                date = d,
                label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                number = d.dayOfMonth,
                selected = d == selected,
                isToday = d == today,
                enabled = lastSelectable == null || !d.isAfter(lastSelectable),
                badge = badges[d],
            )
        }
    }

    /** The month and year of [date], as in "October" and "2026". */
    fun month(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

/**
 * The calendar at the top of a day-based page: the month and year with arrows for the week before and after, and the
 * week as seven tappable days.
 */
@Composable
fun CalendarStrip(
    date: LocalDate,
    days: List<StripDay>,
    onSelect: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
    /** A small label above the month that names the page ("HABITS"). */
    eyebrow: String? = null,
) {
    val c = AppTheme.colors
    Column(modifier) {
        eyebrow?.let {
            Text(it.uppercase(), color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(StripDays.month(date))
                    append(" ")
                    withStyle(SpanStyle(color = c.accent)) { append(date.year.toString()) }
                },
                Modifier.weight(1f), color = c.fg, style = AppTheme.type.h1,
            )
            StripArrow(AppIcons.ChevronLeft, "Previous week", true, onPreviousWeek)
            StripArrow(AppIcons.Forward, "Next week", canGoNext, onNextWeek)
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { d -> DayCell(d, Modifier.weight(1f)) { onSelect(d.date) } }
        }
    }
}

@Composable
private fun StripArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) Icon(icon, null, Modifier.size(22.dp), tint = AppTheme.colors.fg)
    }
}

/** A day: the weekday, the date in a circle (filled when shown, ringed for today), and its badge. */
@Composable
private fun DayCell(d: StripDay, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val fillColor by animateColorAsState(if (d.selected) c.accent else Color.Transparent, tween(250), label = "day")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).alpha(if (d.enabled) 1f else 0.4f)
            .then(if (d.enabled) Modifier.selectable(selected = d.selected, role = Role.Tab, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(d.label, color = c.mut, style = AppTheme.type.small.copy(fontSize = 12.sp))
        Box(
            Modifier.padding(top = 6.dp).size(40.dp).clip(CircleShape).background(fillColor)
                .then(if (d.isToday && !d.selected) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${d.number}", color = if (d.selected) c.onAccent else c.fg,
                style = AppTheme.type.tabular(AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = if (d.selected || d.isToday) FontWeight.Bold else FontWeight.Medium)),
            )
        }
        val badge = d.badge
        if (badge != null) {
            Text(
                badge.text, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(99.dp)).background(if (badge.complete) c.tintMint else c.accentSoft).padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (badge.complete) c.ok else c.accent,
                style = AppTheme.type.tabular(AppTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)),
            )
        } else {
            Spacer(Modifier.padding(top = 6.dp).height(16.dp))
        }
    }
}
