package com.homeworkoutcoach.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.Tasks
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
import java.time.LocalDate
import java.time.LocalTime

class PlannerViewModel(
    repo: TaskRepository,
    private val actions: PlannerActions,
    presenter: PlannerPresenter = PlannerPresenter(),
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    /** The day being looked at; null means today, so a screen left open past midnight moves on with the date. */
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** Null until loaded. */
    val ui: StateFlow<PlannerUi?> = combine(repo.tasks(), selected, todayFlow(clock)) { all, day, today ->
        presenter.present(all, day ?: today, today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val shown get() = selected.value ?: clock()

    fun previousDay() = selected.update { (it ?: clock()).minusDays(1) }

    fun nextDay() = selected.update { (it ?: clock()).plusDays(1) }

    fun goToToday() {
        selected.value = null
    }

    /** Adds a task on the day shown. Returns what to fix, or null if it was added. */
    fun add(title: String): String? {
        Tasks.problem(title, "")?.let { return it }
        val day = shown
        viewModelScope.launch { actions.add(title, day) }
        return null
    }

    fun toggle(id: String) {
        viewModelScope.launch { actions.toggle(id) }
    }

    fun moveToTomorrow(id: String) {
        viewModelScope.launch { actions.postpone(id, clock()) }
    }

    fun moveToToday(id: String) {
        viewModelScope.launch { actions.moveToToday(id, clock()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { actions.delete(id) }
    }
}

class TaskEditViewModel(
    private val id: String,
    private val repo: TaskRepository,
    private val actions: PlannerActions,
) : ViewModel() {
    data class State(
        val title: String,
        val note: String,
        val date: LocalDate,
        val time: LocalTime?,
        /** The first problem is shown only after trying to save. */
        val showProblem: Boolean = false,
    ) {
        val problem: String? get() = Tasks.problem(title, note)
    }

    private val _state = MutableStateFlow<State?>(null)

    /** Null until loaded, and when the task no longer exists. */
    val state: StateFlow<State?> = _state

    init {
        viewModelScope.launch {
            repo.tasks().first().firstOrNull { it.id == id }?.let { _state.value = State(it.title, it.note, it.date, it.time) }
        }
    }

    fun edit(transform: (State) -> State) = _state.update { s -> s?.let(transform) }

    fun save(onDone: () -> Unit) {
        val s = _state.value ?: return
        if (s.problem != null) {
            _state.value = s.copy(showProblem = true)
            return
        }
        viewModelScope.launch {
            actions.update(id, s.title, s.note, s.date, s.time)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            actions.delete(id)
            onDone()
        }
    }
}
