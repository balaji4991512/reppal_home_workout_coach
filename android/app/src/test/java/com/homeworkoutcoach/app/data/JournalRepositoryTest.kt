package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
import com.homeworkoutcoach.app.data.room.RoomJournalRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

private val DAY = LocalDate.of(2026, 10, 28)

/** Behaviour every [JournalRepository] must have. Runs against Room and the in-memory fake. */
abstract class JournalRepositoryContract {
    protected lateinit var repo: JournalRepository

    abstract fun create(): JournalRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<JournalEntry>(), repo.entries().first())
    }

    @Test
    fun anEntryComesBackAsSaved() = runTest {
        val e = JournalEntry(DAY, "Finished the report", "Start earlier")
        repo.save(e)
        assertEquals(listOf(e), repo.entries().first())
    }

    @Test
    fun aDayWithOnlyOneLineIsKept() = runTest {
        repo.save(JournalEntry(DAY, "", "Less phone"))
        repo.save(JournalEntry(DAY.minusDays(1), "A good walk", ""))
        assertEquals(listOf("", "A good walk"), repo.entries().first().map { it.win })
    }

    @Test
    fun savingTheSameDayReplacesIt() = runTest {
        repo.save(JournalEntry(DAY, "first"))
        repo.save(JournalEntry(DAY, "second", "better"))
        assertEquals(listOf(JournalEntry(DAY, "second", "better")), repo.entries().first())
    }

    @Test
    fun entriesAreListedNewestDayFirst() = runTest {
        repo.save(JournalEntry(DAY.minusDays(5), "old"))
        repo.save(JournalEntry(DAY, "new"))
        repo.save(JournalEntry(DAY.minusDays(1), "mid"))
        assertEquals(listOf("new", "mid", "old"), repo.entries().first().map { it.win })
    }

    @Test
    fun deleteRemovesOnlyThatDay() = runTest {
        repo.save(JournalEntry(DAY, "a"))
        repo.save(JournalEntry(DAY.minusDays(1), "b"))
        repo.delete(DAY)
        repo.delete(DAY.plusDays(9)) // nothing there: quietly fine
        assertEquals(listOf("b"), repo.entries().first().map { it.win })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(JournalEntry(DAY, "a"))
        repo.wipe()
        assertEquals(emptyList<JournalEntry>(), repo.entries().first())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomJournalRepositoryTest : JournalRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): JournalRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomJournalRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryJournalRepositoryTest : JournalRepositoryContract() {
    override fun create(): JournalRepository = InMemoryJournalRepository()
}
