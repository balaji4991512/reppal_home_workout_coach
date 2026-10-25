package com.homeworkoutcoach.engine

import kotlin.math.floor

enum class Muscle(val key: String, val short: String) {
    CHEST("chest", "chest"),
    SHOULDERS("shoulders", "shoulders"),
    REAR_DELTS("reardelts", "rear delts"),
    LATS("lats", "back"),
    BICEPS("biceps", "biceps"),
    TRICEPS("triceps", "triceps"),
    QUADS("quads", "quads"),
    HAMS("hams", "hamstrings"),
    GLUTES("glutes", "glutes"),
    ABS("abs", "abs");

    companion object {
        fun fromKey(key: String): Muscle = entries.first { it.key == key }
    }
}

enum class Need(val key: String) {
    DUMBBELL("db"), BENCH("bench"), ADJUSTABLE_BENCH("adj");

    companion object {
        fun fromKey(key: String): Need = entries.first { it.key == key }
    }
}

enum class Bench { NONE, FLAT, ADJUSTABLE }

enum class Level(val key: String, val weightFactor: Double) {
    BEGINNER("beginner", 1.0),
    INTERMEDIATE("intermediate", 1.25),
    ADVANCED("advanced", 1.5);

    companion object {
        fun fromKey(key: String): Level = entries.first { it.key == key }
    }
}

enum class Split { AUTO, FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS, BODY_PART, CUSTOM }

enum class Effort(val key: String) {
    EASY("easy"), JUST_RIGHT("just right"), HARD("hard")
}

enum class SetFlag(val key: String) { PAIN("pain") }

data class Exercise(
    val id: String,
    val name: String,
    val needs: Set<Need>,
    val bodyweight: Boolean,
    val startKg: Double?,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
) {
    /** Top of the rep range: 12 weighted, 15 bodyweight. */
    val topReps: Int get() = if (bodyweight) BODYWEIGHT_TOP_REPS else WEIGHTED_TOP_REPS
}

/** What the user owns. Bodyweight moves are always available. */
data class Equipment(val bench: Bench, val dumbbellsKg: Set<Double>) {
    val dumbbells: List<Double> = dumbbellsKg.sorted()

    fun has(need: Need): Boolean = when (need) {
        Need.DUMBBELL -> dumbbells.isNotEmpty()
        Need.BENCH -> bench != Bench.NONE
        Need.ADJUSTABLE_BENCH -> bench == Bench.ADJUSTABLE
    }

    fun canDo(exercise: Exercise): Boolean = exercise.needs.all(::has)

    fun owns(kg: Double): Boolean = kg in dumbbellsKg

    fun nextHeavier(kg: Double): Double? = dumbbells.firstOrNull { it > kg }

    /** Largest owned dumbbell <= [kg], else the lightest owned (0 when none). */
    fun snapDown(kg: Double): Double = dumbbells.lastOrNull { it <= kg } ?: dumbbells.firstOrNull() ?: 0.0
}

/** Per-exercise plan state, persisted between sessions. Bodyweight moves use weightKg = 0. */
data class ExerciseState(
    val weightKg: Double,
    val targetReps: Int = DEFAULT_TARGET_REPS,
    val sets: Int = DEFAULT_SETS,
)

/** What voice, text or chips produce for one set (PRODUCT_SPEC "Set record"). */
data class SetRecord(
    val reps: Int,
    val weightKg: Double?,
    val effort: Effort,
    val flags: Set<SetFlag> = emptySet(),
    val comment: String = "",
) {
    val pain: Boolean get() = SetFlag.PAIN in flags
}

/** The prescription an exercise had when the workout started. */
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val targetReps: Int,
    val weightKg: Double,
)

/** One saved set: reps done, the weight used and the live target that applied to it. */
data class LoggedSet(
    val reps: Int,
    val weightKg: Double,
    val targetReps: Int,
    val effort: Effort = Effort.JUST_RIGHT,
)

const val DEFAULT_TARGET_REPS = 12
const val DEFAULT_SETS = 3
const val MAX_SETS = 4
const val LOW_REPS = 8
const val WEIGHTED_TOP_REPS = 12
const val BODYWEIGHT_TOP_REPS = 15

/** JavaScript Math.round: halves round up. The prototype is the spec, so match it exactly. */
internal fun jsRound(x: Double): Int = floor(x + 0.5).toInt()
