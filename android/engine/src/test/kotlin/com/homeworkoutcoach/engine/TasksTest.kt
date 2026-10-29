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

    // ---- Blocks and deadlines ----

    @Test
    fun aTaskIsAnytimeAPointABlockOrADeadline() {
        assertEquals(TaskKind.ANYTIME, Task("a", "A", today).kind)
        assertEquals(TaskKind.POINT, Task("a", "A", today, at(9)).kind)
        assertEquals(TaskKind.BLOCK, Task("a", "A", today, at(15), endTime = at(17)).kind)
        assertEquals(TaskKind.DEADLINE, Task("a", "A", today, null, endTime = at(18)).kind)
    }

    @Test
    fun aTaskMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException::class.java) { Task("a", "A", today, at(15), endTime = at(15)) }
        assertThrows(IllegalArgumentException::class.java) { Task("a", "A", today, at(15), endTime = at(14, 59)) }
        assertEquals("The end has to be after the start.", Tasks.problem("A", "", at(15), at(14)))
        assertNull(Tasks.problem("A", "", at(15), at(17)))
        assertNull(Tasks.problem("A", "", null, at(8))) // a deadline needs no start
    }

    @Test
    fun aDeadlineSitsOnTheTimelineAtItsEnd() {
        val all = listOf(
            Task("anytime", "x", today, createdAt = 1),
            Task("deadline", "x", today, endTime = at(10), createdAt = 2),
            Task("block", "x", today, at(9), endTime = at(11), createdAt = 3),
            Task("point", "x", today, at(12), createdAt = 4),
        )
        assertEquals(listOf("block", "deadline", "point", "anytime"), ids(all.sortedWith(Tasks.order)))
        assertEquals(at(10), all[1].timelineTime)
        assertEquals(at(9), all[2].timelineTime)
        assertNull(all[0].timelineTime)
    }

    @Test
    fun aBlockFillsAsTheTimePasses() {
        val d = at(15) to at(17) // 3 pm to 5 pm
        fun clock(now: Int, date: LocalDate = today) = Tasks.clock(date, d.first, d.second, today, now)
        assertEquals(TaskClock(ClockState.UPCOMING, 0.0, null, 60), clock(14 * 60))
        val start = clock(15 * 60)!!
        assertEquals(ClockState.RUNNING, start.state)
        assertEquals(0.0, start.fraction, 1e-9)
        assertEquals(120, start.minutesLeft)
        val half = clock(16 * 60)!!
        assertEquals(0.5, half.fraction, 1e-9)
        assertEquals(60, half.minutesLeft)
        assertEquals(TaskClock(ClockState.OVER, 1.0, null, null), clock(17 * 60)) // the end itself counts as over
        assertEquals(TaskClock(ClockState.OVER, 1.0, null, null), clock(20 * 60))
    }

    @Test
    fun aDeadlineCountsDownAndThenIsOver() {
        fun clock(now: Int) = Tasks.clock(today, null, at(18), today, now)
        assertEquals(TaskClock(ClockState.RUNNING, 0.0, 130, null), clock(15 * 60 + 50))
        assertEquals(TaskClock(ClockState.RUNNING, 0.0, 1, null), clock(17 * 60 + 59))
        assertEquals(TaskClock(ClockState.OVER, 0.0, null, null), clock(18 * 60))
    }

    @Test
    fun otherDaysAreWhollyPastOrToCome() {
        val past = Tasks.clock(yesterday, at(15), at(17), today, 8 * 60)!!
        assertEquals(ClockState.OVER, past.state)
        assertEquals(1.0, past.fraction, 1e-9)
        val future = Tasks.clock(tomorrow, at(15), at(17), today, 23 * 60)!!
        assertEquals(ClockState.UPCOMING, future.state)
        assertEquals(0.0, future.fraction, 1e-9)
        assertNull(future.minutesUntil) // no countdown to another day
    }

    @Test
    fun noEndMeansNoClock() {
        assertNull(Tasks.clock(today, at(9), null, today, 600))
        assertNull(Tasks.clock(Task("a", "A", today, at(9)), today, 600))
    }

    @Test
    fun createAndEditCarryTheEnd() {
        val made = Tasks.create("id", " Deep work ", "", today, at(15), 7, endTime = at(17))
        assertEquals(Task("id", "Deep work", today, at(15), "", false, 7, at(17)), made)
        val edited = Tasks.edit(made, "Deep work", "", today, at(16), at(18))
        assertEquals(at(18), edited.endTime)
        assertNull(Tasks.edit(made, "Deep work", "", today, at(16)).endTime) // clearing the end
    }

    // ---- Reminders ----

    private val nine = today.atTime(9, 0)

    @Test
    fun aTimedTaskRingsAtItsStart() {
        assertEquals(today.atTime(10, 0), Tasks.reminderAt(task("a", time = at(10)), nine))
        assertEquals(today.atTime(15, 0), Tasks.reminderAt(task("b", time = at(15)).copy(endTime = at(17)), nine))
    }

    @Test
    fun aDeadlineOnlyTaskRingsAtTheDeadline() {
        assertEquals(today.atTime(18, 0), Tasks.reminderAt(task("a").copy(endTime = at(18)), nine))
    }

    @Test
    fun noTimeDoneOrPastMeansNoReminder() {
        assertNull(Tasks.reminderAt(task("a"), nine))
        assertNull(Tasks.reminderAt(task("b", time = at(10), done = true), nine))
        assertNull(Tasks.reminderAt(task("c", time = at(8)), nine))
        assertNull(Tasks.reminderAt(task("d", time = at(9)), nine)) // right now has already come
        assertNull(Tasks.reminderAt(task("e", date = yesterday, time = at(10)), nine))
    }

    @Test
    fun aFutureDayRingsOnThatDay() {
        assertEquals(tomorrow.atTime(7, 30), Tasks.reminderAt(task("a", date = tomorrow, time = at(7, 30)), nine))
    }

    @Test
    fun upcomingRemindersAreSoonestFirstAndLimited() {
        val all = listOf(task("late", time = at(20)), task("none"), task("soon", time = at(10)), task("done", time = at(11), done = true), task("mid", time = at(12)))
        assertEquals(listOf("soon", "mid", "late"), Tasks.upcomingReminders(all, nine).map { it.first.id })
        assertEquals(listOf("soon", "mid"), Tasks.upcomingReminders(all, nine, limit = 2).map { it.first.id })
    }
}
