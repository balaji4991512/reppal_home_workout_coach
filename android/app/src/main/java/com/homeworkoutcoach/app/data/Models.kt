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

/** Everything set up on the "Equipment and plan" screen, plus app state that is not history. */
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
    /** Workouts done since the plan was last built; picks the next day of the week. */
    val sessionIndex: Int = 0,
    /** Number of saved workouts when the plan was last built, to find this week's sessions in history. */
    val weekBase: Int = 0,
    /** For BMI on the Body screen. */
    val heightCm: Int? = null,
    /** The user's Bluetooth scale, remembered after the first weigh-in so neighbours' scales are ignored. */
    val scaleId: String? = null,
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
)
