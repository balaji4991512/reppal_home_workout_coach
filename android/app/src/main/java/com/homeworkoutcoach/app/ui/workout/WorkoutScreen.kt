package com.homeworkoutcoach.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.AccentChip
import com.homeworkoutcoach.app.ui.components.AccentButton
import com.homeworkoutcoach.app.ui.components.DeleteAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.homeworkoutcoach.app.ui.components.TextLink
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
    onMakeUp: (Long) -> Unit = {},
    /** Opens how to do the exercise with this id. */
    onHowTo: (String) -> Unit = {},
    vm: WorkoutViewModel = viewModel(factory = Factories.Workout),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val logging = state is WorkoutState.Logging || state is WorkoutState.Saving
    LaunchedEffect(logging) { onActive(logging) }
    BackHandler(enabled = logging, onBack = onLeaveRequest)

    when (val s = state) {
        WorkoutState.Loading, WorkoutState.Saving -> Unit
        WorkoutState.Empty -> LaunchedEffect(Unit) { onWeek() }
        is WorkoutState.NotToday -> NotTodayContent(s.title, s.note, s.makeUps, onWeek, onMakeUp)
        is WorkoutState.Logging -> LoggingContent(
            s,
            onText = vm::onText,
            onReps = vm::onReps,
            onEffort = vm::onEffort,
            onPain = vm::onPain,
            onWeight = vm::onWeight,
            onCheck = vm::onCheck,
            onSave = vm::onSave,
            voice = VoiceHandlers(vm::onVoiceListening, vm::onVoicePartial, vm::onVoiceResult, vm::onVoiceError),
            onHowTo = onHowTo,
            controls = WorkoutControls(
                goTo = vm::goTo, editSet = vm::editSet, deleteSet = vm::deleteSet, undoPain = vm::undoPain,
                skip = vm::skip, unskip = vm::unskip, finish = vm::finishWorkout,
                replacements = vm::replacements, replace = vm::replace,
            ),
        )
        is WorkoutState.Done -> DoneContent(s.ui, onWeek)
    }
}

/** Rest day, or today's workout already done: nothing to start, and where to look instead. */
@Composable
private fun NotTodayContent(title: String, note: String, makeUps: List<MakeUpOption>, onWeek: () -> Unit, onMakeUp: (Long) -> Unit) {
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
        if (makeUps.isNotEmpty()) {
            H2("Skipped this week", top = 24.dp)
            Small("You can still make these up before the week ends.")
            makeUps.forEach { m ->
                OutlineButton("Make up ${m.label}", { onMakeUp(m.epochDay) }, tint = c.accent)
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

/** Moving about the workout and changing it while it runs: other exercises, set corrections, skip and replace. */
class WorkoutControls(
    val goTo: (Int) -> Unit = {},
    val editSet: (Int, Int, Double, Effort) -> Unit = { _, _, _, _ -> },
    val deleteSet: (Int) -> Unit = {},
    val undoPain: () -> Unit = {},
    val skip: () -> Unit = {},
    val unskip: () -> Unit = {},
    val finish: () -> Unit = {},
    val replacements: () -> List<Pair<String, String>> = { emptyList() },
    val replace: (String, Boolean) -> Unit = { _, _ -> },
)

/** One exercise at a time [vLog]. */
@OptIn(ExperimentalMaterial3Api::class)
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
    onWeight: (Double) -> Unit = {},
    onHowTo: (String) -> Unit = {},
    controls: WorkoutControls = WorkoutControls(),
) {
    val c = AppTheme.colors
    val session = state.session
    val e = session.exercise
    val target = session.target
    val input = state.input
    // The sheet open, if any: the exercise list, a set being corrected, or the replacements.
    var listOpen by remember { mutableStateOf(false) }
    var editing by remember(session.index, e.id) { mutableStateOf<Int?>(null) }
    var replacing by remember(session.index, e.id) { mutableStateOf(false) }

    if (listOpen) ExerciseListSheet(session, onPick = { listOpen = false; controls.goTo(it) }, onClose = { listOpen = false })
    editing?.let { i ->
        session.logged.getOrNull(i)?.let { set ->
            EditSetSheet(
                session, i, set,
                onSave = { reps, kg, effort -> editing = null; controls.editSet(i, reps, kg, effort) },
                onDelete = { editing = null; controls.deleteSet(i) },
                onClose = { editing = null },
            )
        }
    }
    if (replacing) {
        ReplaceSheet(
            name = e.name, logged = session.logged.size, options = remember(session.index, e.id) { controls.replacements() },
            onPick = { id, keep -> replacing = false; controls.replace(id, keep) },
            onClose = { replacing = false },
        )
    }

    val complete = session.complete(session.index)
    Page {
        ProgressDots(session)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(AppIcons.ChevronLeft, "Previous exercise", enabled = session.index > 0) { controls.goTo(session.index - 1) }
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Show all exercises") { listOpen = true },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Muted("${session.slot}, exercise ${session.index + 1} of ${session.day.items.size}")
                Icon(AppIcons.ChevronDown, null, Modifier.padding(start = 4.dp).size(16.dp), tint = c.mut)
            }
            NavArrow(AppIcons.ChevronRight, "Next exercise", enabled = session.index < session.day.items.lastIndex) { controls.goTo(session.index + 1) }
        }
        H1(e.name)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TextLink("How to do it", { onHowTo(e.id) })
            if (!complete || session.logged.isEmpty()) TextLink("Replace", { replacing = true })
            if (!complete && session.canSkip) TextLink("Skip", controls.skip)
        }

        // The target in a card, with the sets under it.
        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 600f)))
                .padding(20.dp),
        ) {
            if (!complete) {
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
            } else {
                Text(
                    when {
                        session.isSkipped(session.index) -> "SKIPPED"
                        e.id in session.pain -> "STOPPED FOR PAIN"
                        else -> "ALL SETS DONE"
                    },
                    color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold),
                )
            }
            SetPills(session) { editing = it }
            if (session.logged.isNotEmpty()) Small("Tap a done set to change or delete it.", Modifier.padding(top = 10.dp))
        }

        if (complete) {
            when {
                session.isSkipped(session.index) -> {
                    Body("Left out of this workout. Its plan stays the same for next time.", Modifier.padding(top = 14.dp))
                    OutlineButton("Do it after all", controls.unskip, tint = c.accent)
                }
                e.id in session.pain -> {
                    Body("You reported pain, so this exercise stops here and leaves your plan when you finish.", Modifier.padding(top = 14.dp))
                    OutlineButton("Pain was a mistake, carry on", controls.undoPain, tint = c.accent)
                }
            }
            if (session.allComplete) {
                AccentButton("Finish workout", controls.finish, Modifier.padding(top = 18.dp), icon = AppIcons.Check)
            } else {
                val next = session.nextOpen()
                AccentButton("Next: ${session.lib.exercise(session.day.items[next].exerciseId).name}", { controls.goTo(next) }, Modifier.padding(top = 18.dp))
            }
            return@Page
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

        if (!e.bodyweight) {
            val kg = input.shownWeight(target.weightKg)
            Muted("Weight you used", Modifier.padding(top = 10.dp))
            Chips {
                weightChoices(session.equipment.dumbbells, target.weightKg, kg).forEach { w ->
                    AccentChip("${Copy.kg(w)} kg", kotlin.math.abs(w - kg) < 1e-9, { onWeight(w) }, Modifier.widthIn(min = 64.dp))
                }
            }
        }
        Muted("Reps you did", Modifier.padding(top = 10.dp))
        Chips {
            repChoices(target.targetReps).forEach { n ->
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

/**
 * The weights to offer for a set: the owned dumbbells nearest the [target], two either side of it (more on one side
 * near the ends of the rack), and the [shown] weight too if it is not among them.
 */
internal fun weightChoices(owned: List<Double>, target: Double, shown: Double): List<Double> {
    val all = (owned + target + shown).distinct().sorted()
    val at = all.indexOfFirst { kotlin.math.abs(it - target) < 1e-9 }
    val from = (at - 2).coerceIn(0, maxOf(0, all.size - 5))
    return (all.subList(from, minOf(all.size, from + 5)) + shown).distinct().sorted()
}

/** The reps to offer: two over the [target] down to three under, at least 1. */
internal fun repChoices(target: Int): List<Int> = (target + 2 downTo target - 3).filter { it >= 1 }

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) c.fg else c.bd) }
}

/** Every exercise of the workout with how far it got; tapping one opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseListSheet(session: WorkoutSession, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val c = AppTheme.colors
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            H2("Exercises", top = 0.dp)
            session.day.items.forEachIndexed { i, item ->
                val id = item.exerciseId
                val done = session.sets[id].orEmpty().size
                val status = when {
                    session.isSkipped(i) && done > 0 -> "Skipped after ${if (done == 1) "1 set" else "$done sets"}"
                    session.isSkipped(i) -> "Skipped"
                    id in session.pain -> "Stopped for pain"
                    done >= session.plan[i].sets -> "Done, $done sets"
                    done > 0 -> "$done of ${session.plan[i].sets} sets"
                    else -> "Not started"
                }
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (i == session.index) c.accentSoft else c.soft)
                        .clickable(role = Role.Button) { onPick(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(if (session.complete(i) && !session.isSkipped(i)) c.ok else c.bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (session.complete(i) && !session.isSkipped(i)) Icon(AppIcons.Check, null, Modifier.size(16.dp), tint = c.bg)
                        else Text("${i + 1}", color = c.mut, style = AppTheme.type.small)
                    }
                    Column(Modifier.weight(1f)) {
                        Body(session.lib.exercise(id).name, strong = i == session.index)
                        Small(status)
                    }
                }
            }
        }
    }
}

/** Correcting one logged set: its reps, weight and how it felt, or taking it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(
    session: WorkoutSession,
    i: Int,
    set: com.homeworkoutcoach.engine.LoggedSet,
    onSave: (Int, Double, Effort) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val c = AppTheme.colors
    val e = session.exercise
    var reps by remember(i) { mutableStateOf(set.reps) }
    var kg by remember(i) { mutableStateOf(set.weightKg) }
    var effort by remember(i) { mutableStateOf(set.effort) }
    val weights = (session.equipment.dumbbells + set.weightKg).distinct().sorted()
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            H2("Set ${i + 1}", top = 0.dp)
            Small("${e.name}. Target was ${set.targetReps} reps.")
            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Body("Reps", Modifier.weight(1f))
                Stepper("$reps", "reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(60) })
            }
            if (!e.bodyweight && weights.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Body("Weight", Modifier.weight(1f))
                    val at = weights.indexOfFirst { kotlin.math.abs(it - kg) < 1e-9 }.coerceAtLeast(0)
                    Stepper("${Copy.kg(kg)} kg", "weight", { kg = weights[(at - 1).coerceAtLeast(0)] }, { kg = weights[(at + 1).coerceAtMost(weights.lastIndex)] })
                }
            }
            Muted("How it felt", Modifier.padding(top = 14.dp))
            Chips {
                listOf(Effort.EASY to "Easy", Effort.JUST_RIGHT to "Just right", Effort.HARD to "Hard").forEach { (k, label) ->
                    AccentChip(label, effort == k, { effort = k })
                }
            }
            AccentButton("Save changes", { onSave(reps, kg, effort) }, Modifier.padding(top = 18.dp))
            DeleteAction("Delete this set", "Delete set ${i + 1}? You can do it again.", onDelete, gap = 12.dp)
        }
    }
}

/** What the exercise shown can be swapped for, this workout only or from now on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceSheet(name: String, logged: Int, options: List<Pair<String, String>>, onPick: (String, Boolean) -> Unit, onClose: () -> Unit) {
    val c = AppTheme.colors
    var keep by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.bg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            H2("Replace $name", top = 0.dp)
            if (options.isEmpty()) {
                Body("Nothing else your equipment allows works the same muscle.", Modifier.padding(top = 8.dp))
                return@Column
            }
            Small("Same muscle, with your equipment.")
            if (logged > 0) {
                Text(
                    "The ${if (logged == 1) "set" else "$logged sets"} logged on $name will be dropped.",
                    Modifier.padding(top = 8.dp), color = c.bad, style = AppTheme.type.small,
                )
            }
            Chips(Modifier.padding(top = 10.dp)) {
                AccentChip("This workout only", !keep, { keep = false }, small = true)
                AccentChip("From now on", keep, { keep = true }, small = true)
            }
            options.forEach { (id, label) ->
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(c.soft)
                        .clickable(role = Role.Button) { onPick(id, keep) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Body(label, Modifier.weight(1f))
                    Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.mut)
                }
            }
        }
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
                        i == session.index -> c.accent.copy(alpha = 0.45f)
                        session.isSkipped(i) -> c.mut.copy(alpha = 0.35f)
                        session.complete(i) -> c.accent
                        else -> c.bd
                    },
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetPills(session: WorkoutSession, onEdit: (Int) -> Unit) {
    val c = AppTheme.colors
    val done = session.repsDone
    FlowRow(
        Modifier.padding(top = 16.dp).semantics { contentDescription = "Sets" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val open = !session.complete(session.index)
        repeat(maxOf(session.planned.sets, done.size)) { i ->
            val isDone = i < done.size
            val current = open && !isDone && i == session.setIndex
            if (!isDone && !open) return@repeat
            Box(
                Modifier.widthIn(min = 60.dp).height(44.dp).clip(RoundedCornerShape(99.dp))
                    .background(if (isDone) c.ok else if (current) c.accent else c.bg.copy(alpha = 0.7f))
                    .then(if (isDone) Modifier.clickable(role = Role.Button, onClickLabel = "Change set ${i + 1}") { onEdit(i) } else Modifier)
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
