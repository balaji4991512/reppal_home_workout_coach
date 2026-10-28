package com.homeworkoutcoach.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.FocusRepository
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
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.OverviewRange
import com.homeworkoutcoach.engine.Overviews
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** The top of the Progress screen: how the habits, workouts and focus time went over the chosen range. */
class OverviewViewModel(
    settings: SettingsRepository,
    training: TrainingRepository,
    habits: HabitRepository,
    routines: RoutineRepository,
    tasks: TaskRepository,
    journal: JournalRepository,
    focus: FocusRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    private class Records(val settings: UserSettings, val workouts: List<Workout>, val habits: List<StoredHabit>, val entries: Map<String, Map<LocalDate, Int>>)

    private class More(val routines: List<Routine>, val done: Set<RoutineDone>, val tasks: List<Task>, val journal: List<JournalEntry>)

    private class Rest(val focus: List<FocusSession>, val today: LocalDate, val range: OverviewRange)

    private val _range = MutableStateFlow(OverviewRange.WEEK)
    val range: StateFlow<OverviewRange> = _range

    private val records = combine(settings.settings, training.workouts(), habits.habits(), habits.entries(), ::Records)
    private val more = combine(routines.routines(), routines.done(), tasks.tasks(), journal.entries(), ::More)
    private val rest = combine(focus.sessions(), todayFlow(clock), _range, ::Rest)

    /** Null until loaded. */
    val ui: StateFlow<OverviewUi?> = combine(records, more, rest) { r, m, x ->
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
        OverviewPresenter.present(Overviews.compute(inputs, x.range, x.today), x.today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(range: OverviewRange) {
        _range.value = range
    }
}
