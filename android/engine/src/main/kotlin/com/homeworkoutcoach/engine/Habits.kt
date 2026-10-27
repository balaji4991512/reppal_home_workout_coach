package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How a habit is tracked (specs/HABITS_PLANNER_SPEC.md, section 1). */
enum class HabitKind {
    /** Done or not done; target is 1. */
    DONE,

    /** A number (glasses of water); target is the count to reach. */
    COUNT,

    /** Minutes (reading); target is the minutes to reach. */
    TIME,
}

/** When a habit is due. */
sealed interface HabitSchedule {
    data object Daily : HabitSchedule

    /** Only on these weekdays. */
    data class Weekdays(val days: Set<DayOfWeek>) : HabitSchedule

    /** [times] days a week, on any days: due every day until the week's count is met. */
    data class TimesPerWeek(val times: Int) : HabitSchedule
}

/** What ticks a habit by itself. A tick made by hand always wins, including unticking. */
enum class AutoSource { NONE, WORKOUT, MORNING_ROUTINE, NIGHT_ROUTINE }

data class Habit(
    val id: String,
    val name: String,
    val kind: HabitKind,
    /** 1 for [HabitKind.DONE]; the count or minutes otherwise. */
    val target: Int,
    val schedule: HabitSchedule,
    val startDate: LocalDate,
    val auto: AutoSource = AutoSource.NONE,
    val archived: Boolean = false,
) {
    init {
        require(target >= 1) { "target must be at least 1" }
        require(kind != HabitKind.DONE || target == 1) { "a done/not done habit has target 1" }
        when (schedule) {
            is HabitSchedule.Weekdays -> require(schedule.days.isNotEmpty()) { "choose at least one weekday" }
            is HabitSchedule.TimesPerWeek -> require(schedule.times in 1..7) { "times a week must be 1..7" }
            HabitSchedule.Daily -> Unit
        }
    }
}

/**
 * What was recorded for one habit. [entries] holds the amounts typed or ticked by hand per calendar day (0 means
 * unticked by hand); [autoDays] are days the habit's [AutoSource] happened. Days are local calendar days, never
 * timestamps, so a day does not move with the time zone.
 */
data class HabitLog(
    val entries: Map<LocalDate, Int> = emptyMap(),
    val autoDays: Set<LocalDate> = emptySet(),
)

/** How a single day looks in the habit's calendar. No state means "missed": there are no red marks. */
enum class DayState { DONE, PARTIAL, OPEN, NOT_DUE }

/** [done] of [target] in a week, e.g. "4 of 5". */
data class WeekProgress(val done: Int, val target: Int) {
    val complete: Boolean get() = done >= target
}

/** [done] days of the [due] days that were due so far. A day or week still in progress is never counted as missed. */
data class HabitStats(val done: Int, val due: Int)

object Habits {
    // ---- Days and amounts ----

    /** The week starts on Monday. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Amount counted on [date]: what was entered by hand, otherwise the target if it ticked itself, otherwise 0. */
    fun amount(habit: Habit, log: HabitLog, date: LocalDate): Int =
        log.entries[date] ?: if (date in log.autoDays) habit.target else 0

    fun isDone(habit: Habit, log: HabitLog, date: LocalDate): Boolean = amount(habit, log, date) >= habit.target

    /** The new amount after adding [delta] (a tick is +1 or -1 on a done/not done habit). Never below 0. */
    fun adjusted(habit: Habit, current: Int, delta: Int): Int {
        val next = (current + delta).coerceAtLeast(0)
        return if (habit.kind == HabitKind.DONE) next.coerceAtMost(1) else next
    }

    /** 0.0 to 1.0: how far [date] got towards the target. */
    fun fraction(habit: Habit, log: HabitLog, date: LocalDate): Double =
        (amount(habit, log, date).toDouble() / habit.target).coerceIn(0.0, 1.0)

    // ---- Due ----

    /**
     * Whether the habit is due on [date]. Nothing is due before the start date or once archived. A times-a-week habit
     * stays due until the week has its count, and a day that was ticked anyway stays due so it keeps showing as done.
     */
    fun isDue(habit: Habit, log: HabitLog, date: LocalDate): Boolean {
        if (habit.archived || date < habit.startDate) return false
        return when (val s = habit.schedule) {
            HabitSchedule.Daily -> true
            is HabitSchedule.Weekdays -> date.dayOfWeek in s.days
            is HabitSchedule.TimesPerWeek ->
                isDone(habit, log, date) || doneDaysBefore(habit, log, date) < s.times
        }
    }

    /** Days done earlier in the same week as [date]. */
    private fun doneDaysBefore(habit: Habit, log: HabitLog, date: LocalDate): Int =
        generateSequence(weekStart(date)) { it.plusDays(1) }.takeWhile { it < date }.count { isDone(habit, log, it) }

    fun dayState(habit: Habit, log: HabitLog, date: LocalDate): DayState = when {
        isDone(habit, log, date) -> DayState.DONE
        amount(habit, log, date) > 0 -> DayState.PARTIAL
        isDue(habit, log, date) -> DayState.OPEN
        else -> DayState.NOT_DUE
    }

    // ---- Progress ----

    /**
     * The week containing [date]: days done against the target for the week. The target is the scheduled days from
     * the start date on, or the count for a times-a-week habit (no more than the days left from the start date).
     */
    fun weekProgress(habit: Habit, log: HabitLog, date: LocalDate): WeekProgress {
        val first = weekStart(date)
        val days = (0L..6L).map { first.plusDays(it) }
        val done = days.count { isDone(habit, log, it) }
        val target = when (val s = habit.schedule) {
            HabitSchedule.Daily -> days.count { it >= habit.startDate }
            is HabitSchedule.Weekdays -> days.count { it >= habit.startDate && it.dayOfWeek in s.days }
            is HabitSchedule.TimesPerWeek -> minOf(s.times, days.count { it >= habit.startDate })
        }
        return WeekProgress(done, target)
    }

    /** Days done in the inclusive range, however they were scheduled (for "this month" and "all time"). */
    fun doneDays(habit: Habit, log: HabitLog, from: LocalDate, to: LocalDate): Int =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.count { isDone(habit, log, it) }

    /**
     * Done against due from the start date (or [from]) to [today]. Fixed schedules count the days that were due and
     * over (today counts only if done). A times-a-week habit counts N for each finished week and, for the week in
     * progress, only what is already done, so it is never behind before the week ends.
     */
    fun stats(habit: Habit, log: HabitLog, from: LocalDate, today: LocalDate): HabitStats {
        val start = maxOf(from, habit.startDate)
        if (start > today) return HabitStats(0, 0)
        val schedule = habit.schedule
        if (schedule !is HabitSchedule.TimesPerWeek) {
            var done = 0
            var due = 0
            var day = start
            while (day <= today) {
                val doneToday = isDone(habit, log, day)
                if (doneToday) done++
                // A day done on a non-scheduled day still counts as due, so done never passes due.
                if ((isDueBySchedule(habit, day) && day < today) || doneToday) due++
                day = day.plusDays(1)
            }
            return HabitStats(done, due)
        }
        var done = 0
        var due = 0
        var week = weekStart(start)
        while (week <= today) {
            val days = (0L..6L).map { week.plusDays(it) }.filter { it >= start && it <= today }
            val inWeek = days.count { isDone(habit, log, it) }
            val counted = minOf(inWeek, schedule.times)
            val last = week.plusDays(6)
            val sinceStart = (0L..6L).map { week.plusDays(it) }.count { it >= start }
            done += counted
            due += if (last < today) minOf(schedule.times, sinceStart) else counted
            week = week.plusDays(7)
        }
        return HabitStats(done, due)
    }

    /** Fixed-schedule due check that ignores what was logged (used for counting past days). */
    private fun isDueBySchedule(habit: Habit, date: LocalDate): Boolean = when (val s = habit.schedule) {
        HabitSchedule.Daily -> true
        is HabitSchedule.Weekdays -> date.dayOfWeek in s.days
        is HabitSchedule.TimesPerWeek -> true
    }

    // ---- Starting suggestions ----

    data class Suggestion(val name: String, val kind: HabitKind, val target: Int, val auto: AutoSource = AutoSource.NONE)

    /** The starting list (specs/HABITS_PLANNER_SPEC.md); the user picks any, edits them or ignores them all. */
    val suggestions: List<Suggestion> = listOf(
        Suggestion("Workout", HabitKind.DONE, 1, AutoSource.WORKOUT),
        Suggestion("Morning routine", HabitKind.DONE, 1, AutoSource.MORNING_ROUTINE),
        Suggestion("Night routine", HabitKind.DONE, 1, AutoSource.NIGHT_ROUTINE),
        Suggestion("Journal", HabitKind.DONE, 1),
        Suggestion("Water", HabitKind.COUNT, 8),
        Suggestion("Sleep", HabitKind.TIME, 7 * 60),
        Suggestion("Reading", HabitKind.TIME, 20),
        Suggestion("Meditation", HabitKind.TIME, 10),
        Suggestion("Yoga", HabitKind.TIME, 20),
        Suggestion("Walking", HabitKind.TIME, 30),
    )
}
