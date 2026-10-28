package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {
    private val zone = ZoneOffset.UTC
    private val min = FocusRun.MINUTE_MS
    private val start = 1_000_000L * 60 // some start, in millis

    private fun run(minutes: Int = 25, task: String? = null) = FocusRun(start, minutes, task)

    @Test
    fun theEndIsTheStartPlusTheLength() {
        assertEquals(start + 25 * min, run(25).endMillis)
    }

    @Test
    fun theLengthMustBeOneToOneEighty() {
        assertNull(Focus.problem(1))
        assertNull(Focus.problem(180))
        assertEquals("Enter minutes from 1 to 180.", Focus.problem(0))
        assertEquals("Enter minutes from 1 to 180.", Focus.problem(181))
        assertEquals("Enter minutes from 1 to 180.", Focus.problem(null))
        assertEquals(listOf(15, 25, 45, 60), Focus.presets)
    }

    @Test
    fun remainingAndFinishedAndFraction() {
        val r = run(10)
        assertEquals(10 * min, Focus.remainingMillis(r, start))
        assertEquals(4 * min, Focus.remainingMillis(r, start + 6 * min))
        assertEquals(0, Focus.remainingMillis(r, start + 99 * min)) // never negative
        assertFalse(Focus.isFinished(r, start + 10 * min - 1))
        assertTrue(Focus.isFinished(r, start + 10 * min))
        assertEquals(0.0, Focus.fraction(r, start), 0.0)
        assertEquals(0.5, Focus.fraction(r, start + 5 * min), 1e-9)
        assertEquals(1.0, Focus.fraction(r, start + 20 * min), 0.0)
        assertEquals(0.0, Focus.fraction(r, start - 5), 0.0)
    }

    @Test
    fun theCountdownReadsAsMinutesAndSecondsRoundedUp() {
        assertEquals("25:00", Focus.format(25 * min))
        assertEquals("00:01", Focus.format(1))
        assertEquals("00:01", Focus.format(1000))
        assertEquals("00:02", Focus.format(1001))
        assertEquals("04:59", Focus.format(4 * min + 59_000))
        assertEquals("00:00", Focus.format(0))
        assertEquals("1:00:00", Focus.format(60 * min))
        assertEquals("1:05:09", Focus.format(65 * min + 9_000))
        assertEquals("3:00:00", Focus.format(180 * min))
    }

    @Test
    fun aSessionThatRanOutCountsInFull() {
        val s = Focus.finish(run(25, "Write the report"), start + 25 * min, "id")!!
        assertEquals(FocusSession("id", start, 25, 25, true, "Write the report"), s)
        // finishing late (the phone was off) still counts the planned time, no more
        assertEquals(25, Focus.finish(run(25), start + 400 * min, "id")!!.actualMinutes)
    }

    @Test
    fun aSessionStoppedEarlyKeepsTheWholeMinutesSpent() {
        val s = Focus.finish(run(25), start + 7 * min + 59_000, "id")!!
        assertEquals(7, s.actualMinutes)
        assertFalse(s.completed)
        assertEquals(25, s.plannedMinutes)
    }

    @Test
    fun underAMinuteIsTooShortToCount() {
        assertNull(Focus.finish(run(25), start + 59_999, "id"))
        assertNull(Focus.finish(run(25), start, "id"))
        assertEquals(1, Focus.finish(run(25), start + min, "id")!!.actualMinutes)
        // a clock set back never gives a negative session
        assertNull(Focus.finish(run(25), start - 10 * min, "id"))
    }

    @Test
    fun aOneMinuteSessionThatRanOutCounts() {
        assertEquals(1, Focus.finish(run(1), start + min, "id")!!.actualMinutes)
    }

    // ---- Totals ----

    private fun session(id: String, day: LocalDate, hour: Int, minutes: Int) =
        FocusSession(id, day.atTime(hour, 0).toInstant(zone).toEpochMilli(), minutes, minutes, true)

    @Test
    fun totalsByDayAndWeek() {
        val wed = LocalDate.of(2026, 10, 28)
        val all = listOf(
            session("a", wed, 9, 25), session("b", wed, 14, 45), session("c", wed.minusDays(2), 10, 60), // Monday
            session("d", wed.minusDays(3), 10, 30), // the Sunday before: last week
            session("e", wed.plusDays(5), 10, 15), // next Monday
        )
        assertEquals(70, Focus.minutesOn(all, wed, zone))
        assertEquals(2, Focus.sessionsOn(all, wed, zone))
        assertEquals(0, Focus.minutesOn(all, wed.plusDays(1), zone))
        assertEquals(130, Focus.minutesInWeek(all, wed, zone))
        assertEquals(130, Focus.minutesInWeek(all, wed.minusDays(2), zone)) // Monday is in its own week
        assertEquals(30, Focus.minutesInWeek(all, wed.minusDays(3), zone)) // the Sunday ends the week before
    }

    @Test
    fun recentSessionsAreNewestFirst() {
        val d = LocalDate.of(2026, 10, 28)
        val all = listOf(session("old", d.minusDays(5), 9, 10), session("new", d, 9, 10), session("mid", d.minusDays(1), 9, 10))
        assertEquals(listOf("new", "mid"), Focus.recent(all, 2).map { it.id })
    }

    @Test
    fun aSessionMustBeValid() {
        assertThrows(IllegalArgumentException::class.java) { FocusSession("x", 0, 0, 0, true) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession("x", 0, 25, 0, false) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession("x", 0, 25, 26, true) }
        assertThrows(IllegalArgumentException::class.java) { FocusSession("x", 0, 181, 25, true) }
    }
}
