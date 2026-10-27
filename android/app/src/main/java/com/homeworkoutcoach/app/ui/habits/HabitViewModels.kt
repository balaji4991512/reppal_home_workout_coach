package com.homeworkoutcoach.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Habits
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
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Today's date, checked once a minute so a screen left open past midnight moves on to the new day. */
internal fun todayFlow(clock: () -> LocalDate): Flow<LocalDate> = flow {
    while (true) {
        emit(clock())
        delay(60_000)
    }
}.distinctUntilChanged()

/** What makes the self-ticking habits tick: the days with a workout, and the days the routines were finished. */
internal data class AutoInputs(val workoutDays: Set<LocalDate>, val routineDays: Map<AutoSource, Set<LocalDate>>)

internal fun autoInputs(training: TrainingRepository, routines: RoutineRepository, zone: ZoneId): Flow<AutoInputs> =
    combine(training.workouts(), routines.routines(), routines.done()) { workouts, list, done ->
        AutoInputs(HabitLogs.workoutDays(workouts, zone), HabitLogs.routineDays(list, done))
    }

class HabitsViewModel(
    private val habits: HabitRepository,
    training: TrainingRepository,
    routines: RoutineRepository,
    presenter: HabitsPresenter = HabitsPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    /** Null until loaded. */
    val ui: StateFlow<HabitsUi?> = combine(
        habits.habits(), habits.entries(), autoInputs(training, routines, zone), todayFlow(clock),
    ) { list, entries, auto, today ->
        presenter.present(list, entries, auto.workoutDays, today, auto.routineDays)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val today = clock

    /** Sets today's amount: a tick (1), an untick (0) or a number. */
    fun record(row: HabitRowUi, amount: Int) {
        viewModelScope.launch { HabitWrites.record(habits, row.id, today(), amount, row.auto) }
    }
}

class HabitDetailViewModel(
    private val id: String,
    private val habits: HabitRepository,
    training: TrainingRepository,
    routines: RoutineRepository,
    presenter: HabitDetailPresenter = HabitDetailPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock()))

    /** Null until loaded, and when the habit no longer exists. */
    val ui: StateFlow<HabitDetailUi?> = combine(
        habits.habits(), habits.entries(), autoInputs(training, routines, zone), todayFlow(clock), month,
    ) { list, entries, auto, today, m ->
        list.firstOrNull { it.habit.id == id }?.let { presenter.present(it, entries, auto.workoutDays, today, m, auto.routineDays) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

    fun record(date: LocalDate, amount: Int) {
        val auto = ui.value?.row?.auto ?: return
        viewModelScope.launch { HabitWrites.record(habits, id, date, amount, auto) }
    }
}

class HabitEditViewModel(
    private val id: String?,
    private val habits: HabitRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    data class State(
        val draft: HabitDraft,
        /** Names of the other habits, to catch a repeat. */
        val others: List<String>,
        /** Starting suggestions not already added; only offered for a new habit. */
        val suggestions: List<Habits.Suggestion>,
        val nextPosition: Int,
        /** The first problem is shown only after trying to save. */
        val showProblem: Boolean = false,
    ) {
        val problem: String? get() = draft.problem(others)
    }

    private val _state = MutableStateFlow<State?>(null)

    /** Null until loaded, and when an edited habit no longer exists. */
    val state: StateFlow<State?> = _state

    init {
        viewModelScope.launch {
            val all = habits.habits().first()
            val existing = id?.let { wanted -> all.firstOrNull { it.habit.id == wanted } }
            if (id != null && existing == null) return@launch
            val names = all.filter { it.habit.id != id }.map { it.habit.name }
            _state.value = State(
                draft = existing?.let(HabitDraft::from) ?: HabitDraft(),
                others = names,
                suggestions = if (id == null) Habits.suggestions.filter { s -> names.none { it.equals(s.name, ignoreCase = true) } } else emptyList(),
                nextPosition = (all.maxOfOrNull { it.position } ?: -1) + 1,
            )
        }
    }

    fun edit(transform: (HabitDraft) -> HabitDraft) = _state.update { s -> s?.copy(draft = transform(s.draft)) }

    /** Fills the form from a starting suggestion. */
    fun pick(s: Habits.Suggestion) = _state.update { it?.copy(draft = HabitDraft.from(s), showProblem = false) }

    fun save(onDone: () -> Unit) {
        val s = _state.value ?: return
        if (s.problem != null) {
            _state.value = s.copy(showProblem = true)
            return
        }
        viewModelScope.launch {
            habits.save(s.draft.toStored(UUID.randomUUID().toString(), clock(), s.nextPosition))
            onDone()
        }
    }

    /** Hides the habit and keeps its history, or brings an archived one back. */
    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        if (_state.value == null) return
        val habitId = id ?: return
        viewModelScope.launch {
            val stored = habits.habits().first().firstOrNull { it.habit.id == habitId } ?: return@launch
            habits.save(stored.copy(habit = stored.habit.copy(archived = archived)))
            onDone()
        }
    }

    /** Removes the habit and all its history. */
    fun delete(onDone: () -> Unit) {
        val habitId = id ?: return
        viewModelScope.launch {
            habits.delete(habitId)
            onDone()
        }
    }
}
