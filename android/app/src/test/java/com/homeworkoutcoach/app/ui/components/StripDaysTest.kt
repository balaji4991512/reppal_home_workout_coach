package com.homeworkoutcoach.app.ui.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The week strip every day-based page shares. 2026-10-28 is a Wednesday. */
class StripDaysTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun theWeekRunsMondayToSundayAroundTheDayShown() {
        val days = StripDays.week(today, today)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), days.map { it.label })
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 1), days.map { it.number })
        assertEquals(LocalDate.of(2026, 10, 26), days.first().date)
        assertEquals(LocalDate.of(2026, 11, 1), days.last().date)
    }

    @Test
    fun theDayShownIsSelectedAndTodayIsMarkedSeparately() {
        val days = StripDays.week(today.minusDays(2), today) // Monday
        assertEquals(listOf(true, false, false, false, false, false, false), days.map { it.selected })
        assertEquals(listOf(false, false, true, false, false, false, false), days.map { it.isToday })
    }

    @Test
    fun anotherWeekIsShownWhenTheDayShownIsInIt() {
        val days = StripDays.week(today.minusDays(7), today)
        assertEquals(LocalDate.of(2026, 10, 19), days.first().date)
        assertTrue(days.none { it.isToday })
        assertEquals(LocalDate.of(2026, 10, 21), days.single { it.selected }.date)
    }

    @Test
    fun daysAfterTheLastSelectableOneAreNotEnabled() {
        val days = StripDays.week(today, today, lastSelectable = today)
        assertEquals(listOf(true, true, true, false, false, false, false), days.map { it.enabled })
        assertTrue(StripDays.week(today, today).all { it.enabled }) // no limit
    }

    @Test
    fun badgesLandOnTheirDays() {
        val days = StripDays.week(today, today, badges = mapOf(today to DayBadge("2/4"), today.minusDays(1) to DayBadge("3/3", true)))
        assertEquals("3/3", days[1].badge!!.text)
        assertTrue(days[1].badge!!.complete)
        assertEquals("2/4", days[2].badge!!.text)
        assertFalse(days[2].badge!!.complete)
        assertNull(days[0].badge)
    }

    @Test
    fun theMonthIsNamedInFull() {
        assertEquals("October", StripDays.month(today))
        assertEquals("November", StripDays.month(LocalDate.of(2026, 11, 1)))
    }
}
