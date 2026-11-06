package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.WorkoutSession
import com.homeworkoutcoach.app.ui.workout.DonePresenter
import com.homeworkoutcoach.app.ui.workout.WorkoutState
import com.homeworkoutcoach.app.ui.workout.WorkoutViewModel
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Day
import com.homeworkoutcoach.engine.DayItem
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.KeywordSetParser
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LiveTarget
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Ramp
import com.homeworkoutcoach.engine.SetFlag
import com.homeworkoutcoach.engine.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Changing a workout while it runs: going back, correcting or deleting a set, skipping and replacing. */
class WorkoutChangesTest {
    private val lib = Library.default
    private val kit = Equipment(Bench.FLAT, setOf(5.0, 6.0))
    private val day = Day("A", listOf(DayItem("Push", "bench"), DayItem("Core", "dead")), emptyList())
    private fun start() = WorkoutSession.start(lib, kit, day) { id ->
        if (id == "bench") ExerciseState(5.0) else ExerciseState(0.0)
    }

    private fun set(reps: Int, effort: Effort = Effort.JUST_RIGHT, pain: Boolean = false) =
        SetRecord(reps, null, effort, if (pain) setOf(SetFlag.PAIN) else emptySet())

    /** All three bench sets at 12, so the workout is on the second exercise. */
    private fun benchDone() = start().save(set(12)).save(set(12)).save(set(12))

    @Test
    fun aCorrectedSetKeepsItsTargetAndTheNextSetFollowsIt() {
        // saved as 12 easy by mistake; it was 9, hard
        val s = start().save(set(12, Effort.EASY))
        assertEquals(LiveTarget(6.0, 11), s.target)
        val fixed = s.editSet(0, 9, 5.0, Effort.HARD)
        assertEquals(LoggedSet(9, 5.0, 12, Effort.HARD), fixed.sets.getValue("bench").single())
        // the next set is worked out again from the corrected one, as if it had been saved that way
        assertEquals(start().save(set(9, Effort.HARD)).target, fixed.target)
        assertEquals(1, fixed.setIndex)
    }

    @Test
    fun goingBackToAFinishedExerciseToFixASet() {
        val s = benchDone()
        assertEquals(1, s.index)
        val back = s.goTo(0)
        assertTrue(back.complete(0))
        assertThrows(IllegalStateException::class.java) { back.save(set(12)) } // nothing more to log there
        val fixed = back.editSet(2, 10, 6.0, Effort.HARD)
        assertEquals(listOf(12, 12, 10), fixed.repsDone)
        assertEquals(6.0, fixed.sets.getValue("bench")[2].weightKg, 0.0)
        assertEquals(1, fixed.nextOpen()) // the next thing to do is still the second exercise
    }

    @Test
    fun aDeletedSetIsDoneAgainAndThenTheWorkoutMovesOn() {
        val s = benchDone().goTo(0).deleteSet(1)
        assertFalse(s.complete(0))
        assertEquals(listOf(12, 12), s.repsDone)
        assertEquals(2, s.setIndex)
        val again = s.save(set(11))
        assertEquals(listOf(12, 12, 11), again.sets.getValue("bench").map { it.reps })
        assertEquals(1, again.index) // on to the exercise still to do
    }

    @Test
    fun deletingTheOnlySetStartsTheExerciseAfresh() {
        val s = start().save(set(12, Effort.EASY)).deleteSet(0)
        assertNull(s.sets["bench"])
        assertEquals(0, s.setIndex)
        assertEquals(start().target, s.target)
    }

    @Test
    fun aSkippedExerciseIsLeftAsItWasForNextTime() {
        val s = start().skip()
        assertEquals(1, s.index)
        assertTrue(s.isSkipped(0) && s.complete(0))
        // the last exercise cannot be skipped too: nothing would be logged
        assertFalse(s.canSkip)
        val done = s.save(set(10)).save(set(10)).save(set(10))
        assertTrue(done.finished)
        val result = done.finish(0)
        val bench = result.changes.first { it.exercise.id == "bench" }
        assertTrue(bench.skipped)
        assertNull(bench.next)
        assertFalse("bench" in result.nextStates)
        assertTrue(result.painIds.isEmpty()) // skipping is not pain
        val ui = DonePresenter(lib).present(result, emptyList(), kit)
        val line = ui.changes.first { it.name == lib.exercise("bench").name }
        assertEquals(Copy.SKIPPED_NEXT, line.message)
        assertTrue(line.next, line.next.startsWith("Next: 3 × 12"))
    }

    @Test
    fun skippingTheLastOneLeftFinishesTheWorkout() {
        val s = start().save(set(12)).save(set(12)).save(set(12))
        assertTrue(s.canSkip)
        assertTrue(s.skip().finished)
    }

    @Test
    fun aSkipCanBeTakenBack() {
        val s = start().skip().goTo(0).unskip()
        assertFalse(s.complete(0))
        assertEquals(0, s.save(set(12)).index)
    }

    @Test
    fun painTappedByMistakeCanBeUndone() {
        val s = start().save(set(12, pain = true))
        assertEquals(1, s.index)
        val back = s.goTo(0).undoPain()
        assertFalse(back.complete(0))
        assertTrue(back.pain.isEmpty())
        assertEquals(1, back.setIndex)
    }

    @Test
    fun aReplacementTakesTheSlotForThisWorkoutAndDropsWhatWasLogged() {
        val s = start().save(set(12))
        val floor = lib.exercise("floor")
        val planned = Ramp.plan(floor, kit, ExerciseState(5.0))
        val r = s.replace(planned)
        assertEquals("floor", r.exercise.id)
        assertEquals("bench", r.day.items[0].baseId) // the plan's own exercise, for next time
        assertNull(r.sets["bench"])
        assertEquals(planned, r.planned)
        assertEquals(0, r.setIndex)
        // something already in the workout cannot come in twice
        assertThrows(IllegalArgumentException::class.java) { r.replace(Ramp.plan(lib.exercise("dead"), kit, ExerciseState(0.0))) }
    }

    @Test
    fun allDoneAfterGoingBackIsFinishedOnRequest() {
        val s = benchDone().save(set(10)).save(set(10)).save(set(10))
        assertTrue(s.finished)
        // the same, but having looked back at the first exercise before the last set
        val looked = benchDone().save(set(10)).save(set(10)).goTo(0)
        assertFalse(looked.allComplete)
        val t = looked.goTo(1).save(set(10))
        assertTrue(t.finished)
        assertTrue(benchDone().goTo(0).let { !it.allComplete })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutChangesViewModelTest {
    private val lib = Library.default
    private val settings = InMemorySettingsRepository(
        UserSettings(started = true, bench = Bench.FLAT, dumbbellsKg = listOf(5.0, 6.0), daysPerWeek = 1),
    )
    private val training = InMemoryTrainingRepository()
    private val actions = PlanActions(settings, training, KeywordConcernMapper(lib))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val monday = java.time.LocalDate.of(2026, 10, 26).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    private fun vm() = WorkoutViewModel(lib, Planner(lib), settings, training, actions, KeywordSetParser, java.time.ZoneOffset.UTC, { monday })
    private fun WorkoutViewModel.logging() = state.value as WorkoutState.Logging

    @Test
    fun replacingForThisWorkoutOnlyLeavesThePlanAlone() = runTest {
        val vm = vm()
        val first = vm.logging().session.exercise.id
        val options = vm.replacements()
        assertTrue(options.isNotEmpty())
        assertTrue(options.none { it.first == first })
        vm.replace(options.first().first, fromNowOn = false)
        assertEquals(options.first().first, vm.logging().session.exercise.id)
        assertTrue(training.swaps().first().isEmpty())
    }

    @Test
    fun replacingFromNowOnSwapsItInThePlanToo() = runTest {
        val vm = vm()
        val first = vm.logging().session.exercise.id
        val to = vm.replacements().first().first
        vm.replace(to, fromNowOn = true)
        assertEquals(mapOf(first to to), training.swaps().first())
        // and the plan's own exercise is offered to go back to
        assertEquals(first, vm.replacements().first().first)
    }

    @Test
    fun skippingEverythingButOneAndFinishingSavesTheWorkout() = runTest {
        val vm = vm()
        val n = vm.logging().session.day.items.size
        repeat(n - 1) { vm.skip() }
        while (vm.state.value is WorkoutState.Logging) {
            vm.onReps(vm.logging().session.target.targetReps)
            vm.onSave()
        }
        assertTrue(vm.state.value is WorkoutState.Done)
        val saved = training.workouts().first().single()
        assertEquals(1, saved.sets.values.count { it.isNotEmpty() })
    }

    @Test
    fun aWeightChipGoesIntoTheSetAndTheNextSetFollowsIt() = runTest {
        val vm = vm()
        val target = vm.logging().session.target
        vm.onReps(target.targetReps)
        vm.onWeight(6.0)
        assertEquals(SetRecord(target.targetReps, 6.0, Effort.JUST_RIGHT), vm.logging().input.preview)
        // tapping it again goes back to the target weight
        vm.onWeight(6.0)
        assertEquals(target.weightKg, vm.logging().input.preview!!.weightKg ?: target.weightKg, 0.0)
        vm.onWeight(6.0)
        vm.onSave()
        assertEquals(6.0, vm.logging().session.sets.values.single().single().weightKg, 0.0)
        assertEquals(6.0, vm.logging().session.target.weightKg, 0.0) // the next set carries on at the weight used
        assertNull(vm.logging().input.weightKg) // and the chips start again from the new target
    }

    @Test
    fun aWeightChipAdjustsASpokenNote() = runTest {
        val vm = vm()
        vm.onVoiceResult("ten reps, hard")
        vm.onWeight(6.0)
        assertEquals(SetRecord(10, 6.0, Effort.HARD), vm.logging().input.preview)
    }
}

class WeightChoicesTest {
    private val rack = listOf(2.0, 4.5, 7.0, 9.0, 10.0, 11.5, 13.5, 16.0)

    @Test
    fun twoEitherSideOfTheTarget() {
        assertEquals(listOf(9.0, 10.0, 11.5, 13.5, 16.0), com.homeworkoutcoach.app.ui.workout.weightChoices(rack, 11.5, 11.5))
    }

    @Test
    fun nearTheEndsTheOtherSideMakesUpTheFive() {
        assertEquals(listOf(2.0, 4.5, 7.0, 9.0, 10.0), com.homeworkoutcoach.app.ui.workout.weightChoices(rack, 2.0, 2.0))
        assertEquals(listOf(10.0, 11.5, 13.5, 16.0) , com.homeworkoutcoach.app.ui.workout.weightChoices(rack, 16.0, 16.0).takeLast(4))
    }

    @Test
    fun aWeightOutsideTheFiveIsStillShownWhenChosen() {
        assertEquals(listOf(2.0, 9.0, 10.0, 11.5, 13.5, 16.0), com.homeworkoutcoach.app.ui.workout.weightChoices(rack, 11.5, 2.0))
    }

    @Test
    fun repsRunFromTwoOverToThreeUnder() {
        assertEquals(listOf(14, 13, 12, 11, 10, 9), com.homeworkoutcoach.app.ui.workout.repChoices(12))
        assertEquals(listOf(4, 3, 2, 1), com.homeworkoutcoach.app.ui.workout.repChoices(2))
    }
}
