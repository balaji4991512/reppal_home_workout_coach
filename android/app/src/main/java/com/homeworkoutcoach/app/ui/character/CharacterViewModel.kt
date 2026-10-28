package com.homeworkoutcoach.app.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.CharacterFocus
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.ReflectionRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.Character
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.Quality
import com.homeworkoutcoach.engine.Reflections
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.WeeklyReflection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** The Character screen: evidence from this week, a focus for it, and the end-of-week reflection. */
class CharacterViewModel(
    private val settings: SettingsRepository,
    training: TrainingRepository,
    habits: HabitRepository,
    routines: RoutineRepository,
    tasks: TaskRepository,
    journal: JournalRepository,
    focus: FocusRepository,
    private val reflections: ReflectionRepository,
    presenter: CharacterPresenter = CharacterPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private class Records(val settings: UserSettings, val workouts: List<Workout>, val habits: List<StoredHabit>, val entries: Map<String, Map<LocalDate, Int>>)

    private class More(val routines: List<Routine>, val done: Set<RoutineDone>, val tasks: List<Task>, val journal: List<JournalEntry>)

    private class Rest(val focus: List<FocusSession>, val reflections: List<WeeklyReflection>, val today: LocalDate)

    private val records = combine(settings.settings, training.workouts(), habits.habits(), habits.entries(), ::Records)
    private val more = combine(routines.routines(), routines.done(), tasks.tasks(), journal.entries(), ::More)
    private val rest = combine(focus.sessions(), reflections.reflections(), todayFlow(clock), ::Rest)

    /** Null until loaded. */
    val ui: StateFlow<CharacterUi?> = combine(records, more, rest) { r, m, x ->
        val inputs = CharacterInputsBuilder.build(
            plannedWorkoutsPerWeek = r.settings.daysPerWeek.takeIf { r.settings.started },
            workouts = r.workouts,
            habits = r.habits,
            entries = r.entries,
            routines = m.routines,
            routineDone = m.done,
            tasks = m.tasks,
            journal = m.journal,
            focus = x.focus,
            today = x.today,
            zone = zone,
        )
        presenter.present(Character.week(inputs, x.today), r.settings.characterFocus, x.reflections)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val weekStart get() = Habits.weekStart(clock())

    /** Picks the quality to focus on this week. */
    fun chooseFocus(q: Quality) {
        val start = weekStart
        viewModelScope.launch { settings.update { it.copy(characterFocus = CharacterFocus(start.toEpochDay(), q.name)) } }
    }

    /** Saves this week's reflection. Returns what to fix, or null if it was saved. */
    fun saveReflection(wentWell: String, differently: String): String? {
        Reflections.problem(wentWell, differently)?.let { return it }
        val start = weekStart
        viewModelScope.launch { reflections.save(Reflections.create(start, wentWell, differently)) }
        return null
    }

    fun deleteReflection() {
        val start = weekStart
        viewModelScope.launch { reflections.delete(start) }
    }
}
