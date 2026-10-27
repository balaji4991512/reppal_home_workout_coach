package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.room.RoomHabitRepository
import com.homeworkoutcoach.app.data.room.RoomRoutineRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.RoutineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

private val DAY = LocalDate.of(2026, 10, 28)

/** A routine with [steps] steps; the first one is linked to [linkedTo] if given. */
internal fun routine(
    id: String,
    name: String = id,
    part: DayPart = DayPart.MORNING,
    steps: Int = 3,
    position: Int = 0,
    linkedTo: String? = null,
) = Routine(id, name, part, List(steps) { RoutineItem("$id-$it", "Step $it of $name", if (it == 0) linkedTo else null) }, position)

/** Behaviour every [RoutineRepository] must have. Runs against Room and the in-memory fake. */
abstract class RoutineRepositoryContract {
    protected lateinit var repo: RoutineRepository

    abstract fun create(): RoutineRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<Routine>(), repo.routines().first())
        assertEquals(emptySet<RoutineDone>(), repo.done().first())
    }

    @Test
    fun aRoutineComesBackWithItsStepsInOrder() = runTest {
        val r = routine("m", "Morning", DayPart.MORNING, steps = 4)
        repo.save(r)
        assertEquals(listOf(r), repo.routines().first())
    }

    @Test
    fun everyPartOfTheDayIsKept() = runTest {
        val all = DayPart.entries.mapIndexed { i, p -> routine(p.name, part = p, position = i) }
        all.forEach { repo.save(it) }
        assertEquals(all, repo.routines().first())
    }

    @Test
    fun routinesAreListedByPositionThenName() = runTest {
        repo.save(routine("b", "Bravo", position = 1))
        repo.save(routine("c", "Charlie", position = 0))
        repo.save(routine("a", "Alpha", position = 1))
        assertEquals(listOf("c", "a", "b"), repo.routines().first().map { it.id })
    }

    @Test
    fun savingAgainReplacesTheStepsAndKeepsTheirNewOrder() = runTest {
        repo.save(routine("m", steps = 3))
        val edited = routine("m", "Morning", steps = 3).let { it.copy(items = it.items.reversed().drop(1) + RoutineItem("new", "Added")) }
        repo.save(edited)
        assertEquals(listOf(edited), repo.routines().first())
        assertEquals(listOf("m-1", "m-0", "new"), repo.routines().first().single().items.map { it.id })
    }

    @Test
    fun savingKeepsTheDaysItWasFinished() = runTest {
        repo.save(routine("m"))
        repo.setDone("m", DAY, true)
        repo.save(routine("m", "Renamed"))
        assertEquals(setOf(RoutineDone("m", DAY)), repo.done().first())
    }

    @Test
    fun aDayCanBeMarkedAndUnmarked() = runTest {
        repo.save(routine("m"))
        repo.setDone("m", DAY, true)
        repo.setDone("m", DAY, true) // twice is the same as once
        repo.setDone("m", DAY.minusDays(1), true)
        assertEquals(setOf(RoutineDone("m", DAY), RoutineDone("m", DAY.minusDays(1))), repo.done().first())
        repo.setDone("m", DAY, false)
        assertEquals(setOf(RoutineDone("m", DAY.minusDays(1))), repo.done().first())
        repo.setDone("m", DAY, false) // nothing to take back: quietly fine
    }

    @Test
    fun deletingARoutineDeletesItsStepsAndDaysOnly() = runTest {
        repo.save(routine("a"))
        repo.save(routine("b"))
        repo.setDone("a", DAY, true)
        repo.setDone("b", DAY, true)
        repo.delete("a")
        assertEquals(listOf("b"), repo.routines().first().map { it.id })
        assertEquals(setOf(RoutineDone("b", DAY)), repo.done().first())
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(routine("a"))
        repo.setDone("a", DAY, true)
        repo.wipe()
        assertTrue(repo.routines().first().isEmpty())
        assertTrue(repo.done().first().isEmpty())
    }

    @Test
    fun twoRoutinesMayUseTheSameStepId() = runTest {
        repo.save(Routine("a", "A", DayPart.MORNING, listOf(RoutineItem("s1", "One"))))
        repo.save(Routine("b", "B", DayPart.EVENING, listOf(RoutineItem("s1", "Uno"))))
        assertEquals(listOf("One", "Uno"), repo.routines().first().map { it.items.single().title })
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomRoutineRepositoryTest : RoutineRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): RoutineRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomRoutineRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aStepLinkedToAHabitLosesTheLinkWhenTheHabitIsDeleted() = runTest {
        val habits = RoomHabitRepository(db)
        habits.save(storedHabit("water"))
        repo.save(routine("m", linkedTo = "water"))
        assertEquals("water", repo.routines().first().single().items.first().habitId)
        habits.delete("water")
        val after = repo.routines().first().single()
        assertNull(after.items.first().habitId)
        assertEquals(3, after.items.size) // the step itself stays
    }
}

class InMemoryRoutineRepositoryTest : RoutineRepositoryContract() {
    override fun create(): RoutineRepository = InMemoryRoutineRepository()
}
