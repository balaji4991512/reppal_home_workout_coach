package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Planner rules from specs/HABITS_PLANNER_SPEC.md, section 2. */
class TasksTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun task(
        id: String,
        date: LocalDate = today,
        time: LocalTime? = null,
        done: Boolean = false,
        at: Long = 0,
    ) = Task(id, "Task $id", date, time, done = done, createdAt = at)

    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private fun ids(list: List<Task>) = list.map { it.id }

    // ---- Order ----

    @Test
    fun timedTasksComeFirstByTimeThenUntimedInTheOrderAdded() {
        val all = listOf(
            task("walk", at = 5),
            task("call", time = at(10), at = 2),
            task("milk", at = 1),
            task("gym", time = at(7, 30), at = 9),
        )
        assertEquals(listOf("gym", "call", "milk", "walk"), ids(Tasks.dayPlan(all, today, today).tasks))
    }

    @Test
    fun sameTimeFallsBackToTheOrderAddedAndThenTheId() {
        val all = listOf(
            task("b", time = at(9), at = 5),
            task("a", time = at(9), at = 5),
            task("early", time = at(9), at = 1),
        )
        assertEquals(listOf("early", "a", "b"), ids(Tasks.dayPlan(all, today, today).tasks))
    }

    @Test
    fun tickingATaskDoesNotMoveIt() {
        val all = listOf(task("a", at = 1), task("b", at = 2), task("c", at = 3))
        val after = all.map { if (it.id == "a") Tasks.toggle(it) else it }
        assertEquals(listOf("a", "b", "c"), ids(Tasks.dayPlan(after, today, today).tasks))
        assertTrue(Tasks.dayPlan(after, today, today).tasks.first().done)
    }

    // ---- The day view ----

    @Test
    fun aDayShowsOnlyItsOwnTasksAndCountsThemDone() {
        val all = listOf(task("a"), task("b", done = true), task("c", done = true), task("y", date = yesterday), task("t", date = tomorrow))
        val plan = Tasks.dayPlan(all, today, today)
        assertEquals(setOf("a", "b", "c"), ids(plan.tasks).toSet())
        assertEquals(2, plan.done)
        assertEquals(3, plan.total)
        assertEquals(listOf("t"), ids(Tasks.dayPlan(all, tomorrow, today).tasks))
    }

    @Test
    fun anEmptyDayIsEmpty() {
        val plan = Tasks.dayPlan(emptyList(), today, today)
        assertTrue(plan.tasks.isEmpty() && plan.carriedOver.isEmpty())
        assertEquals(0, plan.total)
    }

    // ---- Carried over ----

    @Test
    fun aTaskLeftUndoneYesterdayShowsOnceUnderCarriedOverToday() {
        val left = task("left", date = yesterday)
        val plan = Tasks.dayPlan(listOf(left), today, today)
        assertEquals(listOf("left"), ids(plan.carriedOver))
        assertTrue(plan.tasks.isEmpty()) // not also listed among today's own tasks
        assertEquals(0, plan.total) // and it does not count against today
    }

    @Test
    fun carriedOverOnlyShowsOnToday() {
        val left = task("left", date = yesterday)
        assertTrue(Tasks.dayPlan(listOf(left), tomorrow, today).carriedOver.isEmpty())
        assertTrue(Tasks.dayPlan(listOf(left), yesterday, today).carriedOver.isEmpty())
        // the task stays on its own day
        assertEquals(listOf("left"), ids(Tasks.dayPlan(listOf(left), yesterday, today).tasks))
    }

    @Test
    fun doneAndFutureTasksAreNotCarriedOver() {
        val all = listOf(task("done", date = yesterday, done = true), task("future", date = tomorrow), task("now"))
        assertTrue(Tasks.dayPlan(all, today, today).carriedOver.isEmpty())
    }

    @Test
    fun carriedOverTasksAreOldestFirstThenInTheUsualOrder() {
        val all = listOf(
            task("recent-late", date = yesterday, time = at(15)),
            task("old", date = today.minusDays(5), at = 9),
            task("recent-early", date = yesterday, time = at(8)),
            task("recent-untimed", date = yesterday, at = 1),
        )
        assertEquals(listOf("old", "recent-early", "recent-late", "recent-untimed"), ids(Tasks.dayPlan(all, today, today).carriedOver))
    }

    // ---- Moving ----

    @Test
    fun moveToTomorrowFromTodayLandsOnTomorrow() {
        val moved = Tasks.postpone(task("a"), today)!!
        assertEquals(tomorrow, moved.date)
        assertEquals(listOf("a"), ids(Tasks.dayPlan(listOf(moved), tomorrow, today).tasks))
        assertTrue(Tasks.dayPlan(listOf(moved), today, today).tasks.isEmpty())
    }

    @Test
    fun aCarriedOverTaskMovedToTomorrowDoesNotReturnToday() {
        val left = task("left", date = yesterday)
        val moved = Tasks.postpone(left, today)!!
        assertEquals(tomorrow, moved.date) // tomorrow, not today
        val plan = Tasks.dayPlan(listOf(moved), today, today)
        assertTrue(plan.carriedOver.isEmpty() && plan.tasks.isEmpty())
        assertEquals(listOf("left"), ids(Tasks.dayPlan(listOf(moved), tomorrow, today).tasks))
        // and it is not carried over again on the day after, as it was done or moved on by then
        assertTrue(Tasks.dayPlan(listOf(moved), tomorrow, tomorrow).carriedOver.isEmpty())
    }

    @Test
    fun aFutureTaskMovesToTheDayAfterItsOwn() {
        val inThree = task("later", date = today.plusDays(3))
        assertEquals(today.plusDays(4), Tasks.postpone(inThree, today)!!.date)
    }

    @Test
    fun aMovedTaskKeepsItsTextTimeAndPlaceInTheOrder() {
        val t = Task("a", "Call mum", yesterday, LocalTime.of(18, 0), "about Sunday", createdAt = 7)
        assertEquals(t.copy(date = tomorrow), Tasks.postpone(t, today))
        assertEquals(t.copy(date = today), Tasks.moveToToday(t, today))
    }

    @Test
    fun moveToTodayTakesACarriedOverTaskOutOfCarriedOver() {
        val left = task("left", date = yesterday)
        val moved = Tasks.moveToToday(left, today)!!
        val plan = Tasks.dayPlan(listOf(moved), today, today)
        assertTrue(plan.carriedOver.isEmpty())
        assertEquals(listOf("left"), ids(plan.tasks))
    }

    @Test
    fun aDoneTaskCannotBeMoved() {
        val done = task("a", done = true)
        assertNull(Tasks.postpone(done, today))
        assertNull(Tasks.moveToToday(done.copy(date = yesterday), today))
    }

    @Test
    fun tickAndUntick() {
        val t = task("a")
        assertTrue(Tasks.toggle(t).done)
        assertEquals(t, Tasks.toggle(Tasks.toggle(t)))
    }

    // ---- Typing a task ----

    @Test
    fun aTaskNeedsATitleOfReasonableLength() {
        assertEquals("Write what you need to do.", Tasks.problem("", ""))
        assertEquals("Write what you need to do.", Tasks.problem("   ", "note"))
        assertNull(Tasks.problem("Buy milk", ""))
        assertNull(Tasks.problem("x".repeat(120), ""))
        assertEquals("Keep the title to 120 characters.", Tasks.problem("x".repeat(121), ""))
        assertNull(Tasks.problem("ok", "n".repeat(500)))
        assertEquals("Keep the note to 500 characters.", Tasks.problem("ok", "n".repeat(501)))
        // spaces around do not count against the limit
        assertNull(Tasks.problem("  " + "x".repeat(120) + "  ", ""))
    }

    @Test
    fun createTrimsAndStartsNotDone() {
        val t = Tasks.create("id", "  Buy milk ", " from the shop\n", today, at(17, 30), now = 1234)
        assertEquals(Task("id", "Buy milk", today, at(17, 30), "from the shop", false, 1234), t)
    }

    @Test
    fun editChangesTheTextDayAndTimeButNotDoneOrWhenItWasAdded() {
        val t = Task("id", "Old", today, null, "", done = true, createdAt = 42)
        val e = Tasks.edit(t, " New ", " why ", tomorrow, at(9))
        assertEquals(Task("id", "New", tomorrow, at(9), "why", true, 42), e)
        assertFalse(Tasks.edit(t.copy(done = false), "x", "", today, null).done)
    }

    @Test
    fun aTaskWithoutATitleCannotExist() {
        assertThrows(IllegalArgumentException::class.java) { Task("a", " ", today) }
    }
}
