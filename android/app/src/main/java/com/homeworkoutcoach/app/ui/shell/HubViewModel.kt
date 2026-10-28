package com.homeworkoutcoach.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.GoalRepository
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.character.CharacterInputsBuilder
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.Quality
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The live lines and top numbers on the Train, Plan, Reflect and Me pages. */
class HubViewModel(
    settings: SettingsRepository,
    training: TrainingRepository,
    habits: HabitRepository,
    routines: RoutineRepository,
    tasks: TaskRepository,
    journal: JournalRepository,
    focus: FocusRepository,
    goals: GoalRepository,
    body: BodyRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private class A(val settings: UserSettings, val workouts: List<Workout>, val habits: List<StoredHabit>, val entries: Map<String, Map<LocalDate, Int>>)

    private class B(val routines: List<Routine>, val done: Set<RoutineDone>, val tasks: List<Task>, val journal: List<JournalEntry>)

    private class C(val focus: List<FocusSession>, val goals: List<Goal>, val weights: List<BodyWeight>, val today: LocalDate)

    private val a = combine(settings.settings, training.workouts(), habits.habits(), habits.entries(), ::A)
    private val b = combine(routines.routines(), routines.done(), tasks.tasks(), journal.entries(), ::B)
    private val c = combine(focus.sessions(), goals.goals(), body.weights(), todayFlow(clock), ::C)

    /** Null until loaded. */
    val ui: StateFlow<Map<Tab, HubUi>?> = combine(a, b, c) { a, b, c ->
        val inputs = CharacterInputsBuilder.build(
            plannedWorkoutsPerWeek = a.settings.daysPerWeek.takeIf { a.settings.started },
            workouts = a.workouts, habits = a.habits, entries = a.entries,
            routines = b.routines, routineDone = b.done, tasks = b.tasks, journal = b.journal,
            focus = c.focus, today = c.today, zone = zone,
        )
        val thisWeek = Habits.weekStart(c.today).toEpochDay()
        val focusLabel = a.settings.characterFocus?.takeIf { it.weekStartEpochDay == thisWeek }
            ?.let { runCatching { Quality.valueOf(it.quality).label }.getOrNull() }
        val hub = HubInputs(
            started = a.settings.started, level = a.settings.level, daysPerWeek = a.settings.daysPerWeek,
            inputs = inputs, goals = c.goals,
            latestWeight = c.weights.lastOrNull()?.let { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() to it.weightKg },
            characterFocus = focusLabel, today = c.today,
        )
        Tab.entries.associateWith { HubPresenter.present(it, hub) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
