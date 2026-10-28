package com.homeworkoutcoach.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.GoalRepository
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.ui.habits.autoInputs
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.GoalKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class GoalsViewModel(
    goals: GoalRepository,
    habits: HabitRepository,
    training: TrainingRepository,
    routines: RoutineRepository,
    journal: JournalRepository,
    body: BodyRepository,
    presenter: GoalsPresenter = GoalsPresenter(),
    zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private class Records(
        val goals: List<com.homeworkoutcoach.engine.Goal>,
        val habits: List<StoredHabit>,
        val entries: Map<String, Map<LocalDate, Int>>,
    )

    private class Activity(
        val auto: com.homeworkoutcoach.app.ui.habits.AutoInputs,
        val weights: List<Pair<LocalDate, Double>>,
        val workoutDates: List<LocalDate>,
    )

    private val records = combine(goals.goals(), habits.habits(), habits.entries(), ::Records)
    private val activity = combine(autoInputs(training, routines, journal, zone), body.weights(), training.workouts()) { auto, weights, workouts ->
        Activity(
            auto,
            GoalInputs.firstWeighInPerDay(weights, zone),
            workouts.map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() },
        )
    }

    /** Null until loaded. */
    val ui: StateFlow<GoalsUi?> = combine(records, activity, todayFlow(clock)) { r, a, today ->
        val byId = r.habits.associateBy { it.habit.id }
        val doneDates = r.goals.mapNotNull { g -> g.habitId?.let { it to byId[it] } }.toMap().mapNotNull { (id, stored) ->
            stored?.let {
                val from = r.goals.filter { g -> g.habitId == id }.minOf { g -> g.startDate }
                id to GoalInputs.habitDoneDays(it.habit, r.entries[id].orEmpty(), a.auto.workoutDays, a.auto.sourceDays, from, today)
            }
        }.toMap()
        presenter.present(r.goals, r.habits.associate { it.habit.id to it.habit.name }, a.workoutDates, doneDates, a.weights, today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class GoalEditViewModel(
    private val id: String?,
    private val goals: GoalRepository,
    private val habits: HabitRepository,
    body: BodyRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    data class State(
        val draft: GoalDraft,
        /** Habits a goal can count (not archived). */
        val habits: List<StoredHabit>,
        /** The latest weigh-in's weight, if there is one. */
        val latestWeightKg: Double?,
        val today: LocalDate,
        /** The first problem is shown only after trying to save. */
        val showProblem: Boolean = false,
    ) {
        val habitName: String? get() = habits.firstOrNull { it.habit.id == draft.habitId }?.habit?.name
        val problem: String? get() = draft.problem(habitName, latestWeightKg, today)
    }

    private val _state = MutableStateFlow<State?>(null)

    /** Null until loaded, and when an edited goal no longer exists. */
    val state: StateFlow<State?> = _state

    private val weights = body

    init {
        viewModelScope.launch {
            val today = clock()
            val existing = id?.let { wanted -> goals.goals().first().firstOrNull { it.id == wanted } }
            if (id != null && existing == null) return@launch
            val latest = GoalInputs.firstWeighInPerDay(weights.weights().first(), zone).lastOrNull()?.second
            _state.value = State(
                draft = existing?.let(GoalDraft::from) ?: GoalDraft.new(today),
                habits = habits.habits().first().filterNot { it.habit.archived },
                latestWeightKg = latest,
                today = today,
            )
        }
    }

    fun edit(transform: (GoalDraft) -> GoalDraft) = _state.update { s -> s?.copy(draft = transform(s.draft)) }

    fun save(onDone: () -> Unit) {
        val s = _state.value ?: return
        if (s.problem != null) {
            _state.value = s.copy(showProblem = true)
            return
        }
        viewModelScope.launch {
            goals.save(s.draft.toGoal(UUID.randomUUID().toString(), s.habitName, s.latestWeightKg, s.today))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val goalId = id ?: return
        viewModelScope.launch {
            goals.delete(goalId)
            onDone()
        }
    }
}
