package com.homeworkoutcoach.app.ui.focus

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.components.DayBadge
import com.homeworkoutcoach.engine.Habits
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
    /** The sessions of the day shown, newest first; each [SessionRowUi.whenText] is just the time. */
    val sessions: List<SessionRowUi>,
    /** The day shown, "Today", "Yesterday" or "Fri 23 Oct", and whether it is today. */
    val date: LocalDate,
    val title: String,
    val isToday: Boolean,
    /** The minutes focused under each day of the week shown that has happened ("25m", "1h5"). */
    val badges: Map<LocalDate, DayBadge>,
    /** Planner tasks to work on: not done, for today or left over. */
    val taskChoices: List<String>,
    val presets: List<Int>,
)

/** "25m", "1h", "1h5": short enough to sit under a day in the strip. */
internal fun compact(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h${minutes % 60}"
}

class FocusPresenter {
    fun present(
        active: ActiveFocus?,
        sessions: List<FocusSession>,
        tasks: List<Task>,
        today: LocalDate,
        zone: ZoneId,
        shown: LocalDate = today,
    ): FocusUi {
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
            sessions = sessions.filter { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() == shown }
                .sortedByDescending { it.startMillis }
                .map { s ->
                    val at = Instant.ofEpochMilli(s.startMillis).atZone(zone)
                    SessionRowUi(
                        whenText = PlannerFormat.time(at.toLocalTime()),
                        lengthText = if (s.completed) HabitFormat.minutes(s.actualMinutes) else "${s.actualMinutes} of ${s.plannedMinutes} min",
                        note = s.taskTitle,
                        completed = s.completed,
                    )
                },
            date = shown,
            title = PlannerFormat.dayTitle(shown, today),
            isToday = shown == today,
            badges = (0L..6L).map { Habits.weekStart(shown).plusDays(it) }.filter { !it.isAfter(today) }.mapNotNull { d ->
                Focus.minutesOn(sessions, d, zone).takeIf { it > 0 }?.let { d to DayBadge(compact(it)) }
            }.toMap(),
            taskChoices = tasks.filter { !it.done && !it.date.isAfter(today) }
                .sortedWith(compareBy<Task> { it.date }.then(Tasks.order)).map { it.title }.distinct().take(6),
            presets = Focus.presets,
        )
    }
}
