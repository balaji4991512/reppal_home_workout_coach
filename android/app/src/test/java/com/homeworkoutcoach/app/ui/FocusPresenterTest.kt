package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.data.focusSession
import com.homeworkoutcoach.app.data.task
import com.homeworkoutcoach.app.ui.focus.FocusPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY = LocalDate.of(2026, 10, 28)
private val ZONE = ZoneOffset.UTC

private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(ZONE).toEpochMilli()

class FocusPresenterTest {
    private val presenter = FocusPresenter()

    @Test
    fun anEmptyDay() {
        val ui = presenter.present(null, emptyList(), emptyList(), TODAY, ZONE)
        assertNull(ui.running)
        assertEquals("No sessions yet today", ui.today)
        assertEquals("0 min this week", ui.week)
        assertEquals("0 min", ui.todayValue)
        assertEquals("No sessions yet", ui.todayNote)
        assertEquals("0 min", ui.weekValue)
        assertTrue(ui.recent.isEmpty() && ui.taskChoices.isEmpty())
        assertEquals(listOf(15, 25, 45, 60), ui.presets)
    }

    @Test
    fun totalsAndTheRecentList() {
        val sessions = listOf(
            focusSession("a", at(TODAY, 9, 30), planned = 25),
            focusSession("b", at(TODAY, 14), planned = 45, actual = 20, task = "Write the report"),
            focusSession("c", at(TODAY.minusDays(1), 16), planned = 60),
            focusSession("d", at(TODAY.minusDays(3), 10), planned = 30), // the Sunday before: last week
        )
        val ui = presenter.present(null, sessions, emptyList(), TODAY, ZONE)
        assertEquals("45 min today, 2 sessions", ui.today)
        assertEquals("1 h 45 min this week", ui.week)
        assertEquals("45 min", ui.todayValue)
        assertEquals("2 sessions", ui.todayNote)
        assertEquals("1 h 45 min", ui.weekValue)
        assertEquals(listOf("Today 2:00 pm", "Today 9:30 am", "Yesterday 4:00 pm", "Sun 25 Oct 10:00 am"), ui.recent.map { it.whenText })
        assertEquals(listOf("20 of 45 min", "25 min", "1 h", "30 min"), ui.recent.map { it.lengthText })
        assertEquals("Write the report", ui.recent.first().note)
        assertEquals(listOf(false, true, true, true), ui.recent.map { it.completed })
    }

    @Test
    fun oneSessionReadsInTheSingular() {
        val ui = presenter.present(null, listOf(focusSession("a", at(TODAY, 9), planned = 25)), emptyList(), TODAY, ZONE)
        assertEquals("25 min today, 1 session", ui.today)
        assertEquals("1 session", ui.todayNote)
    }

    @Test
    fun aRunningSessionCarriesItsTimes() {
        val ui = presenter.present(ActiveFocus(1_000, 25, "Write the report"), emptyList(), emptyList(), TODAY, ZONE)
        val r = ui.running!!
        assertEquals("Write the report", r.title)
        assertEquals("25 min", r.plannedText)
        assertEquals(1_000L, r.startMillis)
        assertEquals(1_000L + 25 * 60_000, r.endMillis)
    }

    @Test
    fun onlyOpenTasksForTodayOrEarlierAreOffered() {
        val tasks = listOf(
            task("a", "Write the report", date = TODAY),
            task("b", "Done already", date = TODAY, done = true),
            task("c", "Tomorrow's thing", date = TODAY.plusDays(1)),
            task("d", "Left over", date = TODAY.minusDays(2)),
            task("e", "Write the report", date = TODAY.minusDays(1)), // same title: listed once
        )
        val ui = presenter.present(null, emptyList(), tasks, TODAY, ZONE)
        assertEquals(listOf("Left over", "Write the report"), ui.taskChoices)
    }

    @Test
    fun atMostSixTasksAreOffered() {
        val tasks = (1..9).map { task("t$it", "Task $it", createdAt = it.toLong()) }
        assertEquals(6, presenter.present(null, emptyList(), tasks, TODAY, ZONE).taskChoices.size)
    }
}
