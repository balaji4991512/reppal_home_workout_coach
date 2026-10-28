package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.task
import com.homeworkoutcoach.app.ui.planner.PlannerActions
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.planner.PlannerPresenter
import com.homeworkoutcoach.app.ui.planner.Timeline
import com.homeworkoutcoach.app.ui.planner.clock
import com.homeworkoutcoach.engine.TaskKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private val TODAY = LocalDate.of(2026, 10, 28) // a Wednesday

class PlannerPresenterTest {
    private val presenter = PlannerPresenter()

    @Test
    fun formatting() {
        assertEquals("12:00 am", PlannerFormat.time(LocalTime.of(0, 0)))
        assertEquals("12:05 pm", PlannerFormat.time(LocalTime.of(12, 5)))
        assertEquals("5:30 pm", PlannerFormat.time(LocalTime.of(17, 30)))
        assertEquals("9:00 am", PlannerFormat.time(LocalTime.of(9, 0)))
        assertEquals("11:59 pm", PlannerFormat.time(LocalTime.of(23, 59)))
        assertEquals("Today", PlannerFormat.dayTitle(TODAY, TODAY))
        assertEquals("Tomorrow", PlannerFormat.dayTitle(TODAY.plusDays(1), TODAY))
        assertEquals("Yesterday", PlannerFormat.dayTitle(TODAY.minusDays(1), TODAY))
        assertEquals("Fri 30 Oct", PlannerFormat.dayTitle(TODAY.plusDays(2), TODAY))
        assertEquals("Fri 1 Jan 2027", PlannerFormat.dayShort(LocalDate.of(2027, 1, 1), TODAY))
        assertEquals("Wednesday 28 October", PlannerFormat.dayLong(TODAY))
    }

    @Test
    fun theWeekStripRunsMondayToSundayWithEachDaysTasks() {
        // TODAY is Wednesday 28 October 2026
        val all = listOf(
            task("a", "A", date = TODAY, done = true),
            task("b", "B", date = TODAY),
            task("c", "C", date = TODAY.plusDays(2), done = true),
            task("d", "D", date = TODAY.minusDays(7)), // last week: not in the strip
        )
        val ui = presenter.present(all, TODAY, TODAY)
        assertEquals("October", ui.month)
        assertEquals("2026", ui.year)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), ui.week.map { it.label })
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 1), ui.week.map { it.number })
        assertEquals(listOf(0, 0, 1, 0, 1, 0, 0), ui.week.map { it.done })
        assertEquals(listOf(0, 0, 2, 0, 1, 0, 0), ui.week.map { it.total })
        assertEquals(listOf(false, false, true, false, false, false, false), ui.week.map { it.selected })
        assertEquals(listOf(false, false, true, false, false, false, false), ui.week.map { it.isToday })
    }

    @Test
    fun lookingAtAnotherDayMovesTheSelectionNotToday() {
        val ui = presenter.present(emptyList(), TODAY.plusDays(2), TODAY)
        assertEquals(listOf(false, false, false, false, true, false, false), ui.week.map { it.selected })
        assertEquals(2, ui.week.indexOfFirst { it.isToday })
    }

    @Test
    fun rowsCarryTheirMinutesForTheTimeline() {
        val all = listOf(task("a", time = LocalTime.of(17, 30)), task("b", time = LocalTime.of(0, 5)), task("c"))
        val ui = presenter.present(all, TODAY, TODAY)
        assertEquals(listOf(5, 1050, null), ui.tasks.map { it.minutes })
    }

    @Test
    fun blocksAndDeadlinesReadInWordsAndSitOnTheTimeline() {
        val all = listOf(
            task("point", time = LocalTime.of(9, 0), createdAt = 1),
            task("block", time = LocalTime.of(15, 0), endTime = LocalTime.of(17, 0), createdAt = 2),
            task("deadline", endTime = LocalTime.of(18, 30), createdAt = 3),
            task("plain", createdAt = 4),
        )
        val rows = presenter.present(all, TODAY, TODAY).tasks.associateBy { it.id }
        assertEquals("9:00 am", rows.getValue("point").time)
        assertEquals("3:00 pm - 5:00 pm", rows.getValue("block").time)
        assertEquals("by 6:30 pm", rows.getValue("deadline").time)
        assertNull(rows.getValue("plain").time)
        assertEquals(listOf(TaskKind.POINT, TaskKind.BLOCK, TaskKind.DEADLINE, TaskKind.ANYTIME), all.map { rows.getValue(it.id).kind })
        // a deadline sits at its end, a block at its start; no time means no place
        assertEquals(listOf(9 * 60, 15 * 60, 18 * 60 + 30, null), all.map { rows.getValue(it.id).sortMinutes })
        assertEquals(17 * 60, rows.getValue("block").endMinutes)
        assertEquals("3:00 pm", rows.getValue("block").startText)
        assertEquals("5:00 pm", rows.getValue("block").endText)
        assertNull(rows.getValue("deadline").startText)
    }

    @Test
    fun aRowKnowsWhereItStandsAgainstTheClock() {
        val rows = presenter.present(
            listOf(task("block", time = LocalTime.of(15, 0), endTime = LocalTime.of(17, 0)), task("point", time = LocalTime.of(9, 0))),
            TODAY, TODAY,
        ).tasks.associateBy { it.id }
        val half = rows.getValue("block").clock(TODAY, 16 * 60)!!
        assertEquals(0.5, half.fraction, 1e-9)
        assertEquals(60, half.minutesLeft)
        assertNull(rows.getValue("point").clock(TODAY, 600)) // no end, no clock
    }

    @Test
    fun lengthsReadAsHoursAndMinutes() {
        assertEquals("25 min", PlannerFormat.length(25))
        assertEquals("2 h", PlannerFormat.length(120))
        assertEquals("1 h 4 min", PlannerFormat.length(64))
    }

    @Test
    fun theNowMarkerSitsAfterTheTasksAtOrBeforeNow() {
        val timed = listOf(9 * 60, 12 * 60 + 30, 17 * 60)
        assertEquals(0, Timeline.nowIndex(timed, 8 * 60))
        assertEquals(1, Timeline.nowIndex(timed, 9 * 60)) // a task starting now is already under way
        assertEquals(1, Timeline.nowIndex(timed, 12 * 60))
        assertEquals(2, Timeline.nowIndex(timed, 14 * 60))
        assertEquals(3, Timeline.nowIndex(timed, 23 * 60 + 59))
        assertEquals(0, Timeline.nowIndex(emptyList(), 600))
        assertEquals(17 * 60 + 5, Timeline.minutesOf(LocalTime.of(17, 5)))
    }

    @Test
    fun theDayViewListsRowsInOrderWithTimes() {
        val all = listOf(
            task("milk", "Buy milk", createdAt = 1),
            task("call", "Call the bank", time = LocalTime.of(17, 30), note = "about the card", createdAt = 2),
            task("done", "Pay rent", time = LocalTime.of(9, 0), done = true, createdAt = 3),
        )
        val ui = presenter.present(all, TODAY, TODAY)
        assertEquals("Today", ui.title)
        assertEquals("Wednesday 28 October", ui.subtitle)
        assertTrue(ui.isToday)
        assertEquals(listOf("done", "call", "milk"), ui.tasks.map { it.id })
        assertEquals(listOf("9:00 am", "5:30 pm", null), ui.tasks.map { it.time })
        assertEquals("about the card", ui.tasks[1].note)
        assertEquals("1 of 3 done", ui.summary)
        assertTrue(ui.carriedOver.isEmpty())
    }

    @Test
    fun theSummaryReadsGently() {
        assertEquals("Nothing planned.", presenter.present(emptyList(), TODAY, TODAY).summary)
        val done = listOf(task("a", done = true), task("b", done = true))
        assertEquals("All done for today.", presenter.present(done, TODAY, TODAY).summary)
        val tomorrow = done.map { it.copy(date = TODAY.plusDays(1)) }
        assertEquals("All done.", presenter.present(tomorrow, TODAY.plusDays(1), TODAY).summary)
    }

    @Test
    fun carriedOverRowsSayWhereTheyCameFromAndShowOnTodayOnly() {
        val all = listOf(task("y", date = TODAY.minusDays(1)), task("old", date = TODAY.minusDays(5)), task("t"))
        val today = presenter.present(all, TODAY, TODAY)
        assertEquals(listOf("old", "y"), today.carriedOver.map { it.id })
        assertEquals(listOf("Fri 23 Oct", "Yesterday"), today.carriedOver.map { it.dayLabel })
        assertEquals(listOf("t"), today.tasks.map { it.id })
        assertNull(today.tasks.single().dayLabel)
        assertEquals("0 of 1 done", today.summary) // carried over tasks do not count against today
        assertTrue(presenter.present(all, TODAY.plusDays(1), TODAY).carriedOver.isEmpty())
    }
}

class PlannerActionsTest {
    private val repo = InMemoryTaskRepository()
    private var clock = 1000L
    private var next = 0
    private val actions = PlannerActions(repo, now = { clock++ }, newId = { "id${next++}" })

    @Test
    fun addingTrimsAndKeepsTheOrderAdded() = runTest {
        assertNull(actions.add("  Buy milk ", TODAY))
        assertNull(actions.add("Call mum", TODAY))
        val tasks = repo.tasks().first()
        assertEquals(listOf("Buy milk", "Call mum"), tasks.map { it.title })
        assertEquals(listOf("id0", "id1"), tasks.map { it.id })
        assertEquals(listOf(1000L, 1001L), tasks.map { it.createdAt })
        assertTrue(tasks.all { it.date == TODAY && it.time == null && !it.done })
    }

    @Test
    fun addingNothingOrTooMuchIsRefused() = runTest {
        assertEquals("Write what you need to do.", actions.add("   ", TODAY))
        assertEquals("Keep the title to 120 characters.", actions.add("x".repeat(121), TODAY))
        assertTrue(repo.tasks().first().isEmpty())
    }

    @Test
    fun tickingAndUnticking() = runTest {
        actions.add("a", TODAY)
        actions.toggle("id0")
        assertTrue(repo.tasks().first().single().done)
        actions.toggle("id0")
        assertFalse(repo.tasks().first().single().done)
        actions.toggle("nothing") // quietly ignored
    }

    @Test
    fun moveToTomorrowAndTodayFollowTheRules() = runTest {
        repo.save(task("left", date = TODAY.minusDays(1)))
        repo.save(task("now"))
        repo.save(task("done", done = true))
        actions.postpone("left", TODAY)
        actions.postpone("done", TODAY) // a done task stays put
        assertEquals(TODAY.plusDays(1), repo.tasks().first().first { it.id == "left" }.date)
        assertEquals(TODAY, repo.tasks().first().first { it.id == "done" }.date)

        repo.save(task("left2", date = TODAY.minusDays(2)))
        actions.moveToToday("left2", TODAY)
        assertEquals(TODAY, repo.tasks().first().first { it.id == "left2" }.date)
    }

    @Test
    fun editingSavesTheFormAndKeepsDoneAndOrder() = runTest {
        repo.save(task("a", "Old", done = true, createdAt = 42))
        assertNull(actions.update("a", " New ", " why ", TODAY.plusDays(2), LocalTime.of(8, 15)))
        val t = repo.tasks().first().single()
        assertEquals(task("a", "New", TODAY.plusDays(2), LocalTime.of(8, 15), "why", done = true, createdAt = 42), t)
    }

    @Test
    fun aBadEditChangesNothing() = runTest {
        repo.save(task("a", "Old"))
        assertEquals("Write what you need to do.", actions.update("a", "", "", TODAY, null))
        assertEquals("Keep the note to 500 characters.", actions.update("a", "x", "n".repeat(501), TODAY, null))
        assertEquals("Old", repo.tasks().first().single().title)
    }

    @Test
    fun theFormCreatesABlockAndASetEndIsKeptWhenEditing() = runTest {
        assertNull(actions.create(" Deep work ", "", TODAY, LocalTime.of(15, 0), LocalTime.of(17, 0)))
        val made = repo.tasks().first().single()
        assertEquals("Deep work", made.title)
        assertEquals(LocalTime.of(17, 0), made.endTime)
        assertEquals("id0", made.id)

        assertNull(actions.update("id0", "Deep work", "", TODAY, LocalTime.of(16, 0), LocalTime.of(18, 0)))
        assertEquals(LocalTime.of(18, 0), repo.tasks().first().single().endTime)
        assertNull(actions.update("id0", "Deep work", "", TODAY, LocalTime.of(16, 0), null)) // the end can be cleared
        assertNull(repo.tasks().first().single().endTime)
    }

    @Test
    fun anEndBeforeTheStartChangesNothing() = runTest {
        assertEquals("The end has to be after the start.", actions.create("Deep work", "", TODAY, LocalTime.of(17, 0), LocalTime.of(15, 0)))
        assertTrue(repo.tasks().first().isEmpty())
        repo.save(task("a", "Old", time = LocalTime.of(9, 0)))
        assertEquals("The end has to be after the start.", actions.update("a", "Old", "", TODAY, LocalTime.of(9, 0), LocalTime.of(8, 0)))
        assertNull(repo.tasks().first().single().endTime)
    }

    @Test
    fun aDeadlineNeedsNoStart() = runTest {
        assertNull(actions.create("Send the report", "", TODAY, null, LocalTime.of(18, 0)))
        val t = repo.tasks().first().single()
        assertNull(t.time)
        assertEquals(LocalTime.of(18, 0), t.endTime)
    }

    @Test
    fun deleting() = runTest {
        repo.save(task("a"))
        repo.save(task("b"))
        actions.delete("a")
        assertEquals(listOf("b"), repo.tasks().first().map { it.id })
    }
}
