package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.ui.workout.WorkoutEditViewModel
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEditViewModelTest {
    private val lib = Library.default
    private val training = InMemoryTrainingRepository()
    private val settings = InMemorySettingsRepository(UserSettings(started = true, bench = Bench.FLAT, dumbbellsKg = lib.dumbbellStepsKg))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long) = WorkoutEditViewModel(id, lib, Planner(lib), training, settings, ZoneOffset.UTC)

    private fun benchWorkout(at: Long) = Workout(
        dateMillis = at, label = "A", plan = listOf(PlannedExercise("bench", 3, 12, 4.5)),
        sets = mapOf("bench" to List(3) { LoggedSet(12, 4.5, 12) }),
    )

    @Test
    fun savingACorrectionUpdatesTheWorkoutAndWorksTheNextLevelOutAgain() = runTest {
        val id = training.saveWorkout(benchWorkout(1_000), mapOf("bench" to ExerciseState(5.5, 11, 3)))
        val vm = vm(id)
        assertEquals("Full body A, Thu, 1 Jan", vm.draft.value!!.title)

        // it was really done at 8 kg, not 4.5: six steps up the set (5.5, 6.5, 8) for every set
        repeat(3) { set -> repeat(3) { vm.weight(0, set, +1) } }
        var done = false
        vm.save { done = true }
        assertEquals(true, done)

        val saved = training.workouts().first().single()
        assertEquals(listOf(8.0, 8.0, 8.0), saved.sets.getValue("bench").map { it.weightKg })
        assertEquals(ExerciseState(9.0, 12, 3), training.exerciseStates().first()["bench"])
    }

    @Test
    fun anUnchangedWorkoutIsLeftAlone() = runTest {
        val id = training.saveWorkout(benchWorkout(1_000), mapOf("bench" to ExerciseState(5.5, 11, 3)))
        val vm = vm(id)
        vm.reps(0, 0, +1)
        vm.reps(0, 0, -1)
        var done = false
        vm.save { done = true }
        assertEquals(true, done)
        assertEquals(ExerciseState(5.5, 11, 3), training.exerciseStates().first()["bench"])
    }

    @Test
    fun anOlderWorkoutCorrectedKeepsTheLevelTheLaterOneMade() = runTest {
        val first = training.saveWorkout(benchWorkout(1_000), mapOf("bench" to ExerciseState(5.5, 11, 3)))
        training.saveWorkout(benchWorkout(2_000), mapOf("bench" to ExerciseState(6.5, 11, 3)))
        val vm = vm(first)
        repeat(3) { set -> vm.reps(0, set, -2) }
        vm.save { }
        // the history is corrected, but today's level comes from the latest workout
        assertEquals(listOf(10, 10, 10), training.workouts().first().first().sets.getValue("bench").map { it.reps })
        assertEquals(ExerciseState(6.5, 11, 3), training.exerciseStates().first()["bench"])
    }

    @Test
    fun aWorkoutThatIsGoneGivesNoDraft() = runTest {
        assertNull(vm(999).draft.value)
    }
}
