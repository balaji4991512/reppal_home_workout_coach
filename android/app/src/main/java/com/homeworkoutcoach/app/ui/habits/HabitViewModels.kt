package com.homeworkoutcoach.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Journal
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
import kotlinx.coroutines.flow.map
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
data class AutoInputs(val workoutDays: Set<LocalDate>, val sourceDays: Map<AutoSource, Set<LocalDate>>)

fun autoInputs(training: TrainingRepository, routines: RoutineRepository, journal: JournalRepository, zone: ZoneId): Flow<AutoInputs> =
    combine(training.workouts(), routines.routines(), routines.done(), journal.entries()) { workouts, list, done, entries ->
        AutoInputs(HabitLogs.workoutDays(workouts, zone), HabitLogs.sourceDays(list, done, Journal.days(entries)))
    }

class HabitsViewModel(
    private val habits: HabitRepository,
    training: TrainingRepository,
    routines: RoutineRepository,
    journal: JournalRepository,
    presenter: HabitsPresenter = HabitsPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    /** The day being looked at; null means today, so a screen left open past midnight moves on with the date. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** Null until loaded. */
    val ui: StateFlow<HabitsUi?> = combine(
        habits.habits(), habits.entries(), autoInputs(training, routines, journal, zone), todayFlow(clock), selected,
    ) { list, entries, auto, today, day ->
        presenter.present(list, entries, auto.workoutDays, today, auto.sourceDays, shown = day ?: today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val today = clock
    private val shown get() = selected.value ?: today()

    /** Shows [date]; today is kept as "no choice" so the screen follows the date. */
    fun select(date: LocalDate) {
        selected.value = date.takeUnless { it == today() }
    }

    fun goToToday() {
        selected.value = null
    }

    fun previousWeek() = selected.update { (it ?: today()).minusWeeks(1) }

    /** A week on, but not into one that has not started: nothing is recorded ahead. */
    fun nextWeek() = selected.update { current ->
        val next = (current ?: today()).plusWeeks(1)
        if (Habits.weekStart(next).isAfter(today())) current else next.takeIf { it <= today() } ?: today()
    }

    /** Sets the shown day's amount: a tick (1), an untick (0) or a number. A day that has not happened is left alone. */
    fun record(row: HabitRowUi, amount: Int) {
        val day = shown
        if (day.isAfter(today())) return
        viewModelScope.launch { HabitWrites.record(habits, row.id, day, amount, row.auto) }
    }
}

class HabitDetailViewModel(
    private val id: String,
    private val habits: HabitRepository,
    training: TrainingRepository,
    routines: RoutineRepository,
    journal: JournalRepository,
    presenter: HabitDetailPresenter = HabitDetailPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock()))

    /** Null until loaded, and when the habit no longer exists. */
    val ui: StateFlow<HabitDetailUi?> = combine(
        habits.habits(), habits.entries(), autoInputs(training, routines, journal, zone), todayFlow(clock), month,
    ) { list, entries, auto, today, m ->
        list.firstOrNull { it.habit.id == id }?.let { presenter.present(it, entries, auto.workoutDays, today, m, auto.sourceDays) }
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

/** The one-time "tap the circle" hint: whether it has been dismissed (null until known), and dismissing it. */
class TickHintViewModel(private val settings: com.homeworkoutcoach.app.data.SettingsRepository) : ViewModel() {
    val seen: StateFlow<Boolean?> = settings.settings.map<com.homeworkoutcoach.app.data.UserSettings, Boolean?> { it.habitTickHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss() {
        viewModelScope.launch { settings.update { it.copy(habitTickHintSeen = true) } }
    }
}
