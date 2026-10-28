package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Habit rules from specs/HABITS_PLANNER_SPEC.md. 2026-10-26 is a Monday. */
class HabitsTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong()) // 0 = Monday .. 6 = Sunday

    private fun habit(
        schedule: HabitSchedule = HabitSchedule.Daily,
        kind: HabitKind = HabitKind.DONE,
        target: Int = 1,
        start: LocalDate = mon.minusDays(60),
        auto: AutoSource = AutoSource.NONE,
        archived: Boolean = false,
    ) = Habit("h", "Habit", kind, target, schedule, start, auto, archived)

    private fun ticks(vararg days: Int) = HabitLog(days.associate { day(it) to 1 })

    // ---- Dates ----

    @Test
    fun weekStartsOnMonday() {
        assertEquals(mon, Habits.weekStart(day(0)))
        assertEquals(mon, Habits.weekStart(day(6)))
        assertEquals(mon.plusDays(7), Habits.weekStart(day(7)))
    }

    // ---- Amounts ----

    @Test
    fun waterAtFiveOfEightIsNotDoneUntilThreeMoreAreAdded() {
        val water = habit(kind = HabitKind.COUNT, target = 8)
        val five = HabitLog(mapOf(day(0) to 5))
        assertFalse(Habits.isDone(water, five, day(0)))
        assertEquals(DayState.PARTIAL, Habits.dayState(water, five, day(0)))
        assertEquals(5.0 / 8, Habits.fraction(water, five, day(0)), 1e-9)
        val eight = HabitLog(mapOf(day(0) to Habits.adjusted(water, 5, 3)))
        assertTrue(Habits.isDone(water, eight, day(0)))
        assertEquals(DayState.DONE, Habits.dayState(water, eight, day(0)))
    }

    @Test
    fun amountsNeverGoBelowZeroAndTicksStayOnOrOff() {
        val water = habit(kind = HabitKind.COUNT, target = 8)
        assertEquals(0, Habits.adjusted(water, 1, -5))
        val journal = habit()
        assertEquals(1, Habits.adjusted(journal, 0, 1))
        assertEquals(1, Habits.adjusted(journal, 1, 1))
        assertEquals(0, Habits.adjusted(journal, 1, -1))
    }

    @Test
    fun fractionStopsAtOne() {
        val reading = habit(kind = HabitKind.TIME, target = 20)
        assertEquals(1.0, Habits.fraction(reading, HabitLog(mapOf(day(0) to 45)), day(0)), 0.0)
        assertEquals(0.0, Habits.fraction(reading, HabitLog(), day(0)), 0.0)
    }

    // ---- Due ----

    @Test
    fun weekdayHabitIsNotDueOnSaturdayAndDoesNotCountAgainstTheWeek() {
        val gym = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        val log = ticks(0, 2, 4)
        assertTrue(Habits.isDue(gym, log, day(0)))
        assertFalse(Habits.isDue(gym, log, day(1)))
        assertFalse(Habits.isDue(gym, log, day(5))) // Saturday
        assertEquals(DayState.NOT_DUE, Habits.dayState(gym, HabitLog(), day(5)))
        assertEquals(WeekProgress(3, 3), Habits.weekProgress(gym, log, day(5)))
        assertTrue(Habits.weekProgress(gym, log, day(5)).complete)
    }

    @Test
    fun threeTimesAWeekIsDoneAfterThreeTicksAndNotDueThursday() {
        val run = habit(HabitSchedule.TimesPerWeek(3))
        val log = ticks(0, 1, 2) // Mon, Tue, Wed
        assertTrue(Habits.isDue(run, log, day(2))) // Wednesday stays due: it is the day that was ticked
        assertEquals(DayState.DONE, Habits.dayState(run, log, day(2)))
        assertFalse(Habits.isDue(run, log, day(3))) // Thursday
        assertEquals(WeekProgress(3, 3), Habits.weekProgress(run, log, day(3)))
        // next week starts fresh
        assertTrue(Habits.isDue(run, log, day(7)))
    }

    @Test
    fun timesAWeekStaysDueUntilTheCountIsMet() {
        val run = habit(HabitSchedule.TimesPerWeek(3))
        val log = ticks(0, 1)
        assertTrue(Habits.isDue(run, log, day(3)))
        assertTrue(Habits.isDue(run, log, day(6)))
        assertEquals(WeekProgress(2, 3), Habits.weekProgress(run, log, day(6)))
        // ticked anyway on a day when it was not due: the day still shows as done
        val extra = ticks(0, 1, 2, 3)
        assertEquals(DayState.DONE, Habits.dayState(run, extra, day(3)))
    }

    @Test
    fun nothingIsDueBeforeTheStartDateOrAfterArchiving() {
        val h = habit(start = day(2))
        assertFalse(Habits.isDue(h, HabitLog(), day(1)))
        assertTrue(Habits.isDue(h, HabitLog(), day(2)))
        assertFalse(Habits.isDue(habit(archived = true), HabitLog(), day(2)))
    }

    @Test
    fun firstWeekTargetOnlyCountsDaysFromTheStartDate() {
        val h = habit(start = day(4)) // starts Friday: Fri, Sat, Sun
        assertEquals(WeekProgress(0, 3), Habits.weekProgress(h, HabitLog(), day(4)))
        val run = habit(HabitSchedule.TimesPerWeek(5), start = day(4))
        assertEquals(3, Habits.weekProgress(run, HabitLog(), day(4)).target)
    }

    // ---- Stats: never "missed" for what hasn't happened ----

    @Test
    fun todayIsNotCountedAsMissedUntilItIsDone() {
        val h = habit(start = day(0))
        // Monday and Tuesday done, today is Wednesday and not yet ticked: 2 of 2
        assertEquals(HabitStats(2, 2), Habits.stats(h, ticks(0, 1), mon, day(2)))
        // ticking today counts it
        assertEquals(HabitStats(3, 3), Habits.stats(h, ticks(0, 1, 2), mon, day(2)))
        // a past day left undone is counted as due
        assertEquals(HabitStats(1, 2), Habits.stats(h, ticks(1), mon, day(2)))
    }

    @Test
    fun weekdayStatsSkipDaysThatAreNotScheduled() {
        val gym = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), start = day(0))
        assertEquals(HabitStats(1, 2), Habits.stats(gym, ticks(0), mon, day(4)))
        // done on an unscheduled day counts on both sides, so done never passes due
        assertEquals(HabitStats(2, 3), Habits.stats(gym, ticks(0, 1), mon, day(4)))
    }

    @Test
    fun timesAWeekStatsOnlyCountAFinishedWeekInFull() {
        val run = habit(HabitSchedule.TimesPerWeek(3), start = mon)
        val log = HabitLog((listOf(0, 1) + listOf(7, 8, 9)).associate { day(it) to 1 })
        // today is Wednesday of week 2: week 1 finished with 2 of 3, week 2 so far has 3 of 3
        assertEquals(HabitStats(5, 6), Habits.stats(run, log, mon, day(9)))
        // on the first day of a new week with nothing done yet, that week is not behind
        assertEquals(HabitStats(2, 3), Habits.stats(run, ticks(0, 1), mon, day(7)))
        assertEquals(HabitStats(0, 0), Habits.stats(run, log, mon, day(-1)))
    }

    @Test
    fun aPastPeriodCountsItsLastDayLikeAnyOther() {
        val h = habit(start = mon.minusDays(30))
        // the week Mon..Sun before this one, nothing done on its Sunday: 6 of 7, with today well after it
        val log = ticks(-7, -6, -5, -4, -3, -2)
        assertEquals(HabitStats(6, 7), Habits.stats(h, log, day(-7), day(2), until = day(-1)))
        // without [until] the range runs to today, which is not counted as missed
        assertEquals(HabitStats(6, 9), Habits.stats(h, log, day(-7), day(2)))
    }

    @Test
    fun doneDaysCountsARange() {
        assertEquals(2, Habits.doneDays(habit(), ticks(0, 3), day(0), day(6)))
        assertEquals(1, Habits.doneDays(habit(), ticks(0, 3), day(1), day(6)))
    }

    // ---- Ticking itself ----

    @Test
    fun anAutoTickCountsAsDoneAndAHandTickOverridesIt() {
        val workout = habit(auto = AutoSource.WORKOUT)
        val log = HabitLog(autoDays = setOf(day(0), day(1)))
        assertTrue(Habits.isDone(workout, log, day(0)))
        assertFalse(Habits.isDone(workout, log, day(2)))
        // unticked by hand: an entry of 0 wins over the auto tick
        val unticked = log.copy(entries = mapOf(day(1) to 0))
        assertFalse(Habits.isDone(workout, unticked, day(1)))
        assertTrue(Habits.isDone(workout, unticked, day(0)))
        // ticked by hand on a day that did not tick itself
        val manual = log.copy(entries = mapOf(day(2) to 1))
        assertTrue(Habits.isDone(workout, manual, day(2)))
    }

    @Test
    fun anAutoTickCountsForNumberHabitsAsTheFullTarget() {
        val minutes = habit(kind = HabitKind.TIME, target = 30, auto = AutoSource.WORKOUT)
        assertEquals(30, Habits.amount(minutes, HabitLog(autoDays = setOf(day(0))), day(0)))
    }

    // ---- Checks and suggestions ----

    @Test
    fun badHabitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { habit(kind = HabitKind.COUNT, target = 0) }
        assertThrows(IllegalArgumentException::class.java) { habit(kind = HabitKind.DONE, target = 2) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.Weekdays(emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.TimesPerWeek(0)) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.TimesPerWeek(8)) }
    }

    @Test
    fun startingSuggestionsMatchTheSpec() {
        val s = Habits.suggestions
        assertEquals(
            listOf("Workout", "Morning routine", "Night routine", "Journal", "Water", "Sleep", "Reading", "Meditation", "Yoga", "Walking"),
            s.map { it.name },
        )
        assertEquals(
            listOf(AutoSource.WORKOUT, AutoSource.MORNING_ROUTINE, AutoSource.NIGHT_ROUTINE, AutoSource.JOURNAL),
            s.map { it.auto }.filter { it != AutoSource.NONE },
        )
        assertEquals(Habits.Suggestion("Water", HabitKind.COUNT, 8), s.first { it.name == "Water" })
        assertEquals(420, s.first { it.name == "Sleep" }.target) // 7 hours, in minutes
        // every suggestion makes a valid habit
        s.forEach { habit(kind = it.kind, target = it.target, auto = it.auto) }
    }
}
