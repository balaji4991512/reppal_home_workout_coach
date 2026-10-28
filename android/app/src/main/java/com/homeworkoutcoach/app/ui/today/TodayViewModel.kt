package com.homeworkoutcoach.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.ui.habits.HabitRowUi
import com.homeworkoutcoach.app.ui.habits.HabitWrites
import com.homeworkoutcoach.app.ui.habits.HabitsPresenter
import com.homeworkoutcoach.app.ui.habits.autoInputs
import com.homeworkoutcoach.app.ui.planner.PlannerActions
import com.homeworkoutcoach.app.ui.planner.PlannerPresenter
import com.homeworkoutcoach.app.ui.routines.RoutinesPresenter
import com.homeworkoutcoach.app.ui.routines.nowFlow
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.engine.Journal
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The Today screen: the day's workout, routines, habits and tasks in one place. */
class TodayViewModel(
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val habits: HabitRepository,
    private val routines: RoutineRepository,
    private val journal: JournalRepository,
    tasks: TaskRepository,
    private val actions: PlanActions,
    private val plannerActions: PlannerActions,
    private val lib: Library,
    private val planner: Planner,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {
    private val weekPresenter = WeekPresenter(lib, planner)
    private val routinesPresenter = RoutinesPresenter()
    private val habitsPresenter = HabitsPresenter()
    private val tasksPresenter = PlannerPresenter()

    private class Plan(val settings: com.homeworkoutcoach.app.data.UserSettings, val states: Map<String, com.homeworkoutcoach.engine.ExerciseState>, val swaps: Map<String, String>, val workouts: List<com.homeworkoutcoach.app.data.Workout>)
    private class HabitData(val habits: List<com.homeworkoutcoach.app.data.StoredHabit>, val entries: Map<String, Map<LocalDate, Int>>, val auto: com.homeworkoutcoach.app.ui.habits.AutoInputs)
    private class RoutineData(
        val routines: List<com.homeworkoutcoach.engine.Routine>,
        val done: Set<com.homeworkoutcoach.engine.RoutineDone>,
        val journalDays: Set<LocalDate>,
    )

    private val plan = combine(settings.settings, training.exerciseStates(), training.swaps(), training.workouts(), ::Plan)
    private val habitData = combine(habits.habits(), habits.entries(), autoInputs(training, routines, journal, zone), ::HabitData)
    private val routineData = combine(routines.routines(), routines.done(), journal.entries()) { list, done, entries ->
        RoutineData(list, done, Journal.days(entries))
    }

    /** Null until loaded. */
    val ui: StateFlow<TodayUi?> = combine(plan, habitData, routineData, tasks.tasks(), nowFlow(clock)) { p, h, r, t, now ->
        val today = now.toLocalDate()
        val week = if (p.settings.started) weekPresenter.present(p.settings, p.states, p.swaps, p.workouts) else null
        val ui = TodayPresenter.present(
            name = p.settings.name,
            now = now,
            workout = WorkoutCardPresenter.present(p.settings, p.swaps, week, p.workouts, lib, planner, today, zone),
            routines = routinesPresenter.present(r.routines, r.done, today, now.toLocalTime()),
            habits = habitsPresenter.present(h.habits, h.entries, h.auto.workoutDays, today, h.auto.sourceDays),
            tasks = tasksPresenter.present(t, today, today),
            journalWritten = today in r.journalDays,
        )
        ui to week?.statesToStore.orEmpty()
    }
        .flowOn(Dispatchers.Default)
        // Keep starting weights fixed once first shown, as the Week screen does.
        .onEach { (_, states) -> actions.storeStates(states) }
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun today(): LocalDate = clock().toLocalDate()

    fun recordHabit(row: HabitRowUi, amount: Int) {
        viewModelScope.launch { HabitWrites.record(habits, row.id, today(), amount, row.auto) }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch { plannerActions.toggle(id) }
    }

    fun moveToTomorrow(id: String) {
        viewModelScope.launch { plannerActions.postpone(id, today()) }
    }

    fun moveToToday(id: String) {
        viewModelScope.launch { plannerActions.moveToToday(id, today()) }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { plannerActions.delete(id) }
    }
}
