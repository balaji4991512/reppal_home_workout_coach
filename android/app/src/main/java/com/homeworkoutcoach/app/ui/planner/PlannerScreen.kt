package com.homeworkoutcoach.app.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import com.homeworkoutcoach.app.ui.components.FlameArt
import com.homeworkoutcoach.app.ui.components.ProgressRing
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import kotlinx.coroutines.delay
import java.time.LocalTime
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
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

    fun submit() {
        if (text.isBlank()) return
        problem = vm.add(text)
        if (problem == null) text = ""
    }

    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(s.month)
                    append(" ")
                    withStyle(SpanStyle(color = c.accent)) { append(s.year) }
                },
                Modifier.weight(1f), color = c.fg, style = AppTheme.type.h1,
            )
            MonthButton(AppIcons.ChevronLeft, "Previous week", true, vm::previousWeek)
            MonthButton(AppIcons.Forward, "Next week", true, vm::nextWeek)
        }

        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.week.forEach { d -> DayCell(d, Modifier.weight(1f)) { vm.select(d.date) } }
        }

        DayHero(s, Modifier.padding(top = 18.dp), onToday = vm::goToToday)

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
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                    .clickable(role = Role.Button) { submit() }
                    .semantics { contentDescription = "Add task" },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, null, Modifier.size(24.dp), tint = c.onAccent) }
        }
        problem?.let { Text(it, Modifier.padding(top = 8.dp), color = c.bad, style = AppTheme.type.small) }

        val untimed = s.tasks.filter { it.minutes == null }
        val timed = s.tasks.filter { it.minutes != null }
        // "Now" is only drawn on today, and moves along as the minutes pass.
        var nowMinutes by remember { mutableIntStateOf(Timeline.minutesOf(LocalTime.now())) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                nowMinutes = Timeline.minutesOf(LocalTime.now())
            }
        }

        if (s.tasks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FlameArt()
                Text("Nothing planned.", Modifier.padding(top = 12.dp), color = c.fg, style = AppTheme.type.h2)
                Small("Add a task above and it lands here.", Modifier.padding(top = 2.dp))
            }
        }
        if (untimed.isNotEmpty()) {
            Small("Anytime", Modifier.padding(top = 22.dp), color = c.mut)
            untimed.forEachIndexed { i, row ->
                key(row.id) {
                    TaskCard(row, onTick = { vm.toggle(row.id) }, onOpen = { onOpen(row.id) }, onTomorrow = { vm.moveToTomorrow(row.id) }, enter = i)
                }
            }
        }
        if (timed.isNotEmpty()) {
            Small("By the clock", Modifier.padding(top = 22.dp), color = c.mut)
            val nowAt = if (s.isToday) Timeline.nowIndex(timed.map { it.minutes!! }, nowMinutes) else null
            // The timeline's stops in order: each timed task, and the Now marker among them.
            val stops: List<Any> = buildList {
                timed.forEachIndexed { i, row ->
                    if (nowAt == i) add(NowStop)
                    add(row)
                }
                if (nowAt == timed.size) add(NowStop)
            }
            stops.forEachIndexed { i, stop ->
                val first = i == 0
                val last = i == stops.lastIndex
                if (stop is TaskRowUi) {
                    key(stop.id) {
                        TimelineStop(first, last, stop.done, time = stop.time, enter = i) {
                            TaskCard(
                                stop, onTick = { vm.toggle(stop.id) }, onOpen = { onOpen(stop.id) },
                                onTomorrow = { vm.moveToTomorrow(stop.id) }, showTime = false, enter = i,
                            )
                        }
                    }
                } else {
                    NowRow(first, last, PlannerFormat.time(LocalTime.of(nowMinutes / 60, nowMinutes % 60)))
                }
            }
        }

        if (s.carriedOver.isNotEmpty()) {
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.tintPeach).padding(14.dp)) {
                H2("Carried over", top = 4.dp)
                Small("Left from earlier days. Nothing is lost; move it, finish it or delete it.", Modifier.padding(bottom = 4.dp))
                var confirm by rememberSaveable { mutableStateOf<String?>(null) }
                s.carriedOver.forEach { row ->
                    TaskCard(row, onTick = { vm.toggle(row.id) }, onOpen = { onOpen(row.id) }, onTomorrow = null) {
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Today", false, { vm.moveToToday(row.id) }, small = true)
                            Chip("Tomorrow", false, { vm.moveToTomorrow(row.id) }, small = true)
                            Chip(
                                if (confirm == row.id) "Sure? Delete" else "Delete", confirm == row.id,
                                { if (confirm == row.id) { confirm = null; vm.delete(row.id) } else confirm = row.id },
                                small = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The marker for the current time on the timeline. */
private object NowStop

/** The day at a glance: its name and date, how it is going, and a progress ring, on a lavender-to-peach wash. */
@Composable
private fun DayHero(s: PlannerUi, modifier: Modifier = Modifier, onToday: () -> Unit) {
    val c = AppTheme.colors
    val done = s.tasks.count { it.done }
    val total = s.tasks.size
    val fraction by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(600), label = "dayRing")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 500f)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 22.sp, lineHeight = 28.sp))
            Small(s.subtitle, color = c.mut)
            Text(s.summary, Modifier.padding(top = 8.dp), color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold))
            if (!s.isToday) TextLink("Back to today", onToday)
        }
        ProgressRing(fraction, size = 76.dp, stroke = 9.dp) {
            Text(
                if (total == 0) "–" else "$done/$total", color = c.fg,
                style = AppTheme.type.tabular(AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)),
            )
        }
    }
}

/**
 * One stop on the timeline: the time at the left, the rail with a node for the task, and the card. The rail joins the
 * stops above and below ([first] and [last] stop it short at the ends).
 */
@Composable
private fun TimelineStop(first: Boolean, last: Boolean, done: Boolean, time: String?, enter: Int, card: @Composable () -> Unit) {
    val c = AppTheme.colors
    val parts = (time ?: "").split(' ')
    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(52.dp).padding(top = 22.dp)) {
            Text(parts.firstOrNull().orEmpty(), color = c.fg, style = AppTheme.type.tabular(AppTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)))
            Text(parts.getOrNull(1).orEmpty(), color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, lineHeight = 13.sp))
        }
        Canvas(Modifier.width(22.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = NODE_Y.toPx()
            val line = c.accent.copy(alpha = 0.22f)
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, y), 3.dp.toPx(), StrokeCap.Round)
            if (!last) drawLine(line, Offset(x, y), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
            if (done) drawCircle(c.accent, 7.dp.toPx(), Offset(x, y))
            else {
                drawCircle(c.bg, 7.dp.toPx(), Offset(x, y))
                drawCircle(c.accent, 7.dp.toPx(), Offset(x, y), style = Stroke(2.5.dp.toPx()))
            }
        }
        Box(Modifier.weight(1f)) { card() }
    }
}

/** "Now": a pulsing dot on the rail and a thin line across, with the time. */
@Composable
private fun NowRow(first: Boolean, last: Boolean, text: String) {
    val c = AppTheme.colors
    val pulse = rememberInfiniteTransition(label = "now")
    val grow by pulse.animateFloat(1f, 2.1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "grow")
    val fade by pulse.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "fade")
    Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Now", Modifier.width(52.dp), color = c.accent, style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Canvas(Modifier.width(22.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = size.height / 2
            val line = c.accent.copy(alpha = 0.22f)
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, y), 3.dp.toPx(), StrokeCap.Round)
            if (!last) drawLine(line, Offset(x, y), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(c.accent.copy(alpha = fade), 6.dp.toPx() * grow, Offset(x, y))
            drawCircle(c.accent, 6.dp.toPx(), Offset(x, y))
        }
        Box(Modifier.weight(1f).height(1.5.dp).background(c.accent.copy(alpha = 0.35f)))
        Text(text, Modifier.padding(start = 8.dp), color = c.accent, style = AppTheme.type.small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** A tick that fills with a little spring, and the check pops in. */
@Composable
private fun TickBox(done: Boolean, label: String, onTick: () -> Unit) {
    val c = AppTheme.colors
    val fill by animateFloatAsState(
        if (done) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "tick",
    )
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onTick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val r = size.width / 2
            drawCircle(c.accent.copy(alpha = 0.55f), radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (fill > 0f) drawCircle(c.accent, radius = r * fill)
        }
        if (fill > 0.15f) {
            Icon(
                AppIcons.Check, null,
                Modifier.size(16.dp).graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill.coerceIn(0f, 1f) },
                tint = c.onAccent,
            )
        }
    }
}

private val NODE_Y = 36.dp

/** A day in the strip: weekday, date in a circle (filled when shown, ringed for today), and its done/total. */
@Composable
private fun DayCell(d: WeekDayUi, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val fillColor by animateColorAsState(if (d.selected) c.accent else Color.Transparent, tween(250), label = "day")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).selectable(selected = d.selected, role = Role.Tab, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(d.label, color = c.mut, style = AppTheme.type.small.copy(fontSize = 12.sp))
        Box(
            Modifier.padding(top = 6.dp).size(40.dp).clip(CircleShape)
                .background(fillColor)
                .then(if (d.isToday && !d.selected) Modifier.border(1.5.dp, c.accent, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${d.number}", color = if (d.selected) c.onAccent else c.fg,
                style = AppTheme.type.tabular(AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = if (d.selected || d.isToday) FontWeight.Bold else FontWeight.Medium)),
            )
        }
        if (d.total > 0) {
            val all = d.done == d.total
            Text(
                "${d.done}/${d.total}", Modifier.padding(top = 6.dp).clip(RoundedCornerShape(99.dp)).background(if (all) c.tintMint else c.accentSoft).padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (all) c.ok else c.accent, style = AppTheme.type.tabular(AppTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)),
            )
        } else {
            Spacer(Modifier.padding(top = 6.dp).height(16.dp))
        }
    }
}

/** A task as a soft card: a round tick, the title, its time as an indigo pill and the first line of its note. */
@Composable
private fun TaskCard(
    row: TaskRowUi,
    onTick: () -> Unit,
    onOpen: () -> Unit,
    onTomorrow: (() -> Unit)?,
    showTime: Boolean = true,
    enter: Int = 0,
    below: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(Modifier.fadeLiftIn(enter * 70).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(horizontal = 6.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            TickBox(row.done, if (row.done) "Mark ${row.title} not done" else "Mark ${row.title} done", onTick)
            Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp, horizontal = 2.dp)) {
                Text(
                    row.title,
                    color = if (row.done) c.mut else c.fg,
                    style = AppTheme.type.bodyStrong.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                val note = row.note.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
                val from = row.dayLabel?.let { "From ${it.replaceFirstChar(Char::lowercase)}" }
                val shownTime = row.time.takeIf { showTime }
                if (shownTime != null || note != null || from != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shownTime?.let {
                            Text(
                                it, color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                        listOfNotNull(from, note).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Text(it, color = c.mut, style = AppTheme.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (onTomorrow != null && !row.done) Chip("Tomorrow", false, onTomorrow, Modifier.padding(start = 8.dp, end = 6.dp), small = true)
        }
        if (below != null && !row.done) Box(Modifier.padding(start = 52.dp, end = 8.dp, bottom = 8.dp)) { below() }
    }
}

/**
 * What was left from earlier days: tick it, move it to today or tomorrow, or delete it (a second tap confirms).
 * Shows nothing when [rows] is empty.
 */
@Composable
internal fun CarriedOver(
    rows: List<TaskRowUi>,
    onTick: (String) -> Unit,
    onOpen: (String) -> Unit,
    onToday: (String) -> Unit,
    onTomorrow: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    // The task whose Delete was tapped once; a second tap deletes it.
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    H2("Carried over", top = 28.dp)
    Small("Left from earlier days. Nothing is lost; move it, finish it or delete it.", Modifier.padding(bottom = 6.dp))
    Hairline()
    rows.forEach { row ->
        TaskRow(row, onTick = { onTick(row.id) }, onOpen = { onOpen(row.id) }, onTomorrow = null) {
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Move to today", false, { onToday(row.id) }, small = true)
                Chip("Move to tomorrow", false, { onTomorrow(row.id) }, small = true)
                Chip(
                    if (confirm == row.id) "Sure? Delete" else "Delete", confirm == row.id,
                    { if (confirm == row.id) { confirm = null; onDelete(row.id) } else confirm = row.id },
                    small = true,
                )
            }
        }
        Hairline()
    }
}

/**
 * A task: tick, title with its time and note, and "Tomorrow" for one with [onTomorrow]. [below] goes under the row
 * (the carried-over actions).
 */
@Composable
internal fun TaskRow(
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
