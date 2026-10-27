package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.LocalTime

/**
 * A task in the daily planner (specs/HABITS_PLANNER_SPEC.md, section 2). [date] is a local calendar day, never a
 * timestamp, so it does not move with the time zone.
 */
data class Task(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val note: String = "",
    val done: Boolean = false,
    /** When it was added (epoch millis): orders tasks that share a time, or have none. */
    val createdAt: Long = 0,
) {
    init {
        require(title.isNotBlank()) { "a task needs a title" }
    }
}

/**
 * One day of the planner: that day's [tasks] in order, and, on today only, what was left undone on earlier days
 * ([carriedOver], oldest first).
 */
data class DayPlan(val date: LocalDate, val tasks: List<Task>, val carriedOver: List<Task>) {
    val done: Int get() = tasks.count { it.done }
    val total: Int get() = tasks.size
}

object Tasks {
    const val MAX_TITLE = 120
    const val MAX_NOTE = 500

    /**
     * Timed tasks first, by time; then tasks with no time, in the order they were added. Ticking a task never moves
     * it, so the list stays still under the finger. Ties fall back to the id so the order is always the same.
     */
    val order: Comparator<Task> = compareBy<Task>({ it.time == null }, { it.time }, { it.createdAt }, { it.id })

    /** What the day view shows for [date] when today is [today]. Carried-over tasks appear on today only. */
    fun dayPlan(all: List<Task>, date: LocalDate, today: LocalDate): DayPlan = DayPlan(
        date = date,
        tasks = all.filter { it.date == date }.sortedWith(order),
        carriedOver = if (date == today) {
            all.filter { it.date < today && !it.done }.sortedWith(compareBy<Task> { it.date }.then(order))
        } else {
            emptyList()
        },
    )

    fun toggle(task: Task): Task = task.copy(done = !task.done)

    /**
     * "Move to tomorrow": the next day after today, or after the task's own day if that is still ahead. A task left
     * over from an earlier day therefore lands on tomorrow and stops being carried over. Null for a done task.
     */
    fun postpone(task: Task, today: LocalDate): Task? =
        if (task.done) null else task.copy(date = maxOf(task.date, today).plusDays(1))

    /** "Move to today" for a carried-over task. Its time and note stay. Null for a done task. */
    fun moveToToday(task: Task, today: LocalDate): Task? = if (task.done) null else task.copy(date = today)

    /** The first thing to fix in what was typed, or null if it can be saved. */
    fun problem(title: String, note: String): String? = when {
        title.isBlank() -> "Write what you need to do."
        title.trim().length > MAX_TITLE -> "Keep the title to $MAX_TITLE characters."
        note.trim().length > MAX_NOTE -> "Keep the note to $MAX_NOTE characters."
        else -> null
    }

    /** A new task from what was typed (trimmed). Check [problem] first. */
    fun create(id: String, title: String, note: String, date: LocalDate, time: LocalTime?, now: Long): Task =
        Task(id, title.trim(), date, time, note.trim(), done = false, createdAt = now)

    /** [task] with new text, day and time; whether it is done and when it was added stay as they were. */
    fun edit(task: Task, title: String, note: String, date: LocalDate, time: LocalTime?): Task =
        task.copy(title = title.trim(), note = note.trim(), date = date, time = time)
}
