package com.homeworkoutcoach.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTest {
    private val day = LocalDate.of(2026, 10, 28)

    @Test
    fun anEntryNeedsAtLeastOneLine() {
        assertEquals("Write at least one line.", Journal.problem("", ""))
        assertEquals("Write at least one line.", Journal.problem("  ", "\n"))
        assertNull(Journal.problem("Finished the report", ""))
        assertNull(Journal.problem("", "Slept late"))
        assertNull(Journal.problem("a", "b"))
        assertThrows(IllegalArgumentException::class.java) { JournalEntry(day, " ", "") }
    }

    @Test
    fun eachLineHasALimitThatIgnoresSurroundingSpaces() {
        assertNull(Journal.problem("w".repeat(200), "i".repeat(200)))
        assertEquals("Keep each line to 200 characters.", Journal.problem("w".repeat(201), ""))
        assertEquals("Keep each line to 200 characters.", Journal.problem("ok", "i".repeat(201)))
        assertNull(Journal.problem("  " + "w".repeat(200) + "  ", ""))
    }

    @Test
    fun createTrimsTheLines() {
        assertEquals(JournalEntry(day, "Walked 5 km", "Less phone"), Journal.create(day, " Walked 5 km ", "\nLess phone "))
        assertEquals(JournalEntry(day, "", "Less phone"), Journal.create(day, "  ", "Less phone"))
    }

    @Test
    fun entriesAreFoundByDayAndListedNewestFirst() {
        val a = JournalEntry(day.minusDays(2), "a")
        val b = JournalEntry(day, "b")
        val c = JournalEntry(day.minusDays(1), "c")
        assertEquals(listOf(b, c, a), Journal.newestFirst(listOf(a, b, c)))
        assertEquals(c, Journal.forDay(listOf(a, b, c), day.minusDays(1)))
        assertNull(Journal.forDay(listOf(a, b, c), day.plusDays(1)))
    }

    @Test
    fun theJournalHabitTicksOnDaysWithAnEntry() {
        val days = Journal.days(listOf(JournalEntry(day, "x"), JournalEntry(day.minusDays(3), "y")))
        assertEquals(setOf(day, day.minusDays(3)), days)
        val habit = Habit("j", "Journal", HabitKind.DONE, 1, HabitSchedule.Daily, day.minusDays(10), AutoSource.JOURNAL)
        val log = HabitLog(autoDays = days)
        assertTrue(Habits.isDone(habit, log, day))
        assertTrue(!Habits.isDone(habit, log, day.minusDays(1)))
    }

    @Test
    fun thePromptsAreTheOnesInTheSpec() {
        assertEquals("One win today", Journal.WIN_PROMPT)
        assertEquals("One thing to do better", Journal.IMPROVE_PROMPT)
    }
}
