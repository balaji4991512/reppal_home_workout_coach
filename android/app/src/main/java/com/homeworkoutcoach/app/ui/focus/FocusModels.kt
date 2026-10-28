package com.homeworkoutcoach.app.ui.focus

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.engine.Focus
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.Tasks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The session that is running. The screen counts down from [endMillis] itself. */
data class RunningUi(val title: String?, val plannedText: String, val startMillis: Long, val endMillis: Long)

data class SessionRowUi(val whenText: String, val lengthText: String, val note: String?, val completed: Boolean)

data class FocusUi(
    val running: RunningUi?,
    /** "1 h 10 min today, 2 sessions", or "No sessions yet today". */
    val today: String,
    /** "2 h 5 min this week". */
    val week: String,
    /** For the stat cards: "45 min" and "2 sessions" (or "No sessions yet"), and "1 h 45 min". */
    val todayValue: String,
    val todayNote: String,
    val weekValue: String,
    val recent: List<SessionRowUi>,
    /** Planner tasks to work on: not done, for today or left over. */
    val taskChoices: List<String>,
    val presets: List<Int>,
)

class FocusPresenter {
    fun present(active: ActiveFocus?, sessions: List<FocusSession>, tasks: List<Task>, today: LocalDate, zone: ZoneId): FocusUi {
        val minutesToday = Focus.minutesOn(sessions, today, zone)
        val countToday = Focus.sessionsOn(sessions, today, zone)
        return FocusUi(
            running = active?.let {
                RunningUi(it.taskTitle, HabitFormat.minutes(it.plannedMinutes), it.startMillis, it.toRun().endMillis)
            },
            today = if (countToday == 0) "No sessions yet today" else "${HabitFormat.minutes(minutesToday)} today, ${if (countToday == 1) "1 session" else "$countToday sessions"}",
            week = "${HabitFormat.minutes(Focus.minutesInWeek(sessions, today, zone))} this week",
            todayValue = HabitFormat.minutes(minutesToday),
            todayNote = when (countToday) {
                0 -> "No sessions yet"
                1 -> "1 session"
                else -> "$countToday sessions"
            },
            weekValue = HabitFormat.minutes(Focus.minutesInWeek(sessions, today, zone)),
            recent = Focus.recent(sessions, 8).map { s ->
                val at = Instant.ofEpochMilli(s.startMillis).atZone(zone)
                SessionRowUi(
                    whenText = "${PlannerFormat.dayTitle(at.toLocalDate(), today)} ${PlannerFormat.time(at.toLocalTime())}",
                    lengthText = if (s.completed) HabitFormat.minutes(s.actualMinutes) else "${s.actualMinutes} of ${s.plannedMinutes} min",
                    note = s.taskTitle,
                    completed = s.completed,
                )
            },
            taskChoices = tasks.filter { !it.done && !it.date.isAfter(today) }
                .sortedWith(compareBy<Task> { it.date }.then(Tasks.order)).map { it.title }.distinct().take(6),
            presets = Focus.presets,
        )
    }
}
