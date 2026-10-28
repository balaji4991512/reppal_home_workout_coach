package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.room.RoomTaskRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

private val TODAY = LocalDate.of(2026, 10, 28)

internal fun task(
    id: String,
    title: String = "Task $id",
    date: LocalDate = TODAY,
    time: LocalTime? = null,
    note: String = "",
    done: Boolean = false,
    createdAt: Long = 1,
    endTime: LocalTime? = null,
) = Task(id, title, date, time, note, done, createdAt, endTime)

/** Behaviour every [TaskRepository] must have. Runs against Room and the in-memory fake. */
abstract class TaskRepositoryContract {
    protected lateinit var repo: TaskRepository

    abstract fun create(): TaskRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<Task>(), repo.tasks().first())
    }

    @Test
    fun aTaskComesBackAsSaved() = runTest {
        val t = task("a", "Call the bank", TODAY.plusDays(2), LocalTime.of(17, 30), "ask about the card", done = true, createdAt = 99)
        repo.save(t)
        assertEquals(listOf(t), repo.tasks().first())
    }

    @Test
    fun theTimeKeepsHoursAndMinutesAndANullStaysNull() = runTest {
        repo.save(task("early", time = LocalTime.of(0, 5)))
        repo.save(task("late", time = LocalTime.of(23, 59)))
        repo.save(task("none"))
        val byId = repo.tasks().first().associateBy { it.id }
        assertEquals(LocalTime.of(0, 5), byId.getValue("early").time)
        assertEquals(LocalTime.of(23, 59), byId.getValue("late").time)
        assertNull(byId.getValue("none").time)
    }

    @Test
    fun theEndTimeComesBackForBlocksAndDeadlines() = runTest {
        repo.save(task("block", time = LocalTime.of(15, 0), endTime = LocalTime.of(17, 0)))
        repo.save(task("deadline", endTime = LocalTime.of(23, 59)))
        repo.save(task("point", time = LocalTime.of(9, 0)))
        val byId = repo.tasks().first().associateBy { it.id }
        assertEquals(LocalTime.of(17, 0), byId.getValue("block").endTime)
        assertNull(byId.getValue("deadline").time)
        assertEquals(LocalTime.of(23, 59), byId.getValue("deadline").endTime)
        assertNull(byId.getValue("point").endTime)
    }

    @Test
    fun savingAgainReplacesTheTask() = runTest {
        repo.save(task("a", "First"))
        repo.save(task("a", "Second", done = true))
        assertEquals(listOf("Second"), repo.tasks().first().map { it.title })
        assertEquals(true, repo.tasks().first().single().done)
    }

    @Test
    fun tasksAreListedOldestDayFirstThenByWhenAdded() = runTest {
        repo.save(task("c", date = TODAY, createdAt = 5))
        repo.save(task("b", date = TODAY, createdAt = 2))
        repo.save(task("a", date = TODAY.minusDays(1), createdAt = 9))
        assertEquals(listOf("a", "b", "c"), repo.tasks().first().map { it.id })
    }

    @Test
    fun deleteRemovesOnlyThatTask() = runTest {
        repo.save(task("a"))
        repo.save(task("b"))
        repo.delete("a")
        repo.delete("nothing")
        assertEquals(listOf("b"), repo.tasks().first().map { it.id })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(task("a"))
        repo.wipe()
        assertEquals(emptyList<Task>(), repo.tasks().first())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomTaskRepositoryTest : TaskRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): TaskRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomTaskRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryTaskRepositoryTest : TaskRepositoryContract() {
    override fun create(): TaskRepository = InMemoryTaskRepository()
}
