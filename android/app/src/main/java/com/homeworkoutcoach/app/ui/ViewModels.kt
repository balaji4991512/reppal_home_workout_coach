package com.homeworkoutcoach.app.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeworkoutcoach.app.HomeWorkoutCoachApp
import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.AuthRepository
import com.homeworkoutcoach.app.data.DataExport
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.DataExporter
import com.homeworkoutcoach.app.data.DataImport
import com.homeworkoutcoach.app.data.DataImporter
import com.homeworkoutcoach.app.data.DeleteResult
import com.homeworkoutcoach.app.data.ImportCheck
import com.homeworkoutcoach.app.data.Restore
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.SignInResult
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.SetupDraft
import com.homeworkoutcoach.app.ui.body.BodyPresenter
import com.homeworkoutcoach.app.ui.body.BodyViewModel
import com.homeworkoutcoach.app.ui.habits.HabitDetailViewModel
import com.homeworkoutcoach.app.ui.habits.HabitEditViewModel
import com.homeworkoutcoach.app.ui.habits.HabitsViewModel
import com.homeworkoutcoach.app.ui.planner.PlannerActions
import com.homeworkoutcoach.app.ui.planner.PlannerViewModel
import com.homeworkoutcoach.app.ui.planner.TaskEditViewModel
import com.homeworkoutcoach.app.ui.profile.ProfilePresenter
import com.homeworkoutcoach.app.ui.routines.RoutineEditViewModel
import com.homeworkoutcoach.app.ui.routines.RoutineRunViewModel
import com.homeworkoutcoach.app.ui.routines.RoutineRunner
import com.homeworkoutcoach.app.ui.routines.RoutinesViewModel
import com.homeworkoutcoach.app.ui.profile.ProfileUi
import com.homeworkoutcoach.app.ui.progress.ProgressPresenter
import com.homeworkoutcoach.app.ui.week.ArrangePresenter
import com.homeworkoutcoach.app.ui.week.ArrangeViewModel
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.app.ui.week.WeekUi
import com.homeworkoutcoach.app.ui.workout.WorkoutViewModel
import com.homeworkoutcoach.engine.ConcernMapper
import com.homeworkoutcoach.engine.KeywordSetParser
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
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shell state: settings drive the theme, the start screen and the menu footer. Null until loaded. */
class AppViewModel(
    settings: SettingsRepository,
    private val actions: PlanActions,
    private val auth: AuthRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val account: StateFlow<Account?> = auth.account

    private val _signIn = MutableStateFlow(SignInUi())
    val signIn: StateFlow<SignInUi> = _signIn

    /** Google sign-in from the sign-in screen. [onDone] runs after a successful sign-in. */
    fun signInWithGoogle(activity: Activity, onDone: () -> Unit = {}) {
        if (_signIn.value.busy) return
        _signIn.value = SignInUi(busy = true)
        viewModelScope.launch {
            val error = when (val r = auth.signInWithGoogle(activity)) {
                is SignInResult.SignedIn -> {
                    actions.signedIn(r.account)
                    onDone()
                    null
                }
                SignInResult.Cancelled -> null
                SignInResult.NoAccount -> "No Google account on this phone. Add one in Settings, or try without an account."
                SignInResult.NotConfigured -> "Google sign-in isn’t set up in this build. Try without an account."
                is SignInResult.Failed -> "Couldn’t sign in with Google. Check your connection and try again."
            }
            _signIn.value = SignInUi(error = error)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            actions.signedOut()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { actions.setTheme(mode) }
    }

    fun continueAsGuest() {
        viewModelScope.launch { actions.continueAsGuest() }
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

class ProgressViewModel(training: TrainingRepository, val presenter: ProgressPresenter) : ViewModel() {
    /** Null until loaded. */
    val workouts: StateFlow<List<Workout>?> =
        training.workouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class ProfileViewModel(
    settings: SettingsRepository,
    private val training: TrainingRepository,
    private val auth: AuthRepository,
    presenter: ProfilePresenter,
    private val actions: PlanActions,
    private val exporter: DataExporter,
    private val importer: DataImporter,
    private val library: Library,
    private val body: BodyRepository,
) : ViewModel() {
    private val _delete = MutableStateFlow(DeleteUi())

    /** Delete account: busy while Google confirms, then a message if it didn't work. */
    val delete: StateFlow<DeleteUi> = _delete

    /**
     * Delete the Google account's sign-in with this app and erase everything on this phone. Google may show the
     * account picker once more to confirm. Afterwards the app is back at the sign-in screen.
     */
    fun deleteAccount(activity: Activity) {
        if (_delete.value.busy) return
        _delete.value = DeleteUi(busy = true)
        viewModelScope.launch {
            _delete.value = when (val r = auth.deleteAccount(activity)) {
                DeleteResult.Deleted -> {
                    actions.wipeAll()
                    DeleteUi()
                }
                DeleteResult.Cancelled -> DeleteUi()
                DeleteResult.NotSignedIn -> DeleteUi(error = "You're not signed in, so there is no account to delete.")
                is DeleteResult.Failed -> DeleteUi(error = "Couldn’t delete your account. Check your connection and try again.")
            }
        }
    }

    private val _restore = MutableStateFlow<RestoreUi?>(null)

    /** Restore from a file: the confirm step, then the outcome. */
    val restore: StateFlow<RestoreUi?> = _restore

    /** Check a chosen file and ask before replacing anything. */
    fun checkFile(text: String?) {
        if (text == null) {
            _restore.value = RestoreUi.Message("Couldn’t open that file.", bad = true)
            return
        }
        viewModelScope.launch {
            _restore.value = when (val c = withContext(Dispatchers.Default) { DataImport.read(library, text) }) {
                is ImportCheck.Bad -> RestoreUi.Message(c.message, bad = true)
                is ImportCheck.Ok -> {
                    val r = c.restore
                    val nowWorkouts = training.workouts().first().size
                    val nowWeights = body.weights().first().size
                    val exported = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.forLanguageTag("en-IN"))
                        .format(Instant.ofEpochMilli(r.exportedAt).atZone(ZoneId.systemDefault()))
                    RestoreUi.Confirm(
                        r,
                        "The file has ${Copy.count(r.workouts.size, "workout")} and ${Copy.count(r.weights.size, "weigh-in")}, " +
                            "saved $exported. Restoring replaces everything on this phone " +
                            "(now ${Copy.count(nowWorkouts, "workout")} and ${Copy.count(nowWeights, "weigh-in")}).",
                    )
                }
            }
        }
    }

    fun confirmRestore() {
        val c = _restore.value as? RestoreUi.Confirm ?: return
        viewModelScope.launch {
            _restore.value = runCatching { importer.restore(c.restore) }.fold(
                { RestoreUi.Message("Restored ${Copy.count(c.restore.workouts.size, "workout")} and ${Copy.count(c.restore.weights.size, "weigh-in")}.") },
                { RestoreUi.Message("Restoring stopped part-way. Try the same file again.", bad = true) },
            )
        }
    }

    fun cancelRestore() {
        _restore.value = null
    }

    private val _exportNote = MutableStateFlow<String?>(null)

    /** Result of the last export, shown under the button. */
    val exportNote: StateFlow<String?> = _exportNote

    /** Suggested name for the export file. */
    fun exportFileName(appName: String) = DataExport.fileName(appName, System.currentTimeMillis(), ZoneId.systemDefault())

    /** Build the export and hand it to [write], which saves it where the user chose. */
    fun export(appName: String, write: suspend (String) -> Unit) {
        viewModelScope.launch {
            _exportNote.value = runCatching {
                val r = exporter.export(appName)
                withContext(Dispatchers.IO) { write(r.json) }
                "Saved ${Copy.count(r.workouts, "workout")} and ${Copy.count(r.weighIns, "weigh-in")}."
            }.getOrElse { "Couldn’t save the file. Try again, or pick another place." }
        }
    }

    val ui: StateFlow<ProfileUi?> = combine(
        settings.settings, training.exerciseStates(), training.workouts(), auth.account, body.weights(),
    ) { s, states, w, account, weights ->
        presenter.present(s, states, w, account, weights.size)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun wipe(onDone: () -> Unit) {
        viewModelScope.launch {
            actions.wipeAll()
            onDone()
        }
    }
}

data class SignInUi(val busy: Boolean = false, val error: String? = null)

data class DeleteUi(val busy: Boolean = false, val error: String? = null)

sealed interface RestoreUi {
    data class Confirm(val restore: Restore, val summary: String) : RestoreUi
    data class Message(val text: String, val bad: Boolean = false) : RestoreUi
}

/** Factories that pull dependencies from the [com.homeworkoutcoach.app.AppContainer]. */
object Factories {
    private fun app(extras: androidx.lifecycle.viewmodel.CreationExtras) =
        (extras[APPLICATION_KEY] as HomeWorkoutCoachApp).container

    val App = viewModelFactory {
        initializer { app(this).let { AppViewModel(it.settings, it.actions, it.auth) } }
    }
    val Week = viewModelFactory {
        initializer {
            app(this).let { WeekViewModel(it.settings, it.training, WeekPresenter(it.library, it.planner), it.actions) }
        }
    }
    val Workout = viewModelFactory {
        initializer {
            app(this).let {
                WorkoutViewModel(it.library, it.planner, it.settings, it.training, it.actions, KeywordSetParser)
            }
        }
    }
    val Arrange = viewModelFactory {
        initializer {
            app(this).let { ArrangeViewModel(it.settings, it.training, it.actions, ArrangePresenter(it.library, it.planner)) }
        }
    }
    val HabitList = viewModelFactory {
        initializer { app(this).let { HabitsViewModel(it.habits, it.training, it.routines) } }
    }
    fun habitDetail(id: String) = viewModelFactory {
        initializer { app(this).let { HabitDetailViewModel(id, it.habits, it.training, it.routines) } }
    }
    fun habitEdit(id: String?) = viewModelFactory {
        initializer { app(this).let { HabitEditViewModel(id, it.habits) } }
    }
    val Planner = viewModelFactory {
        initializer { app(this).let { PlannerViewModel(it.tasks, PlannerActions(it.tasks)) } }
    }
    fun taskEdit(id: String) = viewModelFactory {
        initializer { app(this).let { TaskEditViewModel(id, it.tasks, PlannerActions(it.tasks)) } }
    }
    val RoutineList = viewModelFactory {
        initializer { app(this).let { RoutinesViewModel(it.routines) } }
    }
    fun routineRun(id: String) = viewModelFactory {
        initializer { app(this).let { RoutineRunViewModel(id, it.routines, it.habits, RoutineRunner(it.habits, it.routines) { java.time.LocalDate.now() }) } }
    }
    fun routineEdit(id: String?) = viewModelFactory {
        initializer { app(this).let { RoutineEditViewModel(id, it.routines, it.habits) } }
    }
    val Body = viewModelFactory {
        initializer { app(this).let { BodyViewModel(it.body, it.settings, BodyPresenter()) } }
    }
    val Progress = viewModelFactory {
        initializer { app(this).let { ProgressViewModel(it.training, ProgressPresenter(it.library)) } }
    }
    val Profile = viewModelFactory {
        initializer {
            app(this).let {
                ProfileViewModel(
                    it.settings, it.training, it.auth, ProfilePresenter(it.library, it.planner), it.actions,
                    it.exporter, it.importer, it.library, it.body,
                )
            }
        }
    }
    val Setup = viewModelFactory {
        initializer {
            app(this).let { SetupViewModel(it.settings, it.actions, it.concerns, it.library) }
        }
    }
}
