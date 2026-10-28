package com.homeworkoutcoach.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.AccentChip
import com.homeworkoutcoach.app.ui.components.AccentButton
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import com.homeworkoutcoach.app.ui.components.H2
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.plan.WorkoutSession
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.LineRow
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.week.SessionCard
import com.homeworkoutcoach.app.voice.MicButton
import com.homeworkoutcoach.app.voice.VoiceError
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.SetRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Today's workout, then Workout done. [onActive] tells the shell whether leaving would lose logged sets;
 * [onLeaveRequest] asks it to confirm (back button).
 */
@Composable
fun WorkoutScreen(
    onActive: (Boolean) -> Unit,
    onLeaveRequest: () -> Unit,
    onWeek: () -> Unit,
    vm: WorkoutViewModel = viewModel(factory = Factories.Workout),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logging = state is WorkoutState.Logging || state is WorkoutState.Saving
    LaunchedEffect(logging) { onActive(logging) }
    BackHandler(enabled = logging, onBack = onLeaveRequest)

    when (val s = state) {
        WorkoutState.Loading, WorkoutState.Saving -> Unit
        WorkoutState.Empty -> LaunchedEffect(Unit) { onWeek() }
        is WorkoutState.NotToday -> NotTodayContent(s.title, s.note, onWeek)
        is WorkoutState.Logging -> LoggingContent(
            s,
            onText = vm::onText,
            onReps = vm::onReps,
            onEffort = vm::onEffort,
            onPain = vm::onPain,
            onCheck = vm::onCheck,
            onSave = vm::onSave,
            voice = VoiceHandlers(vm::onVoiceListening, vm::onVoicePartial, vm::onVoiceResult, vm::onVoiceError),
        )
        is WorkoutState.Done -> DoneContent(s.ui, onWeek)
    }
}

/** Rest day, or today's workout already done: nothing to start, and where to look instead. */
@Composable
private fun NotTodayContent(title: String, note: String, onWeek: () -> Unit) {
    val c = AppTheme.colors
    val rest = title == "Rest day"
    Page {
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(if (rest) c.accentSoft else c.tintMint, c.tintPeach), start = Offset.Zero, end = Offset(900f, 700f))),
        ) {
            Column(Modifier.padding(24.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Icon(if (rest) AppIcons.Moon else AppIcons.Check, null, Modifier.size(26.dp), tint = if (rest) c.accent else c.ok)
                }
                H1(title, Modifier.padding(top = 14.dp))
                Body(note, Modifier.padding(top = 6.dp))
                Small("Each workout is done on its own day.", Modifier.padding(top = 10.dp), color = c.mut)
            }
        }
        AccentButton("See your week", onWeek, Modifier.padding(top = 18.dp))
    }
}

/** Voice callbacks, kept together so previews and tests can leave the Mic out. */
class VoiceHandlers(
    val onListening: () -> Unit,
    val onPartial: (String) -> Unit,
    val onResult: (String) -> Unit,
    val onError: (VoiceError) -> Unit,
)

/** One exercise at a time [vLog]. */
@Composable
fun LoggingContent(
    state: WorkoutState.Logging,
    onText: (String) -> Unit,
    onReps: (Int) -> Unit,
    onEffort: (Effort) -> Unit,
    onPain: () -> Unit,
    onCheck: () -> Unit,
    onSave: () -> Unit,
    voice: VoiceHandlers? = null,
) {
    val c = AppTheme.colors
    val session = state.session
    val e = session.exercise
    val target = session.target
    val input = state.input
    Page {
        ProgressDots(session)
        Muted("${session.slot}, exercise ${session.index + 1} of ${session.day.items.size}")
        H1(e.name)

        // The target in a card, with the sets under it.
        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 600f)))
                .padding(20.dp),
        ) {
            Text("YOUR TARGET", color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${target.targetReps}",
                    Modifier.alignByBaseline(),
                    color = c.fg,
                    style = AppTheme.type.tabular(AppTheme.type.big),
                )
                Text(
                    "reps" + if (e.bodyweight) "" else " at ${Copy.kg(target.weightKg)} kg",
                    Modifier.alignByBaseline(),
                    color = c.fg,
                    style = AppTheme.type.body.copy(fontSize = 18.sp),
                )
            }
            SetPills(session)
        }

        if (state.note != null && session.setIndex > 0) {
            Text(state.note, Modifier.padding(top = 10.dp), color = c.ok, style = AppTheme.type.bodyStrong)
        }

        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                input.text,
                onText,
                placeholder = if (input.listening) "Listening…" else "Or say or type how the set went",
                label = "Set feedback",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCheck() }),
            )
            voice?.let { v ->
                MicButton(input.listening, v.onListening, v.onPartial, v.onResult, v.onError, Modifier.heightIn(min = 50.dp))
            }
        }

        Muted("Reps you did", Modifier.padding(top = 10.dp))
        Chips {
            (0 until 5).map { target.targetReps - it }.filter { it >= 1 }.forEach { n ->
                AccentChip("$n", input.shownReps == n, { onReps(n) }, Modifier.widthIn(min = 52.dp))
            }
        }
        Muted("How it felt")
        Chips {
            listOf(Effort.EASY to "Easy", Effort.JUST_RIGHT to "Just right", Effort.HARD to "Hard").forEach { (k, label) ->
                AccentChip(label, input.shownEffort == k, { onEffort(k) })
            }
            AccentChip("Felt pain", input.shownPain, onPain)
        }

        if (input.reps == null && input.preview == null) OutlineButton("Check my note", onCheck, tint = c.accent)
        input.error?.let { Text(it, Modifier.padding(vertical = 6.dp), color = c.bad, style = AppTheme.type.bodyStrong) }
        input.preview?.let { SetPreview(it, onSave) }
    }
}

@Composable
private fun ProgressDots(session: WorkoutSession) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp)
            .semantics { contentDescription = "Exercise ${session.index + 1} of ${session.day.items.size}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        session.day.items.indices.forEach { i ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(
                    when {
                        i < session.index -> c.accent
                        i == session.index -> c.accent.copy(alpha = 0.45f)
                        else -> c.bd
                    },
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetPills(session: WorkoutSession) {
    val c = AppTheme.colors
    val done = session.repsDone
    FlowRow(
        Modifier.padding(top = 16.dp).semantics { contentDescription = "Sets" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(session.planned.sets) { i ->
            val isDone = i < done.size
            val current = !isDone && i == session.setIndex
            Box(
                Modifier.widthIn(min = 60.dp).height(44.dp).clip(RoundedCornerShape(99.dp))
                    .background(if (isDone) c.ok else if (current) c.accent else c.bg.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isDone) "✓ ${done[i]}" else "Set ${i + 1}",
                    color = if (isDone) c.bg else if (current) c.onAccent else c.mut,
                    style = AppTheme.type.tabular(AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold)),
                )
            }
        }
    }
}

/** One-line readback, Save set, and the raw record folded away. */
@Composable
private fun SetPreview(r: SetRecord, onSave: () -> Unit) {
    val c = AppTheme.colors
    var showData by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        Body(
            "${r.reps} reps" + (r.weightKg?.let { " at ${Copy.kg(it)} kg" } ?: "") + ", ${r.effort.key}" +
                (if (r.comment.isNotEmpty()) ". ${r.comment}" else ""),
            strong = true,
        )
        if (r.pain) {
            Text(
                "Pain reported. Saving will stop this exercise and remove it from your plan.",
                Modifier.padding(top = 6.dp),
                color = c.bad,
                style = AppTheme.type.bodyStrong,
            )
        }
        AccentButton("Save set", onSave, Modifier.padding(top = 14.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { showData = !showData }
                .semantics { stateDescription = if (showData) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Small("Show saved data")
        }
        if (showData) {
            Text(
                recordJson(r),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.soft).padding(10.dp),
                color = c.fg,
                style = AppTheme.type.small.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

/** The set record in the spec's JSON shape. */
fun recordJson(r: SetRecord): String = prettyJson.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("reps", r.reps)
        put("weight_kg", r.weightKg?.let { if (it % 1.0 == 0.0) JsonPrimitive(it.toLong()) else JsonPrimitive(it) } ?: JsonNull)
        put("effort", r.effort.key)
        put("flags", buildJsonArray { r.flags.forEach { add(JsonPrimitive(it.key)) } })
        put("comment", r.comment)
    },
)

/** Workout done [vDone, sumCard]: a celebration card, the muscles, the session, and what changes next time. */
@Composable
fun DoneContent(ui: DoneUi, onWeek: () -> Unit) {
    val c = AppTheme.colors
    Page {
        H1("Workout done")
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(c.tintMint, c.accentSoft), start = Offset.Zero, end = Offset(900f, 700f))),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(c.ok.copy(alpha = 0.10f), radius = size.height * 0.6f, center = Offset(size.width * 0.95f, -size.height * 0.1f))
                drawCircle(c.accent.copy(alpha = 0.08f), radius = size.height * 0.4f, center = Offset(size.width * 0.8f, size.height * 1.0f))
            }
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Check, null, Modifier.size(20.dp), tint = c.bg)
                    }
                    Text("Well done", color = c.fg, style = AppTheme.type.h2)
                }
                ui.totalKg?.let { kg ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("$kg", Modifier.alignByBaseline(), color = c.fg, style = AppTheme.type.tabular(AppTheme.type.big))
                        Text(
                            "kg volume load, weighted exercises",
                            Modifier.alignByBaseline().weight(1f),
                            color = c.fg,
                            style = AppTheme.type.body.copy(fontSize = 17.sp),
                        )
                    }
                    ui.kgComparison?.let { Small(it, Modifier.padding(top = 4.dp), color = c.fg) }
                }
                ui.repsLine?.let { line ->
                    val n = line.substringBefore(' ')
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(n) }
                            append(line.removePrefix(n))
                        },
                        Modifier.padding(top = 8.dp),
                        color = c.fg,
                        style = AppTheme.type.body,
                    )
                }
            }
        }
        if (ui.muscles.isNotEmpty()) {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.soft).padding(horizontal = 16.dp, vertical = 6.dp)) {
                ui.muscles.forEachIndexed { i, m ->
                    LineRow(divider = i < ui.muscles.lastIndex, left = {
                        Body(m.muscle)
                        m.comparison?.let { Small(it) }
                    }) {
                        Text(m.value, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                    }
                }
            }
        }
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.soft).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SessionCard(ui.card)
        }
        H2("What changes next time", top = 28.dp)
        ui.changes.forEachIndexed { i, ch ->
            Column(
                Modifier.fadeLiftIn(minOf(i, 6) * 50).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(if (ch.up) c.tintMint else c.soft).padding(16.dp),
            ) {
                Body(ch.name, strong = true)
                Small(ch.summary)
                Body(ch.message, Modifier.padding(top = 6.dp))
                Text(
                    ch.next,
                    Modifier.padding(top = 6.dp),
                    color = if (ch.up) c.ok else c.fg,
                    style = if (ch.up) AppTheme.type.bodyStrong else AppTheme.type.body,
                )
            }
        }
        AccentButton("See your week", onWeek, Modifier.padding(top = 18.dp))
    }
}
