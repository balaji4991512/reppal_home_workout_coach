package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.app.data.room.RoomTrainingRepository
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Volume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Behaviour every [TrainingRepository] must have. Runs against Room and the in-memory fake. */
abstract class TrainingRepositoryContract {
    protected lateinit var repo: TrainingRepository

    abstract fun create(): TrainingRepository

    @Before
    fun setUp() {
        repo = create()
    }

    private fun workout(date: Long, label: String = "A") = Workout(
        dateMillis = date,
        label = label,
        plan = listOf(PlannedExercise("bench", 3, 12, 5.0), PlannedExercise("pushup", 3, 12, 0.0)),
        sets = linkedMapOf(
            "bench" to listOf(
                LoggedSet(12, 5.0, 12, Effort.EASY),
                LoggedSet(11, 6.0, 11, Effort.JUST_RIGHT, "left wrist tight"),
                LoggedSet(9, 6.0, 11, Effort.HARD),
            ),
            "pushup" to listOf(LoggedSet(8, 0.0, 12, Effort.HARD, "sharp pain")),
        ),
        painIds = setOf("pushup"),
    )

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyMap<String, ExerciseState>(), repo.exerciseStates().first())
        assertEquals(emptyMap<String, String>(), repo.swaps().first())
        assertEquals(emptyList<Workout>(), repo.workouts().first())
    }

    @Test
    fun exerciseStatesMergeAndClear() = runTest {
        repo.putExerciseStates(mapOf("bench" to ExerciseState(5.0), "row" to ExerciseState(8.0, 10, 3)))
        repo.putExerciseStates(mapOf("bench" to ExerciseState(6.0, 11, 4)))
        assertEquals(
            mapOf("bench" to ExerciseState(6.0, 11, 4), "row" to ExerciseState(8.0, 10, 3)),
            repo.exerciseStates().first(),
        )
        repo.clearExerciseStates()
        assertTrue(repo.exerciseStates().first().isEmpty())
    }

    @Test
    fun swapsSetReplaceAndRemove() = runTest {
        repo.setSwap("bench", "floor")
        repo.setSwap("row", "brow")
        repo.setSwap("bench", "pushup")
        assertEquals(mapOf("bench" to "pushup", "row" to "brow"), repo.swaps().first())
        repo.setSwap("bench", "bench") // back to the base exercise
        repo.setSwap("row", null)
        assertTrue(repo.swaps().first().isEmpty())
    }

    @Test
    fun workoutRoundTripsExactly() = runTest {
        val original = workout(1_000)
        val id = repo.saveWorkout(original, emptyMap())
        val stored = repo.workouts().first().single()
        assertEquals(original.copy(id = id), stored)
        assertEquals(listOf("bench", "pushup"), stored.sets.keys.toList())
        // the summary from stored data matches the one from the live session
        val lib = Library.default
        assertEquals(
            Volume.summarize(lib, original.plan, original.sets, original.painIds),
            Volume.summarize(lib, stored.plan, stored.sets, stored.painIds),
        )
    }

    @Test
    fun aMadeUpWorkoutRemembersTheDayItMakesUp() = runTest {
        val makeUp = workout(2_000).copy(madeUpForEpochDay = 20_000)
        repo.saveWorkout(makeUp, emptyMap())
        repo.saveWorkout(workout(3_000), emptyMap())
        val stored = repo.workouts().first()
        assertEquals(20_000L, stored[0].madeUpForEpochDay)
        assertEquals(null, stored[1].madeUpForEpochDay) // an ordinary workout makes nothing up
    }

    @Test
    fun saveWorkoutAlsoSavesNextStates() = runTest {
        repo.putExerciseStates(mapOf("row" to ExerciseState(8.0)))
        repo.saveWorkout(workout(1_000), mapOf("bench" to ExerciseState(6.0, 11, 3)))
        assertEquals(
            mapOf("row" to ExerciseState(8.0), "bench" to ExerciseState(6.0, 11, 3)),
            repo.exerciseStates().first(),
        )
    }

    @Test
    fun workoutsAreOldestFirstWithDistinctIds() = runTest {
        val a = repo.saveWorkout(workout(3_000, "B"), emptyMap())
        val b = repo.saveWorkout(workout(1_000, "A"), emptyMap())
        val c = repo.saveWorkout(workout(3_000, "U1"), emptyMap())
        assertEquals(3, setOf(a, b, c).size)
        assertEquals(listOf("A", "B", "U1"), repo.workouts().first().map { it.label })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.putExerciseStates(mapOf("bench" to ExerciseState(5.0)))
        repo.setSwap("bench", "floor")
        repo.saveWorkout(workout(1_000), mapOf("row" to ExerciseState(8.0)))
        repo.wipe()
        assertTrue(repo.exerciseStates().first().isEmpty())
        assertTrue(repo.swaps().first().isEmpty())
        assertTrue(repo.workouts().first().isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomTrainingRepositoryTest : TrainingRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): TrainingRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomTrainingRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryTrainingRepositoryTest : TrainingRepositoryContract() {
    override fun create(): TrainingRepository = InMemoryTrainingRepository()
}
