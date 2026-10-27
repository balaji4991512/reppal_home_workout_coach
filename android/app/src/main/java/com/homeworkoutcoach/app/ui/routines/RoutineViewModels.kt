package com.homeworkoutcoach.app.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.Routines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** The time, checked once a minute, but only passed on when the day or the part of the day changes. */
private fun nowFlow(clock: () -> LocalDateTime): Flow<LocalDateTime> = flow {
    while (true) {
        emit(clock())
        delay(60_000)
    }
}.distinctUntilChanged { a, b -> a.toLocalDate() == b.toLocalDate() && Routines.partOf(a.toLocalTime()) == Routines.partOf(b.toLocalTime()) }

class RoutinesViewModel(
    routines: RoutineRepository,
    presenter: RoutinesPresenter = RoutinesPresenter(),
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {
    /** Null until loaded. */
    val ui: StateFlow<RoutinesUi?> = combine(routines.routines(), routines.done(), nowFlow(clock)) { list, done, now ->
        presenter.present(list, done, now.toLocalDate(), now.toLocalTime())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class RoutineRunViewModel(
    private val id: String,
    private val routines: RoutineRepository,
    private val habits: HabitRepository,
    private val runner: RoutineRunner,
    clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    /** The ticks of this run. They live here, so turning the phone does not lose them. */
    private val run = MutableStateFlow(RoutineRunner.State())

    /** Null until loaded, and when the routine no longer exists. */
    val ui: StateFlow<RunUi?> = combine(routines.routines(), routines.done(), habits.habits(), run, todayFlow(clock)) { list, done, hs, state, today ->
        list.firstOrNull { it.id == id }?.let {
            RunPresenter.present(it, state.ticked, hs.associateBy { h -> h.habit.id }, doneToday = com.homeworkoutcoach.engine.RoutineDone(id, today) in done)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun routine(): Routine? = routines.routines().first().firstOrNull { it.id == id }

    fun toggle(itemId: String) {
        viewModelScope.launch {
            val r = routine() ?: return@launch
            val habitId = r.items.firstOrNull { it.id == itemId }?.habitId
            val habit = habitId?.let { h -> habits.habits().first().firstOrNull { it.habit.id == h }?.habit }
            run.value = runner.toggle(run.value, r, itemId, habit)
        }
    }

    /** Finishes the routine for today if every step is ticked, then calls [onDone]. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            val r = routine() ?: return@launch
            if (runner.finish(r, run.value)) onDone()
        }
    }

    fun undoFinish() {
        viewModelScope.launch { routine()?.let { runner.undoFinish(it) } }
    }
}

class RoutineEditViewModel(
    private val id: String?,
    private val routines: RoutineRepository,
    private val habits: HabitRepository,
) : ViewModel() {
    data class State(
        val draft: RoutineDraft,
        /** Names of the other routines, to catch a repeat. */
        val others: List<String>,
        /** Habits a step can be linked to (not archived). */
        val habits: List<StoredHabit>,
        /** The starting routines not already added; only offered for a new routine. */
        val suggestions: List<Routines.Suggestion>,
        val nextPosition: Int,
        /** The first problem is shown only after trying to save. */
        val showProblem: Boolean = false,
    ) {
        val problem: String? get() = draft.problem(others)
    }

    private val _state = MutableStateFlow<State?>(null)

    /** Null until loaded, and when an edited routine no longer exists. */
    val state: StateFlow<State?> = _state

    init {
        viewModelScope.launch {
            val all = routines.routines().first()
            val existing = id?.let { wanted -> all.firstOrNull { it.id == wanted } }
            if (id != null && existing == null) return@launch
            val names = all.filter { it.id != id }.map { it.name }
            _state.value = State(
                draft = existing?.let(RoutineDraft::from) ?: RoutineDraft(),
                others = names,
                habits = habits.habits().first().filterNot { it.habit.archived },
                suggestions = if (id == null) Routines.suggestions.filter { s -> names.none { it.equals(s.name, ignoreCase = true) } } else emptyList(),
                nextPosition = (all.maxOfOrNull { it.position } ?: -1) + 1,
            )
        }
    }

    fun edit(transform: (RoutineDraft) -> RoutineDraft) = _state.update { s -> s?.copy(draft = transform(s.draft)) }

    /** Fills the form from a starting suggestion. */
    fun pick(s: Routines.Suggestion) = _state.update { it?.copy(draft = RoutineDraft.from(s) { UUID.randomUUID().toString() }, showProblem = false) }

    fun addStep(title: String) = edit { it.addStep(UUID.randomUUID().toString(), title) }

    fun save(onDone: () -> Unit) {
        val s = _state.value ?: return
        if (s.problem != null) {
            _state.value = s.copy(showProblem = true)
            return
        }
        viewModelScope.launch {
            routines.save(s.draft.toRoutine(UUID.randomUUID().toString(), s.nextPosition))
            onDone()
        }
    }

    /** Removes the routine, its steps and the days it was finished. */
    fun delete(onDone: () -> Unit) {
        val routineId = id ?: return
        viewModelScope.launch {
            routines.delete(routineId)
            onDone()
        }
    }
}
