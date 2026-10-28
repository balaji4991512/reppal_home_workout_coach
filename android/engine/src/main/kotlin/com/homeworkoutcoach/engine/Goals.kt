package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a goal counts (specs/JOURNAL_GOALS_FOCUS_CHARACTER_SPEC.md, section 2). */
enum class GoalKind {
    /** Finish N workouts between the start and end days. */
    WORKOUTS,

    /** Do a habit on N days between the start and end days. */
    HABIT,

    /** Reach a weight, counted from the weight at the start. */
    WEIGHT,
}

enum class GoalState { UPCOMING, ACTIVE, REACHED, ENDED }

/** How an active goal is going against an even pace. Never a warning: [BEHIND] is shown as "a little behind". */
enum class Pace { AHEAD, ON_TRACK, BEHIND }

/**
 * A bigger goal. [target] is the number of workouts or days, or the weight in kg. A habit goal names its [habitId];
 * a weight goal keeps [startWeightKg], the weight when it was made, so progress is how far from there to the target.
 */
data class Goal(
    val id: String,
    val title: String,
    val kind: GoalKind,
    val target: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val habitId: String? = null,
    val startWeightKg: Double? = null,
) {
    init {
        require(title.isNotBlank()) { "a goal needs a title" }
        require(target > 0) { "the target must be above zero" }
        require(!endDate.isBefore(startDate)) { "the goal can't end before it starts" }
        when (kind) {
            GoalKind.WORKOUTS -> Unit
            GoalKind.HABIT -> require(habitId != null) { "a habit goal needs a habit" }
            GoalKind.WEIGHT -> require(startWeightKg != null && startWeightKg != target) { "a weight goal needs a different starting weight" }
        }
    }
}

/**
 * [current] is the count so far, or the latest weight in kg. [fraction] runs from 0 to 1. [daysLeft] is 0 on the last
 * day and for goals that are not active. [pace] is only set for an active goal.
 */
data class GoalProgress(
    val current: Double,
    val target: Double,
    val fraction: Double,
    val state: GoalState,
    val pace: Pace?,
    val daysLeft: Int,
)

enum class EndPreset { THIS_WEEK, THIS_MONTH, THIRTY_DAYS }

object Goals {
    const val MAX_TITLE = 60

    /**
     * Where a goal stands on [today]. [workoutDates] has one date per finished workout and [habitDoneDates] the days the
     * goal's habit was done; only days from the start to the end (or today) count. [weights] has one weigh-in per day
     * (the first of each day), as (day, kg); the latest one up to the end (or today) is the current weight.
     * Reaching the target at any time keeps the goal reached, even before the end day.
     */
    fun progress(
        goal: Goal,
        workoutDates: Collection<LocalDate>,
        habitDoneDates: Collection<LocalDate>,
        weights: List<Pair<LocalDate, Double>>,
        today: LocalDate,
    ): GoalProgress {
        val upTo = minOf(goal.endDate, today)
        fun inRange(d: LocalDate) = !d.isBefore(goal.startDate) && !d.isAfter(upTo)
        val current: Double
        val fraction: Double
        when (goal.kind) {
            GoalKind.WORKOUTS -> {
                current = workoutDates.count(::inRange).toDouble()
                fraction = (current / goal.target).coerceIn(0.0, 1.0)
            }
            GoalKind.HABIT -> {
                current = habitDoneDates.toSet().count(::inRange).toDouble()
                fraction = (current / goal.target).coerceIn(0.0, 1.0)
            }
            GoalKind.WEIGHT -> {
                val start = checkNotNull(goal.startWeightKg)
                current = weights.filter { !it.first.isAfter(upTo) }.maxByOrNull { it.first }?.second ?: start
                fraction = ((start - current) / (start - goal.target)).coerceIn(0.0, 1.0)
            }
        }
        val state = when {
            fraction >= 1.0 -> GoalState.REACHED
            today.isBefore(goal.startDate) -> GoalState.UPCOMING
            today.isAfter(goal.endDate) -> GoalState.ENDED
            else -> GoalState.ACTIVE
        }
        val active = state == GoalState.ACTIVE
        return GoalProgress(
            current = current,
            target = goal.target,
            fraction = fraction,
            state = state,
            pace = if (active) pace(goal, fraction, today) else null,
            daysLeft = if (active) ChronoUnit.DAYS.between(today, goal.endDate).toInt() else 0,
        )
    }

    /** Against an even pace over the goal's days: 10 points ahead is ahead, 20 points short is a little behind. */
    private fun pace(goal: Goal, fraction: Double, today: LocalDate): Pace {
        val total = ChronoUnit.DAYS.between(goal.startDate, goal.endDate) + 1
        val elapsed = ChronoUnit.DAYS.between(goal.startDate, today) + 1
        val expected = elapsed.toDouble() / total
        return when {
            fraction >= expected + 0.10 -> Pace.AHEAD
            fraction < expected - 0.20 -> Pace.BEHIND
            else -> Pace.ON_TRACK
        }
    }

    /** The end day for a quick choice. This week ends on Sunday. */
    fun endFor(preset: EndPreset, today: LocalDate): LocalDate = when (preset) {
        EndPreset.THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        EndPreset.THIS_MONTH -> YearMonth.from(today).atEndOfMonth()
        EndPreset.THIRTY_DAYS -> today.plusDays(29)
    }

    /** A title to start from, which the user can change. */
    fun suggestedTitle(kind: GoalKind, target: Double, habitName: String?): String {
        val n = if (target % 1.0 == 0.0) target.toInt().toString() else target.toString()
        return when (kind) {
            GoalKind.WORKOUTS -> if (n == "1") "Finish 1 workout" else "Finish $n workouts"
            GoalKind.HABIT -> "${habitName ?: "A habit"} on $n ${if (n == "1") "day" else "days"}"
            GoalKind.WEIGHT -> "Reach $n kg"
        }
    }

    /** The first thing to fix in what was typed, or null if the goal can be saved. */
    fun problem(
        title: String,
        kind: GoalKind,
        target: Double?,
        habitId: String?,
        startWeightKg: Double?,
        startDate: LocalDate,
        endDate: LocalDate,
    ): String? = when {
        title.isBlank() -> "Give the goal a name."
        title.trim().length > MAX_TITLE -> "Keep the name to $MAX_TITLE characters."
        kind == GoalKind.WEIGHT && (target == null || target < 20.0 || target > 300.0) -> "Enter a weight from 20 to 300 kg."
        kind != GoalKind.WEIGHT && (target == null || target < 1.0 || target > 999.0 || target % 1.0 != 0.0) -> "Enter a whole number from 1 to 999."
        kind == GoalKind.HABIT && habitId == null -> "Pick a habit."
        kind == GoalKind.WEIGHT && startWeightKg == null -> "Weigh in first, or type your weight now."
        kind == GoalKind.WEIGHT && startWeightKg == target -> "Choose a weight different from where you are now."
        endDate.isBefore(startDate) -> "Pick an end day that is not before the start."
        else -> null
    }
}
