package com.homeworkoutcoach.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.plan.WorkoutEditDraft
import com.homeworkoutcoach.app.plan.WorkoutEdits
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.FormButtons
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Correcting a saved workout: the draft being edited, and saving it with the new next levels. */
class WorkoutEditViewModel(
    private val id: Long,
    private val lib: Library,
    planner: Planner,
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _draft = MutableStateFlow<WorkoutEditDraft?>(null)

    /** Null until loaded, and when the workout no longer exists. */
    val draft: StateFlow<WorkoutEditDraft?> = _draft

    init {
        viewModelScope.launch {
            val w = training.workouts().first().firstOrNull { it.id == id } ?: return@launch
            val s = settings.settings.first()
            _draft.value = WorkoutEditDraft.from(lib, w, planner.dayName(w.label) + ", " + Copy.weekdayDayMonth(w.dateMillis, zone), s.dumbbellsKg)
        }
    }

    fun reps(ex: Int, set: Int, delta: Int) = _draft.update { it?.reps(ex, set, delta) }
    fun weight(ex: Int, set: Int, delta: Int) = _draft.update { it?.weight(ex, set, delta) }
    fun addSet(ex: Int) = _draft.update { it?.addSet(ex) }
    fun removeLastSet(ex: Int) = _draft.update { it?.removeLastSet(ex) }

    /** Saves the corrected sets, and works the next level out again for each exercise that was edited and not done since. */
    fun save(onDone: () -> Unit) {
        val d = _draft.value ?: return
        if (!d.changed) return onDone()
        viewModelScope.launch {
            val all = training.workouts().first()
            val original = all.first { it.id == id }
            val edited = WorkoutEdits.withSets(original, d.sets())
            val changedIds = d.sets().filter { (ex, sets) -> sets != original.sets[ex] }.keys
            val later = all.filter { it.id != id && (it.dateMillis > original.dateMillis || (it.dateMillis == original.dateMillis && it.id > id)) }
            val equipment = settings.settings.first().equipment()
            training.updateWorkout(edited, WorkoutEdits.nextStates(lib, equipment, edited, later, only = changedIds))
            onDone()
        }
    }
}

/** Edit a saved workout: change the reps and weight of each set, add a set or take the last one off. */
@Composable
fun WorkoutEditScreen(
    id: Long,
    onDone: () -> Unit,
    vm: WorkoutEditViewModel = viewModel(key = "workout-edit-$id", factory = Factories.workoutEdit(id)),
) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val draft = d ?: return
    val c = AppTheme.colors
    Page {
        H1("Edit workout")
        Muted(draft.title)
        Small("Correct what you really did. Your next targets follow from it.", Modifier.padding(top = 2.dp))

        draft.exercises.forEachIndexed { ei, e ->
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.soft).padding(16.dp)) {
                Text(e.name, color = c.fg, style = AppTheme.type.h2)
                e.sets.forEachIndexed { si, s ->
                    Text("Set ${si + 1}", Modifier.padding(top = 14.dp), color = c.mut, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold))
                    Row(Modifier.padding(top = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Stepper("${s.reps} reps", "reps for set ${si + 1}", { vm.reps(ei, si, -1) }, { vm.reps(ei, si, +1) })
                        if (!e.bodyweight) Stepper("${Copy.kg(s.weightKg)} kg", "weight for set ${si + 1}", { vm.weight(ei, si, -1) }, { vm.weight(ei, si, +1) })
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { OutlineButton("Add a set", { vm.addSet(ei) }, tint = c.accent, compact = true) }
                    Box(Modifier.weight(1f)) { OutlineButton("Remove last set", { vm.removeLastSet(ei) }, compact = true) }
                }
            }
        }

        PrimaryButton("Save", { vm.save(onDone) })
        FormButtons(onDone)
    }
}

/** A value with a minus and a plus either side. */
@Composable
private fun Stepper(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("−", "Less $label", onMinus)
        Text(value, Modifier.widthIn(min = 60.dp), color = c.fg, textAlign = TextAlign.Center, style = AppTheme.type.tabular(AppTheme.type.bodyStrong.copy(fontSize = 15.sp)))
        StepButton("+", "More $label", onPlus)
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.accent, style = AppTheme.type.h2.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)) }
}
