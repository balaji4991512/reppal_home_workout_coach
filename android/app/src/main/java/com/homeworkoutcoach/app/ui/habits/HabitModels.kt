package com.homeworkoutcoach.app.ui.habits

import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.DayState
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.WeekProgress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One habit as a row: how today stands and how the week is going. */
data class HabitRowUi(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val kind: HabitKind,
    val target: Int,
    /** What counts today (by hand, or the full target if the habit ticked itself). */
    val amount: Int,
    val done: Boolean,
    /** 0 to 1: how far today got towards the target. */
    val fraction: Float,
    /** "Done", "5 of 8", "20 of 30 min". */
    val progress: String,
    /** "3 of 5 this week", or null when nothing is scheduled this week. */
    val week: String?,
    val due: Boolean,
    val archived: Boolean,
    /** True if the habit can tick itself (Workout and the routines), so a hand-made 0 must be kept. */
    val auto: Boolean,
)

data class HabitsUi(
    val today: List<HabitRowUi>,
    val other: List<HabitRowUi>,
    val archived: List<HabitRowUi>,
    /** "3 of 6 done today". */
    val summary: String,
)

object HabitFormat {
    fun minutes(n: Int): String = when {
        n < 60 -> "$n min"
        n % 60 == 0 -> "${n / 60} h"
        else -> "${n / 60} h ${n % 60} min"
    }

    fun progress(kind: HabitKind, amount: Int, target: Int): String = when (kind) {
        HabitKind.DONE -> if (amount >= 1) "Done" else "Not done"
        HabitKind.COUNT -> "$amount of $target"
        HabitKind.TIME ->
            if (target < 60 && amount < 60) "$amount of $target min" else "${minutes(amount)} of ${minutes(target)}"
    }

    fun week(p: WeekProgress): String? = if (p.target <= 0) null else "${p.done} of ${p.target} this week"

    fun schedule(s: HabitSchedule): String = when (s) {
        HabitSchedule.Daily -> "Every day"
        is HabitSchedule.Weekdays ->
            if (s.days.size == 7) "Every day" else s.days.sorted().joinToString(", ") { dayShort(it) }
        is HabitSchedule.TimesPerWeek -> if (s.times == 1) "Once a week" else "${s.times} times a week"
    }

    fun dayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** For an amount box: what one "target" means ("glasses" for numbers, minutes for time). */
    fun targetLabel(kind: HabitKind): String = when (kind) {
        HabitKind.DONE -> ""
        HabitKind.COUNT -> "Target a day (a number)"
        HabitKind.TIME -> "Target a day (minutes)"
    }
}

object HabitRows {
    fun row(
        s: StoredHabit,
        entries: Map<String, Map<LocalDate, Int>>,
        workoutDays: Set<LocalDate>,
        today: LocalDate,
        routineDays: Map<AutoSource, Set<LocalDate>> = emptyMap(),
    ): HabitRowUi {
        val h = s.habit
        val log = HabitLogs.log(h, entries[h.id].orEmpty(), workoutDays, routineDays)
        val amount = Habits.amount(h, log, today)
        return HabitRowUi(
            id = h.id,
            name = h.name,
            icon = s.icon,
            color = s.color,
            kind = h.kind,
            target = h.target,
            amount = amount,
            done = Habits.isDone(h, log, today),
            fraction = Habits.fraction(h, log, today).toFloat(),
            progress = HabitFormat.progress(h.kind, amount, h.target),
            week = HabitFormat.week(Habits.weekProgress(h, log, today)),
            due = Habits.isDue(h, log, today),
            archived = h.archived,
            auto = h.auto != AutoSource.NONE,
        )
    }
}

/** The Habits list: what is due today first, then the rest, then archived habits. */
class HabitsPresenter {
    fun present(
        habits: List<StoredHabit>,
        entries: Map<String, Map<LocalDate, Int>>,
        workoutDays: Set<LocalDate>,
        today: LocalDate,
        routineDays: Map<AutoSource, Set<LocalDate>> = emptyMap(),
    ): HabitsUi {
        val rows = habits.map { HabitRows.row(it, entries, workoutDays, today, routineDays) }
        val live = rows.filterNot { it.archived }
        val due = live.filter { it.due }
        val done = due.count { it.done }
        return HabitsUi(
            today = due,
            other = live.filterNot { it.due },
            archived = rows.filter { it.archived },
            summary = when {
                live.isEmpty() -> "Track anything you want to do every day or every week."
                due.isEmpty() -> "Nothing is due today."
                done == due.size -> "All done for today."
                else -> "$done of ${due.size} done today"
            },
        )
    }
}

/** One day in the habit's month calendar. */
data class DayCellUi(
    val date: LocalDate,
    val state: DayState,
    /** 0 to 1, for a partly done number or time habit. */
    val fraction: Float,
    val isToday: Boolean,
    val future: Boolean,
    /** Days from the start date up to today can be changed. */
    val editable: Boolean,
    /** What was recorded, for the editor. */
    val amount: Int,
)

data class HabitDetailUi(
    val row: HabitRowUi,
    val schedule: String,
    val monthTitle: String,
    /** Monday first; null cells pad the first and last week. */
    val weeks: List<List<DayCellUi?>>,
    val canPrev: Boolean,
    val canNext: Boolean,
    val weekDone: Int,
    val weekTarget: Int,
    val weekComplete: Boolean,
    val monthDays: Int,
    val allTimeDays: Int,
    val since: String,
)

class HabitDetailPresenter {
    fun present(
        s: StoredHabit,
        entries: Map<String, Map<LocalDate, Int>>,
        workoutDays: Set<LocalDate>,
        today: LocalDate,
        month: YearMonth,
        routineDays: Map<AutoSource, Set<LocalDate>> = emptyMap(),
    ): HabitDetailUi {
        val h = s.habit
        val log = HabitLogs.log(h, entries[h.id].orEmpty(), workoutDays, routineDays)
        val first = month.atDay(1)
        val days = (0 until month.lengthOfMonth()).map { i ->
            val date = first.plusDays(i.toLong())
            DayCellUi(
                date = date,
                state = Habits.dayState(h, log, date),
                fraction = Habits.fraction(h, log, date).toFloat(),
                isToday = date == today,
                future = date > today,
                editable = date >= h.startDate && date <= today,
                amount = Habits.amount(h, log, date),
            )
        }
        val cells: List<DayCellUi?> = List(first.dayOfWeek.value - 1) { null } + days
        val weeks = (cells + List((7 - cells.size % 7) % 7) { null }).chunked(7)

        val monthEnd = minOf(month.atEndOfMonth(), today)
        val monthStart = maxOf(first, h.startDate)
        val week = Habits.weekProgress(h, log, today)
        return HabitDetailUi(
            row = HabitRows.row(s, entries, workoutDays, today, routineDays),
            schedule = HabitFormat.schedule(h.schedule),
            monthTitle = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
            weeks = weeks,
            canPrev = month > YearMonth.from(h.startDate),
            canNext = month < YearMonth.from(today),
            weekDone = week.done,
            weekTarget = week.target,
            weekComplete = week.complete && week.target > 0,
            monthDays = if (monthStart <= monthEnd) Habits.doneDays(h, log, monthStart, monthEnd) else 0,
            allTimeDays = if (h.startDate <= today) Habits.doneDays(h, log, h.startDate, today) else 0,
            since = "${h.startDate.dayOfMonth} ${h.startDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${h.startDate.year}",
        )
    }
}

/** Writes for habit amounts. */
object HabitWrites {
    /**
     * Records [amount] for a day. Zero removes the entry, except on a habit that can tick itself ([auto]): there a
     * 0 is kept, because it is what keeps a hand-unticked day unticked.
     */
    suspend fun record(repo: HabitRepository, habitId: String, date: LocalDate, amount: Int, auto: Boolean) {
        if (amount <= 0 && !auto) repo.clearAmount(habitId, date) else repo.setAmount(habitId, date, amount.coerceAtLeast(0))
    }
}

enum class ScheduleType { DAILY, WEEKDAYS, TIMES }

/** What the Add / Edit habit form holds until it is saved. */
data class HabitDraft(
    /** Null for a new habit. */
    val id: String? = null,
    val name: String = "",
    val icon: String = HabitStyles.DEFAULT_ICON,
    val color: String = HabitStyles.DEFAULT_COLOR,
    val kind: HabitKind = HabitKind.DONE,
    val targetText: String = "1",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val weekdays: Set<DayOfWeek> = WORKWEEK,
    val times: Int = 3,
    val auto: AutoSource = AutoSource.NONE,
    val archived: Boolean = false,
    /** Kept when editing; a new habit starts today. */
    val startDate: LocalDate? = null,
    val position: Int? = null,
) {
    /** The target as a number, or null if what was typed is not valid for [kind]. */
    val target: Int?
        get() = when (kind) {
            HabitKind.DONE -> 1
            HabitKind.COUNT -> targetText.trim().toIntOrNull()?.takeIf { it in 1..MAX_COUNT }
            HabitKind.TIME -> targetText.trim().toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }
        }

    /** Switching how it is tracked also resets the target to something sensible. */
    fun withKind(k: HabitKind): HabitDraft = copy(
        kind = k,
        targetText = when (k) {
            HabitKind.DONE -> "1"
            HabitKind.COUNT -> if (kind == HabitKind.COUNT) targetText else "8"
            HabitKind.TIME -> if (kind == HabitKind.TIME) targetText else "20"
        },
    )

    /** The first thing to fix, or null if the habit can be saved. [others] are the names of the other habits. */
    fun problem(others: Collection<String>): String? = when {
        name.isBlank() -> "Give the habit a name."
        name.trim().length > MAX_NAME -> "Keep the name to $MAX_NAME characters."
        others.any { it.trim().equals(name.trim(), ignoreCase = true) } -> "You already have a habit with this name."
        target == null -> if (kind == HabitKind.COUNT) "Enter a target from 1 to $MAX_COUNT." else "Enter minutes from 1 to $MAX_MINUTES."
        scheduleType == ScheduleType.WEEKDAYS && weekdays.isEmpty() -> "Choose at least one day."
        else -> null
    }

    /** Only call when [problem] is null. A new habit gets [newId], starts [today] and goes to the end of the list. */
    fun toStored(newId: String, today: LocalDate, nextPosition: Int): StoredHabit {
        val schedule = when (scheduleType) {
            ScheduleType.DAILY -> HabitSchedule.Daily
            ScheduleType.WEEKDAYS -> HabitSchedule.Weekdays(weekdays)
            ScheduleType.TIMES -> HabitSchedule.TimesPerWeek(times)
        }
        return StoredHabit(
            Habit(id ?: newId, name.trim(), kind, checkNotNull(target), schedule, startDate ?: today, auto, archived),
            icon = icon,
            color = color,
            position = position ?: nextPosition,
        )
    }

    companion object {
        val WORKWEEK: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        const val MAX_NAME = 40
        const val MAX_COUNT = 999
        const val MAX_MINUTES = 1440

        fun from(s: StoredHabit): HabitDraft {
            val h = s.habit
            return HabitDraft(
                id = h.id,
                name = h.name,
                icon = s.icon,
                color = s.color,
                kind = h.kind,
                targetText = h.target.toString(),
                scheduleType = when (h.schedule) {
                    HabitSchedule.Daily -> ScheduleType.DAILY
                    is HabitSchedule.Weekdays -> ScheduleType.WEEKDAYS
                    is HabitSchedule.TimesPerWeek -> ScheduleType.TIMES
                },
                weekdays = (h.schedule as? HabitSchedule.Weekdays)?.days ?: WORKWEEK,
                times = (h.schedule as? HabitSchedule.TimesPerWeek)?.times ?: 3,
                auto = h.auto,
                archived = h.archived,
                startDate = h.startDate,
                position = s.position,
            )
        }

        fun from(s: Habits.Suggestion): HabitDraft {
            val (icon, color) = HabitStyles.forName(s.name)
            return HabitDraft(name = s.name, icon = icon, color = color, kind = s.kind, targetText = s.target.toString(), auto = s.auto)
        }
    }
}

/** Icon and colour keys, and the look each starting suggestion gets. */
object HabitStyles {
    const val DEFAULT_ICON = "check"
    const val DEFAULT_COLOR = "blue"

    val icons = listOf("check", "drop", "moon", "sun", "book", "pen", "dumbbell", "walk", "lotus", "heart", "star", "list", "bolt")
    val colors = listOf("blue", "green", "orange", "red", "purple", "teal", "amber", "pink")

    private val named = mapOf(
        "Workout" to ("dumbbell" to "orange"),
        "Morning routine" to ("sun" to "amber"),
        "Night routine" to ("moon" to "purple"),
        "Journal" to ("pen" to "teal"),
        "Water" to ("drop" to "blue"),
        "Sleep" to ("moon" to "blue"),
        "Reading" to ("book" to "green"),
        "Meditation" to ("lotus" to "teal"),
        "Yoga" to ("lotus" to "pink"),
        "Walking" to ("walk" to "green"),
    )

    fun forName(name: String): Pair<String, String> = named[name] ?: (DEFAULT_ICON to DEFAULT_COLOR)
}
