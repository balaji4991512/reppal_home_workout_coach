package com.homeworkoutcoach.app.ui.progress

import com.homeworkoutcoach.engine.Overview
import com.homeworkoutcoach.engine.OverviewBar
import com.homeworkoutcoach.engine.OverviewRange
import com.homeworkoutcoach.engine.Trend
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One bar: [percent] is 0 to 100, or null when nothing was due (the bar is left empty, not zero). */
data class OverviewBarUi(val label: String, val percent: Int?, val current: Boolean)

data class OverviewUi(
    val range: OverviewRange,
    /** Whether any habit day was due in the range; without it the ring and bars have nothing to show. */
    val hasHabits: Boolean,
    /** 0 to 100. */
    val percent: Int,
    /** 0 to 1, for the ring. */
    val fraction: Float,
    val habitsDone: Int,
    /** "out of 41 due so far". */
    val habitsOutOf: String,
    /** "up from last week", "about the same as last week", "quieter than last week", or null. */
    val trend: String?,
    val bars: List<OverviewBarUi>,
    val barsTitle: String,
    val workouts: Int,
    /** "2 before": the span just before. */
    val workoutsBefore: String,
    /** "1 h 20 min", or "None yet". */
    val focusTime: String,
    val focusBefore: String,
)

/** Puts the overview into words. Nothing is flagged: a quiet stretch is said plainly. */
object OverviewPresenter {
    fun present(o: Overview, today: LocalDate): OverviewUi {
        val before = beforeLabel(o.range)
        return OverviewUi(
            range = o.range,
            hasHabits = o.habitsDue > 0,
            percent = o.percent ?: 0,
            fraction = (o.percent ?: 0) / 100f,
            habitsDone = o.habitsDone,
            habitsOutOf = "out of ${o.habitsDue} due so far",
            trend = when (o.habitTrend) {
                Trend.UP -> "up from $before"
                Trend.SAME -> "about the same as $before"
                Trend.QUIETER -> "quieter than $before"
                null -> null
            },
            bars = o.bars.map { OverviewBarUi(label(o.range, it), it.percent, today in it.from..it.to) },
            barsTitle = when (o.range) {
                OverviewRange.WEEK -> "Habit days done, by day"
                OverviewRange.MONTH, OverviewRange.TWELVE_WEEKS -> "Habit days done, by week"
                OverviewRange.YEAR -> "Habit days done, by month"
            },
            workouts = o.workouts,
            workoutsBefore = "${o.workoutsBefore} before",
            focusTime = minutes(o.focusMinutes),
            focusBefore = if (o.focusMinutesBefore <= 0) "none before" else "${minutes(o.focusMinutesBefore)} before",
        )
    }

    /** What the span before is called, as in "up from last week". */
    fun beforeLabel(range: OverviewRange) = when (range) {
        OverviewRange.WEEK -> "last week"
        OverviewRange.MONTH -> "the 4 weeks before"
        OverviewRange.TWELVE_WEEKS -> "the 12 weeks before"
        OverviewRange.YEAR -> "the year before"
    }

    /** A day's initial, a week's first day ("26/10") or a month's initial. */
    private fun label(range: OverviewRange, bar: OverviewBar) = when (range) {
        OverviewRange.WEEK -> bar.from.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
        OverviewRange.MONTH, OverviewRange.TWELVE_WEEKS -> "${bar.from.dayOfMonth}/${bar.from.monthValue}"
        OverviewRange.YEAR -> bar.from.month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
    }

    fun minutes(total: Int) = when {
        total <= 0 -> "None yet"
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }
}
