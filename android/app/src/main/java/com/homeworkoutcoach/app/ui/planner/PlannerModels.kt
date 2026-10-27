package com.homeworkoutcoach.app.ui.planner

import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.Tasks
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** One task as a row. [dayLabel] is set on carried-over rows only ("Yesterday", "Mon 26 Oct"). */
data class TaskRowUi(
    val id: String,
    val title: String,
    /** "5:30 pm", or null. */
    val time: String?,
    val note: String,
    val done: Boolean,
    val dayLabel: String?,
)

data class PlannerUi(
    val date: LocalDate,
    /** "Today", "Tomorrow", "Yesterday" or "Fri 30 Oct". */
    val title: String,
    /** "Wednesday 28 October". */
    val subtitle: String,
    val isToday: Boolean,
    /** "2 of 5 done", "Nothing planned." */
    val summary: String,
    val tasks: List<TaskRowUi>,
    val carriedOver: List<TaskRowUi>,
)

object PlannerFormat {
    /** 12-hour with am/pm, as people say it in India: "5:30 pm". */
    fun time(t: LocalTime): String {
        val h = t.hour % 12
        return "${if (h == 0) 12 else h}:${"%02d".format(t.minute)} ${if (t.hour < 12) "am" else "pm"}"
    }

    /** "Mon 26 Oct", with the year when it is not this year's. */
    fun dayShort(d: LocalDate, today: LocalDate): String {
        val base = "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
        return if (d.year == today.year) base else "$base ${d.year}"
    }

    fun dayLong(d: LocalDate): String =
        "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"

    /** A day as a short name relative to today, for a title. */
    fun dayTitle(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> dayShort(d, today)
    }
}

/** The Planner's day view. */
class PlannerPresenter {
    fun present(all: List<Task>, date: LocalDate, today: LocalDate): PlannerUi {
        val plan = Tasks.dayPlan(all, date, today)
        fun row(t: Task, carried: Boolean) = TaskRowUi(
            id = t.id,
            title = t.title,
            time = t.time?.let(PlannerFormat::time),
            note = t.note,
            done = t.done,
            dayLabel = if (carried) PlannerFormat.dayTitle(t.date, today) else null,
        )
        return PlannerUi(
            date = date,
            title = PlannerFormat.dayTitle(date, today),
            subtitle = PlannerFormat.dayLong(date),
            isToday = date == today,
            summary = when {
                plan.total == 0 -> "Nothing planned."
                plan.done == plan.total -> if (date == today) "All done for today." else "All done."
                else -> "${plan.done} of ${plan.total} done"
            },
            tasks = plan.tasks.map { row(it, false) },
            carriedOver = plan.carriedOver.map { row(it, true) },
        )
    }
}

/** What the planner screens do to tasks. Each one looks the task up by id, so it works on the latest data. */
class PlannerActions(
    private val repo: TaskRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private suspend fun find(id: String): Task? = repo.tasks().first().firstOrNull { it.id == id }

    /** Adds a task on [date] from one line of text. Returns what to fix, or null once it is added. */
    suspend fun add(title: String, date: LocalDate): String? {
        Tasks.problem(title, "")?.let { return it }
        repo.save(Tasks.create(newId(), title, "", date, null, now()))
        return null
    }

    suspend fun toggle(id: String) {
        find(id)?.let { repo.save(Tasks.toggle(it)) }
    }

    /** "Move to tomorrow". */
    suspend fun postpone(id: String, today: LocalDate) {
        find(id)?.let { t -> Tasks.postpone(t, today)?.let { repo.save(it) } }
    }

    /** "Move to today". */
    suspend fun moveToToday(id: String, today: LocalDate) {
        find(id)?.let { t -> Tasks.moveToToday(t, today)?.let { repo.save(it) } }
    }

    suspend fun delete(id: String) = repo.delete(id)

    /** Saves the edit form. Returns what to fix, or null once it is saved. */
    suspend fun update(id: String, title: String, note: String, date: LocalDate, time: LocalTime?): String? {
        Tasks.problem(title, note)?.let { return it }
        find(id)?.let { repo.save(Tasks.edit(it, title, note, date, time)) }
        return null
    }
}
