package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryGoalRepository
import com.homeworkoutcoach.app.data.room.RoomGoalRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.GoalKind
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

internal fun goal(
    id: String,
    title: String = "Goal $id",
    kind: GoalKind = GoalKind.WORKOUTS,
    target: Double = 12.0,
    start: LocalDate = DAY,
    end: LocalDate = DAY.plusDays(29),
    habitId: String? = if (kind == GoalKind.HABIT) "read" else null,
    startWeightKg: Double? = if (kind == GoalKind.WEIGHT) 86.0 else null,
) = Goal(id, title, kind, if (kind == GoalKind.WEIGHT) 80.0 else target, start, end, habitId, startWeightKg)

/** Behaviour every [GoalRepository] must have. Runs against Room and the in-memory fake. */
abstract class GoalRepositoryContract {
    protected lateinit var repo: GoalRepository

    abstract fun create(): GoalRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<Goal>(), repo.goals().first())
    }

    @Test
    fun everyKindComesBackAsSaved() = runTest {
        val all = listOf(
            goal("w", kind = GoalKind.WORKOUTS, end = DAY.plusDays(10)),
            goal("h", kind = GoalKind.HABIT, end = DAY.plusDays(20)),
            goal("kg", kind = GoalKind.WEIGHT, end = DAY.plusDays(30)).copy(target = 79.5),
        )
        all.forEach { repo.save(it) }
        assertEquals(all, repo.goals().first())
    }

    @Test
    fun goalsAreListedBySoonestEndFirst() = runTest {
        repo.save(goal("late", end = DAY.plusDays(60)))
        repo.save(goal("soon", end = DAY.plusDays(7)))
        repo.save(goal("mid", end = DAY.plusDays(30)))
        assertEquals(listOf("soon", "mid", "late"), repo.goals().first().map { it.id })
    }

    @Test
    fun savingAgainReplacesTheGoal() = runTest {
        repo.save(goal("g", title = "First", target = 10.0))
        repo.save(goal("g", title = "Second", target = 20.0))
        val g = repo.goals().first().single()
        assertEquals("Second", g.title)
        assertEquals(20.0, g.target, 0.0)
    }

    @Test
    fun aHabitGoalKeepsItsRecordWhenTheHabitIsGone() = runTest {
        // the habit id is just text: nothing deletes the goal when the habit does
        repo.save(goal("h", kind = GoalKind.HABIT))
        assertEquals("read", repo.goals().first().single().habitId)
    }

    @Test
    fun deleteRemovesOnlyThatGoal() = runTest {
        repo.save(goal("a"))
        repo.save(goal("b"))
        repo.delete("a")
        repo.delete("nothing")
        assertEquals(listOf("b"), repo.goals().first().map { it.id })
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(goal("a"))
        repo.wipe()
        assertEquals(emptyList<Goal>(), repo.goals().first())
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomGoalRepositoryTest : GoalRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): GoalRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomGoalRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryGoalRepositoryTest : GoalRepositoryContract() {
    override fun create(): GoalRepository = InMemoryGoalRepository()
}
