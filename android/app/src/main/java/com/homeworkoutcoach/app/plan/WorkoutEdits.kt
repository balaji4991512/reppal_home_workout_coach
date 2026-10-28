package com.homeworkoutcoach.app.plan

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Progression

/** One set while a saved workout is being corrected. */
data class EditSet(val reps: Int, val weightKg: Double, val targetReps: Int, val effort: Effort, val comment: String) {
    fun toLogged() = LoggedSet(reps, weightKg, targetReps, effort, comment)
}

/** One exercise of a saved workout, with the sets that were logged. */
data class EditExercise(val id: String, val name: String, val bodyweight: Boolean, val sets: List<EditSet>)

/**
 * A saved workout opened for correction: the weight and reps of each set can change, and a set can be added or taken
 * off (an exercise keeps at least one). [weights] are the weights a dumbbell exercise can use, lightest first.
 */
data class WorkoutEditDraft(
    val title: String,
    val exercises: List<EditExercise>,
    val weights: List<Double>,
    private val original: List<EditExercise> = exercises,
) {
    val changed: Boolean get() = exercises != original

    private fun mapSet(ex: Int, set: Int, f: (EditSet) -> EditSet) = copy(
        exercises = exercises.mapIndexed { i, e ->
            if (i != ex) e else e.copy(sets = e.sets.mapIndexed { j, s -> if (j == set) f(s) else s })
        },
    )

    /** [delta] reps more or fewer for one set, never below 1 or above [MAX_REPS]. */
    fun reps(ex: Int, set: Int, delta: Int) = mapSet(ex, set) { it.copy(reps = (it.reps + delta).coerceIn(1, MAX_REPS)) }

    /** The next or previous weight on the list for one set ([delta] steps); a weight not on the list moves to its neighbour. */
    fun weight(ex: Int, set: Int, delta: Int) = mapSet(ex, set) { s ->
        if (weights.isEmpty()) s else {
            val at = weights.indexOfFirst { it >= s.weightKg - 1e-9 }.let { if (it < 0) weights.lastIndex else it }
            val exact = kotlin.math.abs(weights[at] - s.weightKg) < 1e-9
            // from a weight that is not on the list, one step up is the next weight above, one step down the one below
            val target = when {
                exact -> at + delta
                delta > 0 -> at + delta - 1
                else -> at + delta
            }
            s.copy(weightKg = weights[target.coerceIn(0, weights.lastIndex)])
        }
    }

    /** A new set after the last one, like it. */
    fun addSet(ex: Int) = copy(
        exercises = exercises.mapIndexed { i, e -> if (i != ex || e.sets.size >= MAX_SETS) e else e.copy(sets = e.sets + e.sets.last()) },
    )

    /** Takes the last set off, but never the only one. */
    fun removeLastSet(ex: Int) = copy(
        exercises = exercises.mapIndexed { i, e -> if (i != ex || e.sets.size <= 1) e else e.copy(sets = e.sets.dropLast(1)) },
    )

    fun sets(): Map<String, List<LoggedSet>> = exercises.associate { it.id to it.sets.map(EditSet::toLogged) }

    companion object {
        const val MAX_REPS = 60
        const val MAX_SETS = 8

        /** The editable view of [w]: the exercises that have logged sets. [owned] are the dumbbells the person has. */
        fun from(lib: Library, w: Workout, title: String, owned: Collection<Double>): WorkoutEditDraft {
            val exercises = w.plan.mapNotNull { p ->
                val sets = w.sets[p.exerciseId].orEmpty()
                if (sets.isEmpty()) return@mapNotNull null
                val e = lib.exercise(p.exerciseId)
                EditExercise(e.id, e.name, e.bodyweight, sets.map { EditSet(it.reps, it.weightKg, it.targetReps, it.effort, it.comment) })
            }
            val used = exercises.filter { !it.bodyweight }.flatMap { e -> e.sets.map { it.weightKg } }
            return WorkoutEditDraft(title, exercises, (owned + used).distinct().sorted())
        }
    }
}

/** Saving a corrected workout: its new sets, and what the next session of each exercise now looks like. */
object WorkoutEdits {
    /** [w] with the edited [sets] in place of the ones that were saved. Exercises that were not edited keep theirs. */
    fun withSets(w: Workout, sets: Map<String, List<com.homeworkoutcoach.engine.LoggedSet>>): Workout =
        w.copy(sets = w.sets.mapValues { (id, old) -> sets[id] ?: old })

    /**
     * The next-session state for each exercise of [w] that was done, worked out from what was actually done, as when the
     * workout was finished. An exercise done again in a [later] workout keeps the state that one made, and one that
     * stopped for pain is left alone. [w] must already hold the corrected sets.
     */
    fun nextStates(
        lib: Library,
        equipment: Equipment,
        w: Workout,
        later: List<Workout>,
        /** Only these exercises are worked out again (the ones that were edited); null means all of them. */
        only: Set<String>? = null,
    ): Map<String, ExerciseState> =
        w.plan.mapNotNull { p ->
            if (only != null && p.exerciseId !in only) return@mapNotNull null
            val done = w.sets[p.exerciseId].orEmpty()
            if (p.exerciseId in w.painIds || done.isEmpty()) return@mapNotNull null
            if (later.any { l -> l.sets[p.exerciseId].orEmpty().isNotEmpty() }) return@mapNotNull null
            p.exerciseId to Progression.nextSession(lib.exercise(p.exerciseId), equipment, p, done).next
        }.toMap()
}
