package com.homeworkoutcoach.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.focus.FocusSessions
import com.homeworkoutcoach.app.ui.habits.todayFlow
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class FocusViewModel(
    settings: SettingsRepository,
    private val sessions: FocusRepository,
    tasks: TaskRepository,
    private val service: FocusSessions,
    presenter: FocusPresenter = FocusPresenter(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {
    /** The session that just ended, shown until the user starts another or dismisses it. */
    private val _finished = MutableStateFlow<FocusSession?>(null)
    val finished: StateFlow<FocusSession?> = _finished

    /** Null until loaded. */
    val ui: StateFlow<FocusUi?> = combine(
        settings.settings.map { it.activeFocus }.distinctUntilChanged(), sessions.sessions(), tasks.tasks(), todayFlow(clock),
    ) { active, list, taskList, today ->
        presenter.present(active, list, taskList, today, zone)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // A session may have run out while the app was closed.
        viewModelScope.launch { service.settleIfFinished()?.let { _finished.value = it } }
    }

    fun start(minutes: Int, taskTitle: String?) {
        viewModelScope.launch {
            _finished.value = null
            service.start(minutes, taskTitle)
        }
    }

    /** Ends the session now; the time spent so far is kept. */
    fun stop() {
        viewModelScope.launch { service.stop() }
    }

    /**
     * The countdown reached zero. The alarm may have settled the session already, so if there is nothing left to settle
     * the saved session (made from the start time) is shown.
     */
    fun settle(startMillis: Long) {
        viewModelScope.launch {
            val saved = service.settleIfFinished() ?: sessions.sessions().first().firstOrNull { it.id == "focus-$startMillis" }
            if (saved != null) _finished.value = saved
        }
    }

    fun dismissFinished() {
        _finished.value = null
    }
}
