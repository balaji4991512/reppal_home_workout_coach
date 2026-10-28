package com.homeworkoutcoach.app.plan

import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.GoalRepository
import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.data.ReflectionRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.ConcernMapper
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.Split
import kotlinx.coroutines.flow.first

/** What the Setup screen edits. Nothing is saved until the plan is built. */
data class SetupDraft(
    val name: String,
    val bench: Bench,
    val dumbbellsKg: Set<Double>,
    val experienceOption: Int,
    val level: Level,
    val concern: String,
    val daysPerWeek: Int,
    val split: Split,
    /** Always 7 days, so switching day counts keeps each day's picks. */
    val customDays: List<List<Muscle>>,
) {
    companion object {
        fun from(s: UserSettings, defaultCustom: List<List<Muscle>>) = SetupDraft(
            name = s.name,
            bench = s.bench,
            dumbbellsKg = s.dumbbellsKg.toSet(),
            experienceOption = s.experienceOption,
            level = s.level,
            concern = s.concern,
            daysPerWeek = s.daysPerWeek,
            split = s.split,
            customDays = List(7) { i -> s.customDays.getOrNull(i) ?: defaultCustom[i] },
        )
    }
}

/** Plan-level actions shared by screens. */
class PlanActions(
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val concerns: ConcernMapper,
    private val body: BodyRepository? = null,
    private val habits: HabitRepository? = null,
    private val tasks: TaskRepository? = null,
    private val routines: RoutineRepository? = null,
    private val journal: JournalRepository? = null,
    private val goals: GoalRepository? = null,
    private val focus: FocusRepository? = null,
    private val reflections: ReflectionRepository? = null,
    private val today: () -> java.time.LocalDate = { java.time.LocalDate.now() },
) {
    /**
     * Save the setup and start a new week [buildPlan]. The first build forgets any exercise states so starting
     * weights come from the chosen level. History and later progress are kept.
     */
    suspend fun buildPlan(draft: SetupDraft) {
        val first = !settings.settings.first().started
        val saved = training.workouts().first().size
        if (first) training.clearExerciseStates()
        settings.update { s ->
            s.copy(
                started = true,
                name = draft.name.trim(),
                bench = draft.bench,
                dumbbellsKg = draft.dumbbellsKg.sorted(),
                experienceOption = draft.experienceOption,
                level = draft.level,
                concern = draft.concern,
                daysPerWeek = draft.daysPerWeek,
                split = draft.split,
                customDays = draft.customDays,
                excluded = (concerns.excludedIds(draft.concern) + s.painExcluded).distinct(),
                // A rearranged week only fits the plan it was made for.
                sessionWeekdays = s.sessionWeekdays.takeIf {
                    draft.daysPerWeek == s.daysPerWeek && draft.split == s.split &&
                        (draft.split != Split.CUSTOM || draft.customDays.take(draft.daysPerWeek) == s.customDays.take(s.daysPerWeek))
                },
                sessionIndex = 0,
                weekBase = saved,
                // The first plan starts today; later edits keep the day it first started.
                planStartEpochDay = if (first) today().toEpochDay() else s.planStartEpochDay ?: today().toEpochDay(),
            )
        }
    }

    /** An install from before plans had a start day gets today as it, so nothing earlier is marked skipped. */
    suspend fun ensurePlanStart() {
        settings.update { s -> if (s.started && s.planStartEpochDay == null) s.copy(planStartEpochDay = today().toEpochDay()) else s }
    }

    /** Swap [baseId] for [chosenId] in every session; choosing the base again removes the swap. */
    suspend fun swap(baseId: String, chosenId: String) =
        training.setSwap(baseId, chosenId.takeUnless { it == baseId })

    /**
     * Save a finished workout: the record and next-session states together, then move the week on and leave out
     * anything that hurt (kept even if the concern text changes later).
     */
    suspend fun finishWorkout(result: SessionResult) {
        training.saveWorkout(result.workout, result.nextStates)
        settings.update { s ->
            s.copy(
                sessionIndex = s.sessionIndex + 1,
                excluded = (s.excluded + result.painIds).distinct(),
                painExcluded = (s.painExcluded + result.painIds).distinct(),
            )
        }
    }

    /** Erase everything on this device: history, plan states, swaps, weigh-ins, habits, tasks, routines, journal, goals, focus sessions, reflections and settings. */
    suspend fun wipeAll() {
        training.wipe()
        body?.wipe()
        habits?.wipe()
        tasks?.wipe()
        routines?.wipe()
        journal?.wipe()
        goals?.wipe()
        focus?.wipe()
        reflections?.wipe()
        settings.reset()
    }

    /**
     * Save a rearranged week ([weekdays]: the weekday for each session in the plan's default order; null restores the
     * default) and start a fresh week from its first day.
     */
    suspend fun arrangeWeek(weekdays: List<Int>?) {
        val saved = training.workouts().first().size
        // A new arrangement puts the sessions on new days, so it starts counting from today.
        settings.update { it.copy(sessionWeekdays = weekdays, sessionIndex = 0, weekBase = saved, planStartEpochDay = today().toEpochDay()) }
    }

    /** Use the app without an account; everything stays on this phone. */
    suspend fun continueAsGuest() = settings.update { it.copy(guest = true) }

    /** After Google sign-in: use the first name if none was typed [signIn]. */
    suspend fun signedIn(account: Account) = settings.update { s ->
        val first = account.name?.trim()?.split(' ')?.firstOrNull().orEmpty()
        s.copy(guest = false, name = s.name.ifBlank { first })
    }

    /** Signing out returns to the sign-in screen. Workouts stay on this phone. */
    suspend fun signedOut() = settings.update { it.copy(guest = false) }

    suspend fun setTheme(mode: ThemeMode) = settings.update { it.copy(theme = mode) }

    suspend fun storeStates(states: Map<String, ExerciseState>) {
        if (states.isNotEmpty()) training.putExerciseStates(states)
    }
}
