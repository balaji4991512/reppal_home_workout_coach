package com.homeworkoutcoach.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeworkoutcoach.app.HomeWorkoutCoachApp
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.SetupDraft
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.app.ui.week.WeekUi
import com.homeworkoutcoach.engine.ConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shell state: settings drive the theme, the start screen and the menu footer. Null until loaded. */
class AppViewModel(settings: SettingsRepository, private val actions: PlanActions) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { actions.setTheme(mode) }
    }
}

class WeekViewModel(
    settings: SettingsRepository,
    training: TrainingRepository,
    presenter: WeekPresenter,
    private val actions: PlanActions,
) : ViewModel() {
    val ui: StateFlow<WeekUi?> = combine(
        settings.settings, training.exerciseStates(), training.swaps(), training.workouts(),
    ) { s, states, swaps, workouts ->
        if (s.started) presenter.present(s, states, swaps, workouts) else null
    }
        .flowOn(Dispatchers.Default)
        // Keep starting weights fixed once first shown, as the prototype does.
        .onEach { ui -> ui?.let { actions.storeStates(it.statesToStore) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun swap(baseId: String, chosenId: String) {
        viewModelScope.launch { actions.swap(baseId, chosenId) }
    }
}

class SetupViewModel(
    private val settings: SettingsRepository,
    private val actions: PlanActions,
    private val concerns: ConcernMapper,
    private val library: Library,
) : ViewModel() {
    private val _draft = MutableStateFlow<SetupDraft?>(null)
    val draft: StateFlow<SetupDraft?> = _draft

    /** Whether a plan exists already: changes the heading and the button. */
    var editing = false
        private set

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            editing = s.started
            _draft.value = SetupDraft.from(s, Planner.DEFAULT_CUSTOM)
        }
    }

    fun edit(transform: (SetupDraft) -> SetupDraft) = _draft.update { it?.let(transform) }

    /** Names of exercises the concern text leaves out, shown as the user types. */
    fun leftOutFor(concern: String): List<String> =
        concerns.excludedIds(concern).map { library.exercise(it).name }

    fun build(onDone: () -> Unit) {
        val d = _draft.value ?: return
        viewModelScope.launch {
            actions.buildPlan(d)
            onDone()
        }
    }
}

/** Factories that pull dependencies from the [com.homeworkoutcoach.app.AppContainer]. */
object Factories {
    private fun app(extras: androidx.lifecycle.viewmodel.CreationExtras) =
        (extras[APPLICATION_KEY] as HomeWorkoutCoachApp).container

    val App = viewModelFactory {
        initializer { app(this).let { AppViewModel(it.settings, it.actions) } }
    }
    val Week = viewModelFactory {
        initializer {
            app(this).let { WeekViewModel(it.settings, it.training, WeekPresenter(it.library, it.planner), it.actions) }
        }
    }
    val Setup = viewModelFactory {
        initializer {
            app(this).let { SetupViewModel(it.settings, it.actions, it.concerns, it.library) }
        }
    }
}
