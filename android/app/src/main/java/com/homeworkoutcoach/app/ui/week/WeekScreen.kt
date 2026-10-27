package com.homeworkoutcoach.app.ui.week

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.WeekViewModel
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.LineRow
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Pill
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Segments
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.SetCountStatus

@Composable
fun WeekScreen(onStart: () -> Unit, onArrange: () -> Unit = {}, vm: WeekViewModel = viewModel(factory = Factories.Week)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    ui?.let { WeekContent(it, onStart, vm::swap, onArrange) }
}

/** This week [vWeek]: hero, Monday to Sunday, left-out note, planned volume. */
@Composable
fun WeekContent(
    ui: WeekUi,
    onStart: () -> Unit,
    onSwap: (baseId: String, chosenId: String) -> Unit,
    onArrange: () -> Unit = {},
) {
    // Days the user opened; the next workout starts open.
    var open by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    val nextIndex = ui.hero.done
    val opened = open ?: listOf(nextIndex)
    // "day:item" whose swap list is showing.
    var swapKey by rememberSaveable { mutableStateOf<String?>(null) }

    Page {
        Hero(ui.hero, onStart)
        H2("Your week")
        ui.days.forEach { day ->
            Hairline()
            when (day) {
                is WeekdayUi.Rest -> RestRow(day.abbr)
                is WeekdayUi.Training -> TrainingDay(
                    day = day,
                    expanded = day.index in opened,
                    onToggle = { open = if (day.index in opened) opened - day.index else opened + day.index },
                    swapKey = swapKey,
                    onSwapKey = { swapKey = if (swapKey == it) null else it },
                    onSwap = { base, to -> swapKey = null; onSwap(base, to) },
                )
            }
        }
        Hairline()
        TextLink("Rearrange days", onArrange)
        if (ui.leftOut.isNotEmpty()) Muted("Left out because of what you told me: ${ui.leftOut.joinToString(", ")}.")
        PlannedVolume(ui.volume)
    }
}

@Composable
private fun Hero(h: HeroUi, onStart: () -> Unit) {
    Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        Small(h.when_)
        H1(h.title, Modifier.padding(top = 6.dp))
        Body(h.summary, Modifier.padding(top = 6.dp))
        Segments(
            h.total, h.done,
            Modifier.padding(top = 16.dp).semantics { contentDescription = "${h.done} of ${h.total} workouts done this week" },
        )
        Small("${h.done} of ${h.total} workouts done this week", Modifier.padding(top = 6.dp))
        if (h.canStart) PrimaryButton("Start workout", onStart)
        else Muted("Nothing to train on this day yet. Add muscles or equipment under Plan settings.")
    }
}

@Composable
private fun DayAbbr(text: String) =
    Text(text, Modifier.width(44.dp), color = AppTheme.colors.mut, style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold))

@Composable
private fun RestRow(abbr: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DayAbbr(abbr)
        Body("Rest", color = AppTheme.colors.mut)
    }
}

@Composable
private fun Chevron(expanded: Boolean) =
    Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = AppTheme.colors.mut)

@Composable
private fun TrainingDay(
    day: WeekdayUi.Training,
    expanded: Boolean,
    onToggle: () -> Unit,
    swapKey: String?,
    onSwapKey: (String) -> Unit,
    onSwap: (String, String) -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DayAbbr(day.abbr)
        Column(Modifier.weight(1f)) {
            Body(day.name, strong = true)
            Small("${day.exerciseCount} exercises")
        }
        when (day.status) {
            DayStatus.DONE -> Pill("Done", c.oks, c.ok)
            DayStatus.NEXT -> Pill("Next up", c.fg, c.bg)
            DayStatus.UPCOMING -> Unit
        }
        Chevron(expanded)
    }
    AnimatedVisibility(expanded) {
        Column(Modifier.padding(bottom = 10.dp)) {
            if (day.status == DayStatus.DONE) {
                day.session?.let { SessionCard(it) }
            } else {
                day.exercises.forEachIndexed { i, ex ->
                    val key = "${day.index}:$i"
                    ExerciseRow(
                        ex,
                        last = i == day.exercises.lastIndex,
                        swapOpen = swapKey == key,
                        onSwapClick = { onSwapKey(key) },
                        onSwap = onSwap,
                    )
                }
                if (day.missing.isNotEmpty()) {
                    Muted("Nothing available for ${day.missing.joinToString(", ").lowercase()} with your equipment.")
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(ex: ExerciseRowUi, last: Boolean, swapOpen: Boolean, onSwapClick: () -> Unit, onSwap: (String, String) -> Unit) {
    val c = AppTheme.colors
    LineRow(
        // The day's own hairline closes the list, so the last row has none.
        divider = !swapOpen && !last,
        left = {
            Body(ex.name)
            if (ex.swapped) Small("Swapped")
        },
        right = {
            Text(ex.prescription, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
            Icon(
                AppIcons.Swap,
                contentDescription = "Swap ${ex.name}",
                tint = if (swapOpen) c.fg else c.mut,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (swapOpen) c.bd else c.bg)
                    .clickable(role = Role.Button, onClick = onSwapClick)
                    .padding(7.dp),
            )
        },
    )
    if (swapOpen) {
        Column(Modifier.padding(top = 4.dp, bottom = 14.dp)) {
            Muted("Swap ${ex.name} for:")
            if (ex.alternatives.isEmpty()) {
                Muted("Nothing else available for this muscle with your equipment.")
            } else {
                Chips { ex.alternatives.forEach { (id, name) -> Chip(name, false, { onSwap(ex.baseId, id) }, small = true) } }
            }
            if (ex.swapped) Chip("Back to ${ex.baseName}", false, { onSwap(ex.baseId, ex.baseId) }, small = true)
            Muted("Applies to every session. Progress is tracked separately for each exercise.")
        }
        if (!last) Hairline()
    }
}

/** Collapsed "Planned volume this week" section [volCard]. */
@Composable
private fun PlannedVolume(v: VolumeUi) {
    val c = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var showSets by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 24.dp)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Planned volume this week", Modifier.weight(1f), color = c.fg, style = AppTheme.type.h2)
            Chevron(expanded)
        }
        AnimatedVisibility(expanded) {
            Column {
                Body(v.headline, Modifier.padding(vertical = 6.dp))
                v.rows.forEachIndexed { i, r ->
                    LineRow(divider = i < v.rows.lastIndex, left = { Body(r.muscle) }) {
                        Text(r.load, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                    }
                }
                Muted(
                    "Weighted exercises show kg (sets × reps × weight). Bodyweight exercises show total reps, because their " +
                        "load can’t be measured honestly in kg. Only the main muscle of each exercise gets the kg.",
                )
                PrimaryButton(if (showSets) "Hide sets per muscle" else "Show sets per muscle", { showSets = !showSets }, alt = true)
                if (showSets) {
                    v.rows.forEachIndexed { i, r ->
                        LineRow(divider = i < v.rows.lastIndex, left = { Body(r.muscle) }) {
                            Text(
                                r.sets,
                                color = when (r.status) {
                                    SetCountStatus.LOW -> c.bad
                                    SetCountStatus.HIGH -> c.mut
                                    SetCountStatus.ON_TARGET -> c.ok
                                },
                                style = AppTheme.type.small.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    Muted(v.setTarget)
                }
            }
        }
    }
}
