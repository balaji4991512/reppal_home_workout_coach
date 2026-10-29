package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.LocalDateTime
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
    /**
     * When it should be over. With a [time] it makes a block ("Deep work, 3 to 5 pm"); on its own it is a deadline
     * ("finish by 6 pm"). Always after the start, on the same day.
     */
    val endTime: LocalTime? = null,
) {
    init {
        require(title.isNotBlank()) { "a task needs a title" }
        require(time == null || endTime == null || endTime > time) { "a task must end after it starts" }
    }

    val kind: TaskKind
        get() = when {
            time != null && endTime != null -> TaskKind.BLOCK
            endTime != null -> TaskKind.DEADLINE
            time != null -> TaskKind.POINT
            else -> TaskKind.ANYTIME
        }

    /** Where it sits on the day's timeline: its start, or its deadline when it has no start. Null for no time. */
    val timelineTime: LocalTime? get() = time ?: endTime
}

/** How a task relates to the clock. */
enum class TaskKind { ANYTIME, POINT, BLOCK, DEADLINE }

/** Where a block or deadline stands against the clock. */
enum class ClockState { UPCOMING, RUNNING, OVER }

/**
 * A block or deadline against the clock: [fraction] is how much of a block has passed (0 to 1; always 0 for a deadline),
 * [minutesLeft] is what remains while [ClockState.RUNNING], and [minutesUntil] is the wait while [ClockState.UPCOMING].
 */
data class TaskClock(val state: ClockState, val fraction: Double, val minutesLeft: Int?, val minutesUntil: Int?)

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
    val order: Comparator<Task> = compareBy<Task>({ it.timelineTime == null }, { it.timelineTime }, { it.createdAt }, { it.id })

    /**
     * When a task should ring: its start, or its deadline when it has no start. Null when it has no time, or is done.
     * Only moments after [after] count, so something that has passed never rings late.
     */
    fun reminderAt(task: Task, after: LocalDateTime): LocalDateTime? {
        if (task.done) return null
        val at = task.timelineTime?.let { task.date.atTime(it) } ?: return null
        return at.takeIf { it.isAfter(after) }
    }

    /** The next [limit] reminders after [after], soonest first (a phone only needs the near ones set). */
    fun upcomingReminders(all: List<Task>, after: LocalDateTime, limit: Int = 40): List<Pair<Task, LocalDateTime>> =
        all.mapNotNull { t -> reminderAt(t, after)?.let { t to it } }.sortedBy { it.second }.take(limit)

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

    /**
     * Where a block or deadline stands at [nowMinutes] (minutes since midnight) on [today]. A task on an earlier day is
     * over and one on a later day is still to come, whatever the time. Null for a task with no end.
     */
    fun clock(date: LocalDate, start: LocalTime?, end: LocalTime?, today: LocalDate, nowMinutes: Int): TaskClock? {
        if (end == null) return null
        val s = start?.let { it.hour * 60 + it.minute }
        val e = end.hour * 60 + end.minute
        val now = when {
            date < today -> Int.MAX_VALUE
            date > today -> Int.MIN_VALUE
            else -> nowMinutes
        }
        return when {
            now >= e -> TaskClock(ClockState.OVER, if (s == null) 0.0 else 1.0, null, null)
            s != null && now < s -> TaskClock(ClockState.UPCOMING, 0.0, null, if (now == Int.MIN_VALUE) null else s - now)
            s == null && now == Int.MIN_VALUE -> TaskClock(ClockState.UPCOMING, 0.0, null, null) // a deadline on a later day
            s == null -> TaskClock(ClockState.RUNNING, 0.0, e - now, null) // counting down to a deadline
            else -> TaskClock(ClockState.RUNNING, (now - s).toDouble() / (e - s), e - now, null)
        }
    }

    fun clock(task: Task, today: LocalDate, nowMinutes: Int): TaskClock? = clock(task.date, task.time, task.endTime, today, nowMinutes)

    /** The first thing to fix in what was typed, or null if it can be saved. */
    fun problem(title: String, note: String, time: LocalTime? = null, endTime: LocalTime? = null): String? = when {
        title.isBlank() -> "Write what you need to do."
        time != null && endTime != null && endTime <= time -> "The end has to be after the start."
        title.trim().length > MAX_TITLE -> "Keep the title to $MAX_TITLE characters."
        note.trim().length > MAX_NOTE -> "Keep the note to $MAX_NOTE characters."
        else -> null
    }

    /** A new task from what was typed (trimmed). Check [problem] first. */
    fun create(id: String, title: String, note: String, date: LocalDate, time: LocalTime?, now: Long, endTime: LocalTime? = null): Task =
        Task(id, title.trim(), date, time, note.trim(), done = false, createdAt = now, endTime = endTime)

    /** [task] with new text, day and time; whether it is done and when it was added stay as they were. */
    fun edit(task: Task, title: String, note: String, date: LocalDate, time: LocalTime?, endTime: LocalTime? = null): Task =
        task.copy(title = title.trim(), note = note.trim(), date = date, time = time, endTime = endTime)
}
