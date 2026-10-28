package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
import com.homeworkoutcoach.app.ui.journal.JournalActions
import com.homeworkoutcoach.app.ui.journal.JournalPresenter
import com.homeworkoutcoach.engine.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 28) // a Wednesday

class JournalPresenterTest {
    private val presenter = JournalPresenter()

    @Test
    fun theDayShownCarriesItsEntryAndTheOthersAreListed() {
        val all = listOf(
            JournalEntry(TODAY, "Finished the report", "Start earlier"),
            JournalEntry(TODAY.minusDays(1), "A walk", ""),
            JournalEntry(TODAY.minusDays(5), "", "Less phone"),
        )
        val ui = presenter.present(all, TODAY, TODAY)
        assertEquals("Today", ui.title)
        assertEquals("Wednesday 28 October", ui.subtitle)
        assertTrue(ui.isToday && ui.hasEntry)
        assertEquals("Finished the report", ui.win)
        assertEquals("Start earlier", ui.improve)
        assertEquals(listOf("Yesterday", "Fri 23 Oct"), ui.past.map { it.label })
        assertEquals("Written on 3 of the last 7 days.", ui.summary)
    }

    @Test
    fun anEarlierDayCanBeShownAndEdited() {
        val all = listOf(JournalEntry(TODAY, "now"), JournalEntry(TODAY.minusDays(1), "before", "x"))
        val ui = presenter.present(all, TODAY.minusDays(1), TODAY)
        assertFalse(ui.isToday)
        assertEquals("before", ui.win)
        assertEquals(listOf(TODAY), ui.past.map { it.date }) // the one shown is not listed again
    }

    @Test
    fun aDayWithNothingWrittenIsEmptyAndTheSummaryIsGentle() {
        val ui = presenter.present(emptyList(), TODAY, TODAY)
        assertFalse(ui.hasEntry)
        assertEquals("", ui.win)
        assertEquals("Two lines a day is plenty.", ui.summary)
        assertTrue(ui.past.isEmpty())
    }

    @Test
    fun onlyTheLastSevenDaysCountInTheSummary() {
        val all = listOf(JournalEntry(TODAY.minusDays(6), "in"), JournalEntry(TODAY.minusDays(7), "out"))
        assertEquals("Written on 1 of the last 7 days.", presenter.present(all, TODAY, TODAY).summary)
    }
}

class JournalActionsTest {
    private val repo = InMemoryJournalRepository()
    private val actions = JournalActions(repo)

    @Test
    fun savingTrimsAndKeepsOneEntryPerDay() = runTest {
        assertNull(actions.save(TODAY, " A win ", " Do better "))
        assertNull(actions.save(TODAY, "Second try", ""))
        assertEquals(listOf(JournalEntry(TODAY, "Second try", "")), repo.entries().first())
    }

    @Test
    fun anEmptyOrTooLongEntryIsRefused() = runTest {
        assertEquals("Write at least one line.", actions.save(TODAY, "  ", ""))
        assertEquals("Keep each line to 200 characters.", actions.save(TODAY, "w".repeat(201), ""))
        assertTrue(repo.entries().first().isEmpty())
    }

    @Test
    fun deleting() = runTest {
        actions.save(TODAY, "a", "")
        actions.delete(TODAY)
        assertTrue(repo.entries().first().isEmpty())
    }
}
