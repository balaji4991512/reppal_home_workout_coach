package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryReflectionRepository
import com.homeworkoutcoach.app.data.room.RoomReflectionRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.WeeklyReflection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

private val MON = LocalDate.of(2026, 10, 26)

internal fun reflection(week: LocalDate, wentWell: String = "Trained three times", differently: String = "Sleep earlier") =
    WeeklyReflection(week, wentWell, differently)

/** Behaviour every [ReflectionRepository] must have. Runs against Room and the in-memory fake. */
abstract class ReflectionRepositoryContract {
    protected lateinit var repo: ReflectionRepository

    abstract fun create(): ReflectionRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<WeeklyReflection>(), repo.reflections().first())
    }

    @Test
    fun aReflectionComesBackAsSaved() = runTest {
        val r = reflection(MON)
        repo.save(r)
        assertEquals(listOf(r), repo.reflections().first())
    }

    @Test
    fun aWeekWithOnlyOneAnswerIsKept() = runTest {
        repo.save(reflection(MON, wentWell = "", differently = "Less phone"))
        assertEquals("", repo.reflections().first().single().wentWell)
    }

    @Test
    fun savingTheSameWeekReplacesIt() = runTest {
        repo.save(reflection(MON, "first", "x"))
        repo.save(reflection(MON, "second", "y"))
        assertEquals(listOf(reflection(MON, "second", "y")), repo.reflections().first())
    }

    @Test
    fun weeksAreListedNewestFirst() = runTest {
        repo.save(reflection(MON.minusDays(14)))
        repo.save(reflection(MON))
        repo.save(reflection(MON.minusDays(7)))
        assertEquals(listOf(MON, MON.minusDays(7), MON.minusDays(14)), repo.reflections().first().map { it.weekStart })
    }

    @Test
    fun deleteRemovesOnlyThatWeek() = runTest {
        repo.save(reflection(MON))
        repo.save(reflection(MON.minusDays(7)))
        repo.delete(MON)
        repo.delete(MON.plusDays(70)) // nothing there: quietly fine
        assertEquals(listOf(MON.minusDays(7)), repo.reflections().first().map { it.weekStart })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(reflection(MON))
        repo.wipe()
        assertEquals(emptyList<WeeklyReflection>(), repo.reflections().first())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomReflectionRepositoryTest : ReflectionRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): ReflectionRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomReflectionRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryReflectionRepositoryTest : ReflectionRepositoryContract() {
    override fun create(): ReflectionRepository = InMemoryReflectionRepository()
}
