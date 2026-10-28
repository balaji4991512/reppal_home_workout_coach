package com.homeworkoutcoach.app.ui.goals

import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.EndPreset
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.GoalProgress
import com.homeworkoutcoach.engine.GoalState
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.Goals
import com.homeworkoutcoach.engine.Pace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Turns stored records into what the goal rules read. */
object GoalInputs {
    /** The days from [from] to [to] on which [habit] counted as done, the way the habit screens count them. */
    fun habitDoneDays(
        habit: Habit,
        entries: Map<LocalDate, Int>,
        workoutDays: Set<LocalDate>,
        sourceDays: Map<AutoSource, Set<LocalDate>>,
        from: LocalDate,
        to: LocalDate,
    ): Set<LocalDate> {
        val log = HabitLogs.log(habit, entries, workoutDays, sourceDays)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { Habits.isDone(habit, log, it) }.toSet()
    }

    /** One weigh-in per day, the first of each day (as the Body screen does), oldest day first. */
    fun firstWeighInPerDay(weights: List<BodyWeight>, zone: ZoneId): List<Pair<LocalDate, Double>> =
        weights.sortedBy { it.dateMillis }
            .groupBy { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }
            .map { (day, list) -> day to list.first().weightKg }
            .sortedBy { it.first }
}

data class GoalRowUi(
    val id: String,
    val title: String,
    /** "12 of 20 workouts", "84.5 kg now, goal 80 kg". */
    val detail: String,
    /** 0 to 1, for the thin bar. */
    val fraction: Float,
    /** "18 days left, on track", "Reached", "Ended with 17 of 20". */
    val status: String,
    val state: GoalState,
)

data class GoalsUi(
    /** Active and not yet started goals, soonest end first. */
    val active: List<GoalRowUi>,
    val reached: List<GoalRowUi>,
    val ended: List<GoalRowUi>,
    val summary: String,
)

object GoalFormat {
    fun number(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else "%.1f".format(x)

    fun daysLeft(n: Int): String = when (n) {
        0 -> "Last day"
        1 -> "1 day left"
        else -> "$n days left"
    }

    fun pace(p: Pace): String = when (p) {
        Pace.AHEAD -> "ahead of the pace"
        Pace.ON_TRACK -> "on track"
        Pace.BEHIND -> "a little behind, still time"
    }
}

class GoalsPresenter {
    /**
     * [habitNames] and [habitDoneDates] are by habit id. [weights] is one weigh-in per day. [workoutDates] has one date
     * for each finished workout.
     */
    fun present(
        goals: List<Goal>,
        habitNames: Map<String, String>,
        workoutDates: List<LocalDate>,
        habitDoneDates: Map<String, Set<LocalDate>>,
        weights: List<Pair<LocalDate, Double>>,
        today: LocalDate,
    ): GoalsUi {
        val rows = goals.map { g ->
            val p = Goals.progress(g, workoutDates, habitDoneDates[g.habitId].orEmpty(), weights, today)
            g to row(g, p, habitNames, today)
        }
        val active = rows.filter { it.second.state == GoalState.ACTIVE || it.second.state == GoalState.UPCOMING }
        return GoalsUi(
            active = active.map { it.second },
            // the most recent first
            reached = rows.filter { it.second.state == GoalState.REACHED }.sortedByDescending { it.first.endDate }.map { it.second },
            ended = rows.filter { it.second.state == GoalState.ENDED }.sortedByDescending { it.first.endDate }.map { it.second },
            summary = when {
                goals.isEmpty() -> "Pick something bigger to work towards."
                active.isEmpty() -> "Nothing active right now."
                else -> "${active.size} active"
            },
        )
    }

    private fun row(g: Goal, p: GoalProgress, habitNames: Map<String, String>, today: LocalDate): GoalRowUi {
        val cur = GoalFormat.number(p.current)
        val target = GoalFormat.number(p.target)
        val detail = when (g.kind) {
            GoalKind.WORKOUTS -> "$cur of $target workouts"
            GoalKind.HABIT -> "$cur of $target days · ${habitNames[g.habitId] ?: "habit removed"}"
            GoalKind.WEIGHT -> "$cur kg now, goal $target kg"
        }
        val status = when (p.state) {
            GoalState.ACTIVE -> GoalFormat.daysLeft(p.daysLeft) + (p.pace?.let { ", ${GoalFormat.pace(it)}" } ?: "")
            GoalState.UPCOMING -> "Starts ${PlannerFormat.dayShort(g.startDate, today)}"
            GoalState.REACHED -> "Reached"
            GoalState.ENDED -> if (g.kind == GoalKind.WEIGHT) "Ended at $cur kg" else "Ended with $cur of $target"
        }
        return GoalRowUi(g.id, g.title, detail, p.fraction.toFloat(), status, p.state)
    }
}

/** What the Add / Edit goal form holds until it is saved. */
data class GoalDraft(
    /** Null for a new goal. */
    val id: String? = null,
    val kind: GoalKind = GoalKind.WORKOUTS,
    /** The name the user typed; used only once [titleEdited]. Until then the name follows the suggestion. */
    val title: String = "",
    val titleEdited: Boolean = false,
    val targetText: String = "",
    val habitId: String? = null,
    /** The weight now, typed when there is no weigh-in to take it from. */
    val weightNowText: String = "",
    val endDate: LocalDate,
    /** Kept when editing; a new goal starts today. */
    val startDate: LocalDate? = null,
    /** Kept when editing a weight goal. */
    val startWeightKg: Double? = null,
) {
    val target: Double? get() = targetText.trim().replace(',', '.').toDoubleOrNull()

    /** The weight the goal counts from: the one kept, else what was typed, else the latest weigh-in. */
    fun fromWeight(latestWeightKg: Double?): Double? =
        startWeightKg ?: weightNowText.trim().replace(',', '.').toDoubleOrNull() ?: latestWeightKg

    fun effectiveTitle(habitName: String?): String =
        if (titleEdited) title else target?.let { Goals.suggestedTitle(kind, it, habitName) } ?: ""

    fun problem(habitName: String?, latestWeightKg: Double?, today: LocalDate): String? = Goals.problem(
        // A name that is still following the target is not missing: the target is what to fix first.
        title = if (titleEdited) title else effectiveTitle(habitName).ifBlank { "Goal" },
        kind = kind,
        target = target,
        habitId = habitId,
        startWeightKg = fromWeight(latestWeightKg),
        startDate = startDate ?: today,
        endDate = endDate,
    )

    /** Only call when [problem] is null. */
    fun toGoal(newId: String, habitName: String?, latestWeightKg: Double?, today: LocalDate): Goal = Goal(
        id = id ?: newId,
        title = effectiveTitle(habitName).trim(),
        kind = kind,
        target = checkNotNull(target),
        startDate = startDate ?: today,
        endDate = endDate,
        habitId = habitId.takeIf { kind == GoalKind.HABIT },
        startWeightKg = fromWeight(latestWeightKg).takeIf { kind == GoalKind.WEIGHT },
    )

    companion object {
        fun new(today: LocalDate) = GoalDraft(endDate = Goals.endFor(EndPreset.THIRTY_DAYS, today))

        fun from(g: Goal) = GoalDraft(
            id = g.id,
            kind = g.kind,
            title = g.title,
            titleEdited = true,
            targetText = GoalFormat.number(g.target),
            habitId = g.habitId,
            endDate = g.endDate,
            startDate = g.startDate,
            startWeightKg = g.startWeightKg,
        )
    }
}
