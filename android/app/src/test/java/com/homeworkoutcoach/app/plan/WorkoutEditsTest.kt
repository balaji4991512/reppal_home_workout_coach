package com.homeworkoutcoach.app.plan

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Correcting a saved workout: the draft's steppers, and the next level worked out again. */
class WorkoutEditsTest {
    private val lib = Library.default
    private val kit = Equipment(Bench.FLAT, lib.dumbbellStepsKg.toSet())

    private fun workout(vararg benchSets: Pair<Int, Double>, withPushup: Boolean = false) = Workout(
        id = 7, dateMillis = 1_000, label = "A",
        plan = listOf(PlannedExercise("bench", 3, 12, 4.5)) + if (withPushup) listOf(PlannedExercise("pushup", 3, 12, 0.0)) else emptyList(),
        sets = linkedMapOf<String, List<LoggedSet>>("bench" to benchSets.map { LoggedSet(it.first, it.second, 12) }).also {
            if (withPushup) it["pushup"] = List(3) { LoggedSet(10, 0.0, 12) }
        },
    )

    private fun draft(w: Workout) = WorkoutEditDraft.from(lib, w, "Full body A", lib.dumbbellStepsKg)

    // ---- The draft ----

    @Test
    fun theDraftListsTheExercisesThatHaveSets() {
        val d = draft(workout(12 to 4.5, 12 to 4.5, 12 to 4.5, withPushup = true))
        assertEquals(listOf("bench", "pushup"), d.exercises.map { it.id })
        assertEquals(listOf(true), d.exercises.map { it.bodyweight }.drop(1))
        assertFalse(d.changed)
        // a planned exercise with nothing logged (stopped for pain, say) is not editable
        assertEquals(listOf("bench"), draft(workout(12 to 4.5).copy(plan = workout(12 to 4.5).plan + PlannedExercise("row", 3, 12, 5.5))).exercises.map { it.id })
    }

    @Test
    fun repsStepUpAndDownWithinLimits() {
        var d = draft(workout(12 to 4.5, 11 to 4.5))
        d = d.reps(0, 1, +2)
        assertEquals(listOf(12, 13), d.exercises[0].sets.map { it.reps })
        assertTrue(d.changed)
        assertEquals(1, draft(workout(1 to 4.5)).reps(0, 0, -5).exercises[0].sets[0].reps)
        assertEquals(WorkoutEditDraft.MAX_REPS, draft(workout(59 to 4.5)).reps(0, 0, +5).exercises[0].sets[0].reps)
    }

    @Test
    fun weightStepsThroughTheDumbbellsYouHave() {
        val d = draft(workout(12 to 4.5))
        assertEquals(5.5, d.weight(0, 0, +1).exercises[0].sets[0].weightKg, 0.0)
        assertEquals(3.5, d.weight(0, 0, -1).exercises[0].sets[0].weightKg, 0.0)
        assertEquals(8.0, d.weight(0, 0, +1).weight(0, 0, +1).weight(0, 0, +1).exercises[0].sets[0].weightKg, 0.0) // 5.5, 6.5, 8
        // never past the ends
        assertEquals(2.5, draft(workout(12 to 2.5)).weight(0, 0, -1).exercises[0].sets[0].weightKg, 0.0)
        assertEquals(24.0, draft(workout(12 to 24.0)).weight(0, 0, +1).exercises[0].sets[0].weightKg, 0.0)
    }

    @Test
    fun aWeightNotOnTheListIsKeptAndStepsToItsNeighbours() {
        // 5.0 kg was lifted before the adjustable set; it stays on the list, so it can be stepped from and back to
        val d = draft(workout(12 to 5.0))
        assertTrue(5.0 in d.weights)
        assertEquals(5.5, d.weight(0, 0, +1).exercises[0].sets[0].weightKg, 0.0)
        assertEquals(4.5, d.weight(0, 0, -1).exercises[0].sets[0].weightKg, 0.0)
    }

    @Test
    fun aBodyweightExerciseKeepsItsZeroWeight() {
        val d = draft(workout(12 to 4.5, withPushup = true))
        assertEquals(listOf(0.0, 0.0, 0.0), d.exercises[1].sets.map { it.weightKg })
        assertEquals(listOf(11, 10, 10), d.reps(1, 0, +1).exercises[1].sets.map { it.reps })
    }

    @Test
    fun aSetCanBeAddedLikeTheLastOneAndTakenOffButNeverTheOnlyOne() {
        var d = draft(workout(12 to 4.5, 10 to 5.5))
        d = d.addSet(0)
        assertEquals(listOf(12, 10, 10), d.exercises[0].sets.map { it.reps })
        assertEquals(listOf(4.5, 5.5, 5.5), d.exercises[0].sets.map { it.weightKg })
        d = d.removeLastSet(0).removeLastSet(0)
        assertEquals(1, d.exercises[0].sets.size)
        assertEquals(1, d.removeLastSet(0).exercises[0].sets.size) // the only one stays
        var many = draft(workout(12 to 4.5))
        repeat(20) { many = many.addSet(0) }
        assertEquals(WorkoutEditDraft.MAX_SETS, many.exercises[0].sets.size)
    }

    @Test
    fun changingBackToWhatItWasIsNotAChange() {
        val d = draft(workout(12 to 4.5)).reps(0, 0, +1).reps(0, 0, -1)
        assertFalse(d.changed)
    }

    @Test
    fun theDraftSavesItsSetsKeepingEffortAndComments() {
        val w = workout(12 to 4.5).let { it.copy(sets = mapOf("bench" to listOf(LoggedSet(12, 4.5, 12, Effort.HARD, "wrist")))) }
        val sets = draft(w).reps(0, 0, -2).sets()
        assertEquals(LoggedSet(10, 4.5, 12, Effort.HARD, "wrist"), sets.getValue("bench").single())
    }

    // ---- Saving a correction ----

    @Test
    fun correctedSetsReplaceTheSavedOnesAndOthersAreKept() {
        val w = workout(12 to 4.5, 12 to 4.5, 12 to 4.5, withPushup = true)
        val edited = WorkoutEdits.withSets(w, mapOf("bench" to listOf(LoggedSet(12, 5.5, 12))))
        assertEquals(listOf(5.5), edited.sets.getValue("bench").map { it.weightKg })
        assertEquals(w.sets.getValue("pushup"), edited.sets.getValue("pushup"))
    }

    @Test
    fun theNextLevelFollowsWhatWasActuallyDone() {
        // saved as 3 x 12 at 4.5 kg (next: 5.5 kg for 11 reps); it was really done at 8 kg
        val saved = workout(12 to 4.5, 12 to 4.5, 12 to 4.5)
        assertEquals(ExerciseState(5.5, 11, 3), WorkoutEdits.nextStates(lib, kit, saved, emptyList())["bench"])
        val fixed = WorkoutEdits.withSets(saved, mapOf("bench" to List(3) { LoggedSet(12, 8.0, 12) }))
        assertEquals(ExerciseState(9.0, 12, 3), WorkoutEdits.nextStates(lib, kit, fixed, emptyList())["bench"])
    }

    @Test
    fun aLaterWorkoutKeepsTheStateItMade() {
        val saved = workout(12 to 4.5, 12 to 4.5, 12 to 4.5)
        val later = saved.copy(id = 8, dateMillis = 2_000)
        assertTrue(WorkoutEdits.nextStates(lib, kit, saved, listOf(later)).isEmpty())
        // a later workout that did not include the exercise changes nothing
        val other = later.copy(plan = emptyList(), sets = emptyMap())
        assertEquals(setOf("bench"), WorkoutEdits.nextStates(lib, kit, saved, listOf(other)).keys)
    }

    @Test
    fun anExerciseStoppedForPainIsLeftAlone() {
        val saved = workout(12 to 4.5, 12 to 4.5, 12 to 4.5).copy(painIds = setOf("bench"))
        assertTrue(WorkoutEdits.nextStates(lib, kit, saved, emptyList()).isEmpty())
    }
}
