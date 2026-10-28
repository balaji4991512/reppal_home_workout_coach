package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.room.RoomFocusRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

internal fun focusSession(
    id: String,
    start: Long = 1_793_000_000_000L,
    planned: Int = 25,
    actual: Int = planned,
    task: String? = null,
) = FocusSession(id, start, planned, actual, completed = actual == planned, taskTitle = task)

/** Behaviour every [FocusRepository] must have. Runs against Room and the in-memory fake. */
abstract class FocusRepositoryContract {
    protected lateinit var repo: FocusRepository

    abstract fun create(): FocusRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<FocusSession>(), repo.sessions().first())
    }

    @Test
    fun aSessionComesBackAsSaved() = runTest {
        val full = focusSession("a", planned = 45, task = "Write the report")
        val early = focusSession("b", start = 1_793_100_000_000L, planned = 25, actual = 7)
        repo.save(full)
        repo.save(early)
        assertEquals(listOf(early, full), repo.sessions().first())
        assertEquals(false, repo.sessions().first().first().completed)
    }

    @Test
    fun sessionsAreListedNewestFirst() = runTest {
        repo.save(focusSession("old", start = 1_000))
        repo.save(focusSession("new", start = 3_000))
        repo.save(focusSession("mid", start = 2_000))
        assertEquals(listOf("new", "mid", "old"), repo.sessions().first().map { it.id })
    }

    @Test
    fun savingTheSameSessionTwiceKeepsOne() = runTest {
        repo.save(focusSession("a", planned = 25))
        repo.save(focusSession("a", planned = 25))
        assertEquals(1, repo.sessions().first().size)
    }

    @Test
    fun deleteRemovesOnlyThatSession() = runTest {
        repo.save(focusSession("a"))
        repo.save(focusSession("b", start = 5))
        repo.delete("a")
        repo.delete("nothing")
        assertEquals(listOf("b"), repo.sessions().first().map { it.id })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(focusSession("a"))
        repo.wipe()
        assertEquals(emptyList<FocusSession>(), repo.sessions().first())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomFocusRepositoryTest : FocusRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): FocusRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomFocusRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryFocusRepositoryTest : FocusRepositoryContract() {
    override fun create(): FocusRepository = InMemoryFocusRepository()
}
