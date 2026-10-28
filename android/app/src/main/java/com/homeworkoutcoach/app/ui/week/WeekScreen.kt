package com.homeworkoutcoach.app.ui.week

import com.homeworkoutcoach.app.ui.components.OutlineButton
import androidx.compose.animation.AnimatedVisibility
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import com.homeworkoutcoach.app.ui.components.ProgressRing
import com.homeworkoutcoach.app.ui.components.AccentButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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

/** This week [vWeek]: hero, the seven days at a glance, a card per training day, left-out note, planned volume. */
@Composable
fun WeekContent(
    ui: WeekUi,
    onStart: () -> Unit,
    onSwap: (baseId: String, chosenId: String) -> Unit,
    onArrange: () -> Unit = {},
) {
    // Days the user opened; the next workout starts open.
    var open by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    // Today's workout starts open; on a rest day, the next one.
    val startOpen = ui.days.filterIsInstance<WeekdayUi.Training>()
        .firstOrNull { it.status == DayStatus.TODAY } ?: ui.days.filterIsInstance<WeekdayUi.Training>().firstOrNull { it.status == DayStatus.UPCOMING }
    val opened = open ?: listOfNotNull(startOpen?.index)
    // "day:item" whose swap list is showing.
    var swapKey by rememberSaveable { mutableStateOf<String?>(null) }

    Page {
        Hero(ui.hero, onStart)
        WeekStrip(ui.days, Modifier.padding(top = 16.dp))
        H2("Your week")
        ui.days.forEachIndexed { i, day ->
            when (day) {
                is WeekdayUi.Rest -> RestRow(day.abbr, i)
                is WeekdayUi.Training -> TrainingDay(
                    day = day,
                    index = i,
                    expanded = day.index in opened,
                    onToggle = { open = if (day.index in opened) opened - day.index else opened + day.index },
                    swapKey = swapKey,
                    onSwapKey = { swapKey = if (swapKey == it) null else it },
                    onSwap = { base, to -> swapKey = null; onSwap(base, to) },
                )
            }
        }
        OutlineButton("Rearrange days", onArrange, tint = AppTheme.colors.accent)
        if (ui.leftOut.isNotEmpty()) Muted("Left out because of what you told me: ${ui.leftOut.joinToString(", ")}.")
        PlannedVolume(ui.volume)
    }
}

/** The week in one card: a ring of workouts done, what is next, and the Start button. */
@Composable
private fun Hero(h: HeroUi, onStart: () -> Unit) {
    val c = AppTheme.colors
    val fraction by animateFloatAsState(if (h.total == 0) 0f else h.done.toFloat() / h.total, tween(700), label = "weekRing")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 700f))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(c.accent.copy(alpha = 0.07f), radius = size.height * 0.6f, center = Offset(size.width * 0.95f, -size.height * 0.1f))
            drawCircle(c.coral.copy(alpha = 0.10f), radius = size.height * 0.35f, center = Offset(size.width * 0.8f, size.height * 1.0f))
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(fraction, size = 92.dp, stroke = 10.dp, track = c.bg.copy(alpha = 0.7f)) {
                    Text(
                        "${h.done}/${h.total}", color = c.fg,
                        style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Small(h.when_, color = c.mut)
                    Text(h.title, Modifier.padding(top = 2.dp), color = c.fg, style = AppTheme.type.h2.copy(fontSize = 22.sp, lineHeight = 27.sp))
                    Small(h.summary, Modifier.padding(top = 2.dp), color = c.fg)
                }
            }
            Small(
                "${h.done} of ${h.total} workouts done this week", Modifier.padding(top = 12.dp),
                color = c.mut,
            )
            when (h.kind) {
                HeroKind.TODAY ->
                    if (h.canStart) AccentButton("Start workout", onStart, Modifier.padding(top = 14.dp), icon = AppIcons.Dumbbell)
                    else Muted("Nothing to train on this day yet. Add muscles or equipment under Plan settings.")
                HeroKind.DONE_TODAY -> Text(
                    "Done for today. " + (h.nextTitle?.let { "Next: ${h.nextDay}, $it." } ?: "That is the week."),
                    Modifier.padding(top = 12.dp), color = c.ok, style = AppTheme.type.bodyStrong,
                )
                HeroKind.REST -> Small(
                    "Each workout is done on its own day, so there is nothing to start today.",
                    Modifier.padding(top = 10.dp), color = c.mut,
                )
            }
        }
    }
}

/** Seven days as circles: done is green, the next one is indigo, others to come are ringed, rest days are faint. */
@Composable
private fun WeekStrip(days: List<WeekdayUi>, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            val status = (d as? WeekdayUi.Training)?.status
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(
                            when (status) {
                                DayStatus.DONE -> c.ok
                                DayStatus.TODAY -> c.accent
                                DayStatus.SKIPPED -> c.tintRose
                                else -> Color.Transparent
                            },
                        )
                        .then(if (status == DayStatus.UPCOMING) Modifier.border(1.5.dp, c.accent.copy(alpha = 0.6f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    when (status) {
                        DayStatus.DONE -> Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = c.bg)
                        DayStatus.TODAY -> Icon(AppIcons.Dumbbell, null, Modifier.size(20.dp), tint = c.onAccent)
                        DayStatus.SKIPPED -> Icon(AppIcons.Close, null, Modifier.size(18.dp), tint = c.coral)
                        DayStatus.UPCOMING -> Icon(AppIcons.Dumbbell, null, Modifier.size(18.dp), tint = c.accent)
                        null -> Text("–", color = c.mut.copy(alpha = 0.6f), style = AppTheme.type.small)
                    }
                }
                Text(
                    d.abbr.take(3), Modifier.padding(top = 6.dp),
                    color = if (status == null) c.mut.copy(alpha = 0.6f) else c.fg, style = AppTheme.type.small.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun RestRow(abbr: String, index: Int) {
    val c = AppTheme.colors
    Row(
        Modifier.fadeLiftIn(index * 50).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accs.copy(alpha = 0.6f))
            .heightIn(min = 46.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(abbr, Modifier.width(36.dp), color = c.mut, style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold))
        Text("Rest day", color = c.mut, style = AppTheme.type.body)
    }
}

@Composable
private fun Chevron(expanded: Boolean) =
    Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = AppTheme.colors.mut)

@Composable
private fun TrainingDay(
    day: WeekdayUi.Training,
    index: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    swapKey: String?,
    onSwapKey: (String) -> Unit,
    onSwap: (String, String) -> Unit,
) {
    val c = AppTheme.colors
    val disc = when (day.status) {
        DayStatus.DONE -> c.tintMint
        DayStatus.TODAY -> c.accentSoft
        DayStatus.SKIPPED -> c.tintRose
        DayStatus.UPCOMING -> c.tintPeach
    }
    Column(Modifier.fadeLiftIn(index * 50).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.soft)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
                Text(day.abbr.take(3), color = c.fg, style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            Column(Modifier.weight(1f)) {
                Body(day.name, strong = true)
                Small("${day.exerciseCount} exercises")
            }
            when (day.status) {
                DayStatus.DONE -> Pill("Done", c.oks, c.ok)
                DayStatus.TODAY -> Pill("Today", c.accent, c.onAccent)
                DayStatus.SKIPPED -> Pill("Skipped", c.tintRose, c.coral)
                DayStatus.UPCOMING -> Unit
            }
            Chevron(expanded)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                if (day.status == DayStatus.DONE) {
                    day.session?.let { SessionCard(it) }
                } else if (day.status == DayStatus.SKIPPED) {
                    Muted("This workout was not done on ${day.abbr}. Each workout belongs to its own day, so it stays marked as skipped.")
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
}

@Composable
private fun ExerciseRow(ex: ExerciseRowUi, last: Boolean, swapOpen: Boolean, onSwapClick: () -> Unit, onSwap: (String, String) -> Unit) {
    val c = AppTheme.colors
    LineRow(
        // The card closes the list, so the last row has no line.
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
                tint = if (swapOpen) c.accent else c.mut,
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(if (swapOpen) c.accentSoft else c.accs)
                    .clickable(role = Role.Button, onClick = onSwapClick)
                    .padding(8.dp),
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

/** Collapsed "Planned volume this week" card [volCard]. */
@Composable
private fun PlannedVolume(v: VolumeUi) {
    val c = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var showSets by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.soft).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Planned volume this week", Modifier.weight(1f), color = c.fg, style = AppTheme.type.h2)
            Chevron(expanded)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 12.dp)) {
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
                OutlineButton(if (showSets) "Hide sets per muscle" else "Show sets per muscle", { showSets = !showSets })
                if (showSets) {
                    v.rows.forEachIndexed { i, r ->
                        LineRow(divider = i < v.rows.lastIndex, left = { Body(r.muscle) }) {
                            Text(
                                r.sets,
                                color = when (r.status) {
                                    SetCountStatus.LOW -> c.coral
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
