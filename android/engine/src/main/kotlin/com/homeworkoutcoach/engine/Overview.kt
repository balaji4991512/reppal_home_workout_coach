package com.homeworkoutcoach.engine

import java.time.LocalDate

/** The span the Progress overview covers, always ending today. Weeks are Monday to Sunday. */
enum class OverviewRange(val weeks: Int?, val buckets: Int) {
    /** This week so far, one bar a day. */
    WEEK(1, 7),

    /** The last 4 weeks including this one, one bar a week. */
    MONTH(4, 4),

    /** The last 12 weeks including this one, one bar a week. */
    TWELVE_WEEKS(12, 12),

    /** The last 12 calendar months including this one, one bar a month; [weeks] is null because months differ. */
    YEAR(null, 12),
}

/** One bar: the habit days [done] of [due] from [from] to [to]. No due days means no bar height, never zero. */
data class OverviewBar(val from: LocalDate, val to: LocalDate, val done: Int, val due: Int) {
    /** 0 to 100, or null when nothing was due. */
    val percent: Int? get() = if (due == 0) null else done * 100 / due
}

data class Overview(
    val range: OverviewRange,
    val from: LocalDate,
    val bars: List<OverviewBar>,
    /** Habit days done of due over the whole range. */
    val habitsDone: Int,
    val habitsDue: Int,
    /** How the habit completion compares with the range just before, or null if either has nothing due. */
    val habitTrend: Trend?,
    val workouts: Int,
    val workoutsBefore: Int,
    val focusMinutes: Int,
    val focusSessions: Int,
    val focusMinutesBefore: Int,
) {
    val percent: Int? get() = if (habitsDue == 0) null else habitsDone * 100 / habitsDue
}

/**
 * What the Progress overview shows, counted from what was recorded. Today and any day still in progress are never
 * counted as missed (see [Habits.stats]), and nothing here is a score.
 */
object Overviews {
    /** A change of at least this many percentage points counts as up or quieter. */
    private const val TREND_POINTS = 5

    fun compute(inputs: CharacterInputs, range: OverviewRange, today: LocalDate): Overview {
        val from = startOf(range, today)
        val beforeTo = from.minusDays(1)
        val beforeFrom = range.weeks?.let { from.minusWeeks(it.toLong()) } ?: from.minusYears(1)

        // Habits that run a set number of times a week only make sense over whole weeks, so a bar for a single day
        // counts the habits with fixed days only ([fixedOnly]).
        fun habits(a: LocalDate, b: LocalDate, fixedOnly: Boolean = false): HabitStats {
            var done = 0
            var due = 0
            inputs.habits.forEach { f ->
                if (fixedOnly && f.habit.schedule is HabitSchedule.TimesPerWeek) return@forEach
                val s = Habits.stats(f.habit, f.log, a, today, until = b)
                done += s.done
                due += s.due
            }
            return HabitStats(done, due)
        }

        val bars = buckets(range, from, today).map { (a, b) ->
            habits(a, b, fixedOnly = range == OverviewRange.WEEK).let { OverviewBar(a, minOf(b, today), it.done, it.due) }
        }
        val now = habits(from, today)
        val before = habits(beforeFrom, beforeTo)
        val trend = if (now.due == 0 || before.due == 0) null else {
            val d = now.done * 100 / now.due - before.done * 100 / before.due
            when {
                d >= TREND_POINTS -> Trend.UP
                d <= -TREND_POINTS -> Trend.QUIETER
                else -> Trend.SAME
            }
        }

        fun inRange(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && !d.isAfter(b)
        val focusNow = inputs.focus.filter { inRange(it.date, from, today) }
        return Overview(
            range = range,
            from = from,
            bars = bars,
            habitsDone = now.done,
            habitsDue = now.due,
            habitTrend = trend,
            workouts = inputs.workouts.count { inRange(it.date, from, today) },
            workoutsBefore = inputs.workouts.count { inRange(it.date, beforeFrom, beforeTo) },
            focusMinutes = focusNow.sumOf { it.minutes },
            focusSessions = focusNow.size,
            focusMinutesBefore = inputs.focus.filter { inRange(it.date, beforeFrom, beforeTo) }.sumOf { it.minutes },
        )
    }

    /** The first day of the range: the Monday [OverviewRange.weeks] weeks back, or the 1st of the month 11 months ago. */
    fun startOf(range: OverviewRange, today: LocalDate): LocalDate =
        range.weeks?.let { Habits.weekStart(today).minusWeeks(it - 1L) } ?: today.withDayOfMonth(1).minusMonths(11)

    /** Oldest first. Day and week bars run their full length (the last may reach past today); month bars end on the month's last day. */
    private fun buckets(range: OverviewRange, from: LocalDate, today: LocalDate): List<Pair<LocalDate, LocalDate>> = when (range) {
        OverviewRange.WEEK -> (0 until 7).map { from.plusDays(it.toLong()).let { d -> d to d } }
        OverviewRange.MONTH, OverviewRange.TWELVE_WEEKS -> (0 until range.buckets).map { from.plusDays(it * 7L) to from.plusDays(it * 7L + 6) }
        OverviewRange.YEAR -> (0 until 12).map {
            val first = from.plusMonths(it.toLong())
            first to first.plusMonths(1).minusDays(1)
        }
    }
}
