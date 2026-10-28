package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Progress overview: habit completion over a range, bars, and what came before. 2026-10-26 is a Monday. */
class OverviewTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong()) // 0 = this Monday, -7 = last Monday

    private fun habit(
        schedule: HabitSchedule = HabitSchedule.Daily,
        start: Int = -400,
    ) = Habit("h", "Habit", HabitKind.DONE, 1, schedule, day(start))

    private fun fact(h: Habit, vararg doneDays: Int) = HabitFact(h, HabitLog(doneDays.associate { day(it) to 1 }))

    private fun inputs(
        habits: List<HabitFact> = emptyList(),
        workouts: List<WorkoutFact> = emptyList(),
        focus: List<FocusFact> = emptyList(),
    ) = CharacterInputs(null, workouts, habits, emptyList(), emptyList(), emptyList(), emptySet(), focus, emptySet())

    // ---- Ranges ----

    @Test
    fun rangesStartOnAMondayAndTheYearOnTheFirstOfAMonth() {
        assertEquals(mon, Overviews.startOf(OverviewRange.WEEK, day(3)))
        assertEquals(mon.minusWeeks(3), Overviews.startOf(OverviewRange.MONTH, day(3)))
        assertEquals(mon.minusWeeks(11), Overviews.startOf(OverviewRange.TWELVE_WEEKS, day(6)))
        assertEquals(LocalDate.of(2025, 11, 1), Overviews.startOf(OverviewRange.YEAR, day(3)))
    }

    @Test
    fun eachRangeHasItsNumberOfBarsOldestFirst() {
        val o = { r: OverviewRange -> Overviews.compute(inputs(), r, day(2)) }
        assertEquals(7, o(OverviewRange.WEEK).bars.size)
        assertEquals(4, o(OverviewRange.MONTH).bars.size)
        assertEquals(12, o(OverviewRange.TWELVE_WEEKS).bars.size)
        assertEquals(12, o(OverviewRange.YEAR).bars.size)
        assertEquals(mon, o(OverviewRange.WEEK).bars.first().from)
        assertEquals(DayOfWeek.MONDAY, o(OverviewRange.TWELVE_WEEKS).bars.last().from.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 1), o(OverviewRange.YEAR).bars.last().from)
        assertEquals(LocalDate.of(2025, 11, 30), o(OverviewRange.YEAR).bars.first().to)
    }

    // ---- Habit completion ----

    @Test
    fun theWeekCountsDaysDoneAgainstDaysDueAndTodayIsNeverMissed() {
        // Monday to Wednesday: done Monday and Wednesday (today), Tuesday missed.
        val o = Overviews.compute(inputs(listOf(fact(habit(), 0, 2))), OverviewRange.WEEK, day(2))
        assertEquals(2, o.habitsDone)
        assertEquals(3, o.habitsDue)
        assertEquals(66, o.percent)
        assertEquals(listOf(100, 0, 100), o.bars.take(3).map { it.percent })
        // days that have not happened have no bar height, not zero
        assertEquals(List(4) { null }, o.bars.drop(3).map { it.percent })
    }

    @Test
    fun todayNotYetDoneIsNotCountedAsMissed() {
        val o = Overviews.compute(inputs(listOf(fact(habit(), 0, 1))), OverviewRange.WEEK, day(2))
        assertEquals(2, o.habitsDone)
        assertEquals(2, o.habitsDue)
        assertNull(o.bars[2].percent)
    }

    @Test
    fun weeklyBarsCountEachCalendarWeekOnItsOwn() {
        // A daily habit done 5 of the 7 days last week and every day so far this week (Mon to Wed).
        val o = Overviews.compute(inputs(listOf(fact(habit(), -7, -6, -5, -4, -3, 0, 1, 2))), OverviewRange.MONTH, day(2))
        assertEquals(listOf(0, 0, 71, 100), o.bars.map { it.percent }) // the two older weeks had nothing done
    }

    @Test
    fun aFinishedWeekOfATimesAWeekHabitIsMetOrNot() {
        val h = habit(HabitSchedule.TimesPerWeek(3), start = -30)
        val met = Overviews.compute(inputs(listOf(fact(h, -7, -5, -3))), OverviewRange.MONTH, day(2))
        assertEquals(100, met.bars[2].percent)
        val short = Overviews.compute(inputs(listOf(fact(h, -7))), OverviewRange.MONTH, day(2))
        assertEquals(33, short.bars[2].percent)
    }

    @Test
    fun aHabitStartedLaterOnlyCountsFromItsStartDate() {
        val h = habit(start = 1) // starts Tuesday
        val o = Overviews.compute(inputs(listOf(fact(h, 1))), OverviewRange.WEEK, day(3))
        assertEquals(1, o.habitsDone)
        assertEquals(2, o.habitsDue) // Tuesday done, Wednesday over; Thursday (today) not counted
    }

    @Test
    fun dailyBarsLeaveOutHabitsThatRunSomeTimesAWeek() {
        val o = Overviews.compute(inputs(listOf(fact(habit(HabitSchedule.TimesPerWeek(3)), 0, 1))), OverviewRange.WEEK, day(2))
        assertEquals(List(7) { null }, o.bars.map { it.percent })
        // but the week as a whole counts it: 2 done so far (the week is not over, so nothing is behind yet)
        assertEquals(2, o.habitsDone)
    }

    @Test
    fun noHabitsMeansNoPercent() {
        val o = Overviews.compute(inputs(), OverviewRange.MONTH, day(2))
        assertNull(o.percent)
        assertNull(o.habitTrend)
        assertEquals(List(4) { null }, o.bars.map { it.percent })
    }

    // ---- Compared with before ----

    @Test
    fun theTrendComparesWithTheSameSpanBefore() {
        val h = habit()
        // this week Mon..Wed all done; last week 3 of 7 done: up
        val up = Overviews.compute(inputs(listOf(fact(h, -7, -6, -5, 0, 1, 2))), OverviewRange.WEEK, day(2))
        assertEquals(Trend.UP, up.habitTrend)
        // last week all done, this week one of three: quieter
        val quieter = Overviews.compute(inputs(listOf(fact(h, -7, -6, -5, -4, -3, -2, -1, 0))), OverviewRange.WEEK, day(2))
        assertEquals(Trend.QUIETER, quieter.habitTrend)
        // the same both weeks: about the same
        val same = Overviews.compute(inputs(listOf(fact(h, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2))), OverviewRange.WEEK, day(2))
        assertEquals(Trend.SAME, same.habitTrend)
    }

    @Test
    fun noTrendWithoutAnEarlierPeriodToCompareWith() {
        val h = habit(start = 0) // started this Monday: nothing was due the week before
        val o = Overviews.compute(inputs(listOf(fact(h, 0, 1))), OverviewRange.WEEK, day(2))
        assertNull(o.habitTrend)
    }

    // ---- Workouts and focus ----

    @Test
    fun workoutsAndFocusAreCountedInTheRangeAndTheOneBefore() {
        val i = inputs(
            workouts = listOf(WorkoutFact(day(-8)), WorkoutFact(day(-1)), WorkoutFact(day(0)), WorkoutFact(day(2))),
            focus = listOf(FocusFact(day(-3), 25), FocusFact(day(1), 25), FocusFact(day(2), 10)),
        )
        val o = Overviews.compute(i, OverviewRange.WEEK, day(2))
        assertEquals(2, o.workouts)
        assertEquals(1, o.workoutsBefore) // day(-8) is the Sunday before last week
        assertEquals(35, o.focusMinutes)
        assertEquals(2, o.focusSessions)
        assertEquals(25, o.focusMinutesBefore)
    }

    @Test
    fun theYearGroupsByCalendarMonth() {
        val h = habit(start = -400)
        // done every day in October 2026 up to Wednesday the 28th
        val days = (-25..2).toList() // Oct 1 .. Oct 28
        val o = Overviews.compute(inputs(listOf(fact(h, *days.toIntArray()))), OverviewRange.YEAR, day(2))
        assertEquals(100, o.bars.last().percent)
        assertEquals(0, o.bars[10].percent) // September: nothing done
    }
}
