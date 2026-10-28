package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.PlanSettings
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Split
import kotlinx.serialization.Serializable

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** A focus session that is running. The settings keep it, so it survives the app being closed. */
@Serializable
data class ActiveFocus(val startMillis: Long, val plannedMinutes: Int, val taskTitle: String? = null) {
    fun toRun() = com.homeworkoutcoach.engine.FocusRun(startMillis, plannedMinutes, taskTitle)
}

/** The quality picked for the week that starts on [weekStartEpochDay] (a Monday). A new week goes back to the suggestion. */
@Serializable
data class CharacterFocus(val weekStartEpochDay: Long, val quality: String)

/** Everything set up on the "Plan settings" screen, plus app state that is not history. */
@Serializable
data class UserSettings(
    /** True once the first plan has been built. */
    val started: Boolean = false,
    /** Continued without an account. Data stays on the device. */
    val guest: Boolean = false,
    val name: String = "",
    val bench: Bench = Bench.FLAT,
    val dumbbellsKg: List<Double> = listOf(5.0),
    val level: Level = Level.BEGINNER,
    /** Index of the "your training so far" option, which sets [level]. */
    val experienceOption: Int = 0,
    val daysPerWeek: Int = 3,
    val split: Split = Split.AUTO,
    val customDays: List<List<Muscle>> = emptyList(),
    /** The free-text "anything to work around" box, as typed. */
    val concern: String = "",
    /** Exercises left out of the plan: mapped from [concern] plus [painExcluded]. */
    val excluded: List<String> = emptyList(),
    /** Exercises removed after a pain report. Kept when the concern text changes. */
    val painExcluded: List<String> = emptyList(),
    val theme: ThemeMode = ThemeMode.LIGHT,
    /** No longer used to pick a workout (each session belongs to its weekday); kept so old saved settings still load. */
    val sessionIndex: Int = 0,
    /** No longer used; kept so old saved settings still load. */
    val weekBase: Int = 0,
    /**
     * The day the plan (or its weekday arrangement) was made, as an epoch day. Days before it were never due, so they
     * are not shown as skipped. Null until set (an older install), which counts as today.
     */
    val planStartEpochDay: Long? = null,
    /** For BMI on the Body screen. */
    val heightCm: Int? = null,
    /** The user's Bluetooth scale, remembered after the first weigh-in so neighbours' scales are ignored. */
    val scaleId: String? = null,
    /** Rearranged week: weekday (0 = Monday) for each session of the plan's default order. Null = default days. */
    val sessionWeekdays: List<Int>? = null,
    /** The focus timer, while one is running. */
    val activeFocus: ActiveFocus? = null,
    /** The quality chosen for this week on the Character screen, if the person changed the suggestion. */
    val characterFocus: CharacterFocus? = null,
    /** The "tap the circle to mark it done" hint on the habit lists has been dismissed. */
    val habitTickHintSeen: Boolean = false,
) {
    fun equipment(): Equipment = Equipment(bench, dumbbellsKg.toSet())

    fun planSettings(swaps: Map<String, String>): PlanSettings = PlanSettings(
        equipment = equipment(),
        level = level,
        daysPerWeek = daysPerWeek,
        split = split,
        customDays = customDays,
        excluded = excluded.toSet(),
        swaps = swaps,
        sessionWeekdays = sessionWeekdays,
    )
}

/** One finished workout. [sets] keeps the order exercises were done in. */
data class Workout(
    val id: Long = 0,
    val dateMillis: Long,
    /** Day label from the planner, such as "A" or "M:chest,triceps#1". */
    val label: String,
    val plan: List<PlannedExercise>,
    val sets: Map<String, List<LoggedSet>>,
    val painIds: Set<String> = emptySet(),
    /** The day (epoch day) of the skipped workout this one makes up, or null for a workout done on its own day. */
    val madeUpForEpochDay: Long? = null,
)
