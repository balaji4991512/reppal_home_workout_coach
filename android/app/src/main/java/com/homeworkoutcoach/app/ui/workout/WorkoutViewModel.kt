package com.homeworkoutcoach.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.WorkoutSession
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.voice.VoiceError
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Progression
import com.homeworkoutcoach.engine.SetCheck
import com.homeworkoutcoach.engine.SetParser
import com.homeworkoutcoach.engine.SetRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The set being entered. Chips build the record at once; typed text needs "Check my note". */
data class SetInput(
    val text: String = "",
    val reps: Int? = null,
    /** Null shows "Just right" selected. */
    val effort: Effort? = null,
    val pain: Boolean = false,
    val preview: SetRecord? = null,
    val error: String? = null,
    /** The microphone is open; [text] shows what has been heard so far. */
    val listening: Boolean = false,
)

sealed interface WorkoutState {
    data object Loading : WorkoutState

    /** Nothing to train today (no exercises for this day). */
    data object Empty : WorkoutState

    data class Logging(val session: WorkoutSession, val input: SetInput, val note: String?) : WorkoutState

    /** Saving the finished workout. */
    data object Saving : WorkoutState

    data class Done(val ui: DoneUi) : WorkoutState
}

class WorkoutViewModel(
    private val lib: Library,
    private val planner: Planner,
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val actions: PlanActions,
    private val parser: SetParser,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Loading)
    val state: StateFlow<WorkoutState> = _state

    init {
        viewModelScope.launch { _state.value = startToday() }
    }

    /** Today's day of the plan with the current prescriptions [start]. */
    private suspend fun startToday(): WorkoutState {
        val s = settings.settings.first()
        val ps = s.planSettings(training.swaps().first())
        val stored = training.exerciseStates().first()
        val seq = planner.weekSequence(ps)
        val day = planner.buildDay(seq[s.sessionIndex % seq.size], ps)
        if (day.items.isEmpty()) return WorkoutState.Empty
        val session = WorkoutSession.start(lib, ps.equipment, day) { id ->
            Progression.resolveState(lib.exercise(id), stored[id], ps.equipment, ps.level)
        }
        return WorkoutState.Logging(session, SetInput(), note = null)
    }

    private fun editLogging(transform: (WorkoutState.Logging) -> WorkoutState.Logging) =
        _state.update { if (it is WorkoutState.Logging) transform(it) else it }

    fun onText(text: String) = editLogging { it.copy(input = it.input.copy(text = text)) }

    /** Tapping the selected chip again clears it [qs]. A rep count builds the record straight away. */
    fun onReps(n: Int) = editLogging { l ->
        val input = l.input.copy(reps = if (l.input.reps == n) null else n)
        l.copy(input = if (input.reps != null) read(l.session, input) else input.copy(preview = null, error = null))
    }

    fun onEffort(e: Effort) = editLogging { l ->
        val input = l.input.copy(effort = if (l.input.effort == e) null else e)
        l.copy(input = if (input.reps != null) read(l.session, input) else input.copy(preview = null))
    }

    fun onPain() = editLogging { l ->
        val input = l.input.copy(pain = !l.input.pain)
        l.copy(input = if (input.reps != null) read(l.session, input) else input)
    }

    /** "Check my note" for typed or spoken text. */
    fun onCheck() = editLogging { l -> l.copy(input = read(l.session, l.input)) }

    fun onVoiceListening() = editLogging { l ->
        l.copy(input = l.input.copy(listening = true, text = "", preview = null, error = null))
    }

    fun onVoicePartial(text: String) = editLogging { l -> l.copy(input = l.input.copy(text = text)) }

    /** A spoken phrase is checked at once [mic]. The chips are cleared so the spoken words are what count. */
    fun onVoiceResult(text: String) = editLogging { l -> l.copy(input = read(l.session, SetInput(text = text))) }

    fun onVoiceError(error: VoiceError) = editLogging { l ->
        l.copy(
            input = l.input.copy(
                listening = false,
                error = when (error) {
                    VoiceError.NOT_AVAILABLE -> "Voice input isn’t available on this phone. Type instead."
                    VoiceError.NO_PERMISSION -> "The microphone is off for this app. Type instead, or allow it in Settings."
                    VoiceError.NOT_HEARD -> "Couldn’t hear that. Try again or type it."
                    VoiceError.FAILED -> "Voice input stopped. Try again or type it."
                },
            ),
        )
    }

    /** Parse the chips (if a rep count is chosen) or the typed text, and check it against this exercise [read]. */
    private fun read(session: WorkoutSession, input: SetInput): SetInput {
        val text = if (input.reps != null) {
            "${input.reps} reps, ${(input.effort ?: Effort.JUST_RIGHT).key}" + if (input.pain) ", pain" else ""
        } else input.text.trim()
        if (text.isEmpty()) return input.copy(preview = null, error = "Say or type how the set went.")
        return when (val c = session.check(parser.parse(text))) {
            is SetCheck.Ok -> input.copy(preview = c.record, error = null)
            SetCheck.NoReps -> input.copy(preview = null, error = "I couldn’t find the rep count. Try again, like “10 reps, hard”.")
            is SetCheck.WeightNotOwned -> input.copy(
                preview = null,
                error = "You don’t have a ${Copy.kg(c.kg)} kg dumbbell. Try again or edit your equipment.",
            )
        }
    }

    /** Save the previewed set; after the last one, save the workout and show the summary [confirmSet]. */
    fun onSave() {
        val l = _state.value as? WorkoutState.Logging ?: return
        val record = l.input.preview ?: return
        val cp = l.session.target
        val e = l.session.exercise
        val next = l.session.save(record)
        if (!next.finished) {
            val note = next.lastAdjustment?.let { Copy.liveNote(e, cp, it) }
            _state.value = WorkoutState.Logging(next, SetInput(), note)
            return
        }
        _state.value = WorkoutState.Saving
        viewModelScope.launch {
            val history = training.workouts().first()
            val result = next.finish(now())
            actions.finishWorkout(result)
            _state.value = WorkoutState.Done(DonePresenter(lib).present(result, history))
        }
    }
}
