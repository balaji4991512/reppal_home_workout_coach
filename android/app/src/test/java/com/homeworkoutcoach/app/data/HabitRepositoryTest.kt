package com.homeworkoutcoach.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.room.RoomHabitRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitLog
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Habits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

private val DAY = LocalDate.of(2026, 10, 27)

internal fun storedHabit(
    id: String,
    name: String = id,
    kind: HabitKind = HabitKind.DONE,
    target: Int = 1,
    schedule: HabitSchedule = HabitSchedule.Daily,
    auto: AutoSource = AutoSource.NONE,
    archived: Boolean = false,
    position: Int = 0,
) = StoredHabit(Habit(id, name, kind, target, schedule, DAY.minusDays(3), auto, archived), icon = "drop", color = "blue", position = position)

/** Behaviour every [HabitRepository] must have. Runs against Room and the in-memory fake. */
abstract class HabitRepositoryContract {
    protected lateinit var repo: HabitRepository

    abstract fun create(): HabitRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList<StoredHabit>(), repo.habits().first())
        assertEquals(emptyMap<String, Map<LocalDate, Int>>(), repo.entries().first())
    }

    @Test
    fun everyKindOfHabitComesBackAsSaved() = runTest {
        val all = listOf(
            storedHabit("water", kind = HabitKind.COUNT, target = 8, position = 0),
            storedHabit(
                "gym", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)),
                auto = AutoSource.WORKOUT, position = 1,
            ),
            storedHabit("walk", kind = HabitKind.TIME, target = 30, schedule = HabitSchedule.TimesPerWeek(4), position = 2),
            storedHabit("old", archived = true, position = 3),
        )
        all.forEach { repo.save(it) }
        assertEquals(all, repo.habits().first())
    }

    @Test
    fun habitsAreListedByPositionThenName() = runTest {
        repo.save(storedHabit("b", name = "Bravo", position = 1))
        repo.save(storedHabit("c", name = "Charlie", position = 0))
        repo.save(storedHabit("a", name = "Alpha", position = 1))
        assertEquals(listOf("c", "a", "b"), repo.habits().first().map { it.habit.id })
    }

    @Test
    fun savingAgainEditsTheHabitAndKeepsItsEntries() = runTest {
        repo.save(storedHabit("water", kind = HabitKind.COUNT, target = 8))
        repo.setAmount("water", DAY, 5)
        repo.save(storedHabit("water", name = "Drink water", kind = HabitKind.COUNT, target = 10))
        val habit = repo.habits().first().single()
        assertEquals("Drink water", habit.habit.name)
        assertEquals(10, habit.habit.target)
        assertEquals(mapOf("water" to mapOf(DAY to 5)), repo.entries().first())
    }

    @Test
    fun amountsAreKeptPerDayIncludingZero() = runTest {
        repo.save(storedHabit("water", kind = HabitKind.COUNT, target = 8))
        repo.setAmount("water", DAY, 5)
        repo.setAmount("water", DAY, 6)
        repo.setAmount("water", DAY.plusDays(1), 0)
        assertEquals(mapOf(DAY to 6, DAY.plusDays(1) to 0), repo.entries().first().getValue("water"))
        repo.clearAmount("water", DAY)
        assertEquals(mapOf(DAY.plusDays(1) to 0), repo.entries().first().getValue("water"))
        repo.clearAmount("water", DAY.plusDays(1))
        assertEquals(emptyMap<String, Map<LocalDate, Int>>(), repo.entries().first())
    }

    @Test
    fun anAmountNeverGoesBelowZero() = runTest {
        repo.save(storedHabit("water", kind = HabitKind.COUNT, target = 8))
        repo.setAmount("water", DAY, -3)
        assertEquals(0, repo.entries().first().getValue("water").getValue(DAY))
    }

    @Test
    fun deletingAHabitDeletesItsEntriesOnly() = runTest {
        repo.save(storedHabit("a"))
        repo.save(storedHabit("b"))
        repo.setAmount("a", DAY, 1)
        repo.setAmount("b", DAY, 1)
        repo.delete("a")
        assertEquals(listOf("b"), repo.habits().first().map { it.habit.id })
        assertEquals(setOf("b"), repo.entries().first().keys)
    }

    @Test
    fun wipeClearsEverything() = runTest {
        repo.save(storedHabit("a"))
        repo.setAmount("a", DAY, 1)
        repo.wipe()
        assertTrue(repo.habits().first().isEmpty())
        assertTrue(repo.entries().first().isEmpty())
    }

    @Test
    fun theHabitRulesReadWhatWasStored() = runTest {
        // end to end: store a habit and a hand entry, read both back and ask the rules
        repo.save(storedHabit("water", kind = HabitKind.COUNT, target = 8))
        repo.setAmount("water", DAY, 5)
        val habit = repo.habits().first().single().habit
        val log = HabitLog(repo.entries().first().getValue("water"))
        assertFalse(Habits.isDone(habit, log, DAY))
        repo.setAmount("water", DAY, 8)
        assertTrue(Habits.isDone(habit, HabitLog(repo.entries().first().getValue("water")), DAY))
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomHabitRepositoryTest : HabitRepositoryContract() {
    private lateinit var db: TrainingDatabase

    override fun create(): HabitRepository {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return RoomHabitRepository(db)
    }

    @After
    fun tearDown() = db.close()
}

class InMemoryHabitRepositoryTest : HabitRepositoryContract() {
    override fun create(): HabitRepository = InMemoryHabitRepository()
}

class HabitLogsTest {
    private val ist = ZoneOffset.ofHoursMinutes(5, 30)

    private fun workout(ms: Long) = Workout(dateMillis = ms, label = "A", plan = emptyList(), sets = emptyMap())

    @Test
    fun aWorkoutCountsOnItsLocalDay() {
        // 2026-10-27 23:30 IST is still the 27th in India, though it is the 27th 18:00 in UTC and the 28th in Tokyo
        val late = java.time.OffsetDateTime.of(2026, 10, 27, 23, 30, 0, 0, ist).toInstant().toEpochMilli()
        assertEquals(setOf(DAY), HabitLogs.workoutDays(listOf(workout(late)), ist))
        assertEquals(setOf(DAY.plusDays(1)), HabitLogs.workoutDays(listOf(workout(late)), ZoneOffset.ofHours(9)))
        assertEquals(setOf(DAY), HabitLogs.workoutDays(listOf(workout(late), workout(late + 1000)), ist))
    }

    @Test
    fun theRoutineHabitsTickOnDaysTheirRoutinesWereFinished() {
        val morning = com.homeworkoutcoach.engine.Routine("m", "Morning", com.homeworkoutcoach.engine.DayPart.MORNING, listOf(com.homeworkoutcoach.engine.RoutineItem("a", "A")))
        val evening = com.homeworkoutcoach.engine.Routine("e", "Evening", com.homeworkoutcoach.engine.DayPart.EVENING, listOf(com.homeworkoutcoach.engine.RoutineItem("b", "B")))
        val done = listOf(RoutineDone("m", DAY), RoutineDone("e", DAY.minusDays(1)))
        val days = HabitLogs.routineDays(listOf(morning, evening), done)
        assertEquals(setOf(DAY), days.getValue(AutoSource.MORNING_ROUTINE))
        assertEquals(setOf(DAY.minusDays(1)), days.getValue(AutoSource.NIGHT_ROUTINE))

        val morningHabit = storedHabit("mh", auto = AutoSource.MORNING_ROUTINE).habit
        val nightHabit = storedHabit("nh", auto = AutoSource.NIGHT_ROUTINE).habit
        assertTrue(Habits.isDone(morningHabit, HabitLogs.log(morningHabit, emptyMap(), emptySet(), days), DAY))
        assertFalse(Habits.isDone(nightHabit, HabitLogs.log(nightHabit, emptyMap(), emptySet(), days), DAY))
        assertTrue(Habits.isDone(nightHabit, HabitLogs.log(nightHabit, emptyMap(), emptySet(), days), DAY.minusDays(1)))
        // unticked by hand wins here too
        assertFalse(Habits.isDone(morningHabit, HabitLogs.log(morningHabit, mapOf(DAY to 0), emptySet(), days), DAY))
        // and without routines nothing ticks
        assertFalse(Habits.isDone(morningHabit, HabitLogs.log(morningHabit, emptyMap(), emptySet(), HabitLogs.routineDays(emptyList(), done)), DAY))
    }

    @Test
    fun onlyTheWorkoutHabitTicksItselfFromWorkouts() {
        val days = setOf(DAY)
        val workoutHabit = storedHabit("w", auto = AutoSource.WORKOUT).habit
        val other = storedHabit("j").habit
        val manual = mapOf(DAY.minusDays(1) to 1)
        assertEquals(HabitLog(manual, days), HabitLogs.log(workoutHabit, manual, days))
        assertEquals(HabitLog(manual, emptySet()), HabitLogs.log(other, manual, days))
        assertTrue(Habits.isDone(workoutHabit, HabitLogs.log(workoutHabit, emptyMap(), days), DAY))
        // unticked by hand wins
        assertFalse(Habits.isDone(workoutHabit, HabitLogs.log(workoutHabit, mapOf(DAY to 0), days), DAY))
    }
}
