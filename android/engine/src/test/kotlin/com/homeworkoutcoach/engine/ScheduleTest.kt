package com.homeworkoutcoach.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A workout belongs to its own weekday. 2026-10-26 is a Monday. */
class ScheduleTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong())

    // Monday, Wednesday, Friday
    private val mwf = listOf(0, 2, 4)

    private fun statuses(today: Int, done: Set<Int> = emptySet(), planStart: Int? = -30) =
        Schedule.week(mwf, day(today), done.map { day(it) }.toSet(), planStart?.let { day(it) }).map { it.status }

    @Test
    fun sessionsSitOnTheirOwnWeekdays() {
        val w = Schedule.week(mwf, day(2), emptySet(), day(-30))
        assertEquals(listOf(day(0), day(2), day(4)), w.map { it.date })
        assertEquals(listOf(0, 1, 2), w.map { it.index })
        assertEquals(listOf(0, 2, 4), w.map { it.weekday })
    }

    @Test
    fun theWeekIsTheOneTodayIsIn() {
        // on Sunday the week is still the one that began on Monday
        assertEquals(day(0), Schedule.week(mwf, day(6), emptySet(), day(-30)).first().date)
        // on the next Monday it is the new week
        assertEquals(day(7), Schedule.week(mwf, day(7), emptySet(), day(-30)).first().date)
    }

    @Test
    fun aDayThatPassedWithoutAWorkoutIsSkipped() {
        // today is Thursday: Monday done, Wednesday not
        assertEquals(listOf(SessionStatus.DONE, SessionStatus.SKIPPED, SessionStatus.UPCOMING), statuses(today = 3, done = setOf(0)))
        // nothing done at all
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.SKIPPED, SessionStatus.UPCOMING), statuses(today = 3))
    }

    @Test
    fun todayIsTodayUntilItIsDoneAndNeverSkippedYet() {
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.TODAY, SessionStatus.UPCOMING), statuses(today = 2))
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.DONE, SessionStatus.UPCOMING), statuses(today = 2, done = setOf(2)))
    }

    @Test
    fun aSkippedDayStaysSkippedEvenIfALaterDayIsDone() {
        // Monday was missed; doing Wednesday does not turn Monday into done or move anything
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.DONE, SessionStatus.UPCOMING), statuses(today = 2, done = setOf(2)))
    }

    @Test
    fun aWorkoutOnADifferentDayDoesNotCountForTheSession() {
        // a workout on Tuesday (a rest day) is not Monday's or Wednesday's
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.TODAY, SessionStatus.UPCOMING), statuses(today = 2, done = setOf(1)))
    }

    @Test
    fun daysBeforeThePlanWasMadeAreNotSkipped() {
        // plan made on Wednesday: Monday was never due
        assertEquals(listOf(SessionStatus.BEFORE_PLAN, SessionStatus.TODAY, SessionStatus.UPCOMING), statuses(today = 2, planStart = 2))
        // a workout done before the plan still counts as done
        assertEquals(listOf(SessionStatus.DONE, SessionStatus.TODAY, SessionStatus.UPCOMING), statuses(today = 2, done = setOf(0), planStart = 2))
    }

    @Test
    fun noPlanStartMeansNothingBeforeTodayIsSkipped() {
        assertEquals(listOf(SessionStatus.BEFORE_PLAN, SessionStatus.TODAY, SessionStatus.UPCOMING), statuses(today = 2, planStart = null))
    }

    @Test
    fun aNewWeekStartsFresh() {
        assertEquals(listOf(SessionStatus.TODAY, SessionStatus.UPCOMING, SessionStatus.UPCOMING), statuses(today = 7))
    }

    @Test
    fun todayAndNextAreFound() {
        val w = Schedule.week(mwf, day(2), emptySet(), day(-30))
        assertEquals(1, Schedule.today(w)!!.index)
        assertEquals(2, Schedule.next(w)!!.index)
        val rest = Schedule.week(mwf, day(1), setOf(day(0)), day(-30)) // Tuesday
        assertNull(Schedule.today(rest))
        assertEquals(1, Schedule.next(rest)!!.index)
        assertNull(Schedule.next(Schedule.week(mwf, day(5), emptySet(), day(-30)))) // Saturday: nothing left
    }

    @Test
    fun aRearrangedWeekFollowsItsOwnWeekdays() {
        val w = Schedule.week(listOf(1, 3, 5), day(3), emptySet(), day(-30)) // Tue, Thu, Sat
        assertEquals(listOf(SessionStatus.SKIPPED, SessionStatus.TODAY, SessionStatus.UPCOMING), w.map { it.status })
    }
}
