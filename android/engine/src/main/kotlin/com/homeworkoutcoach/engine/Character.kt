package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The qualities the Character screen shows evidence for (specs/JOURNAL_GOALS_FOCUS_CHARACTER_SPEC.md, section 4). */
enum class Quality(val label: String) {
    CONSISTENCY("Consistency"),
    DISCIPLINE("Discipline"),
    COMEBACK("Comeback"),
    LISTENING("Listening to your body"),
    FOCUS("Focus"),
    REFLECTION("Reflection"),
}

/** A finished workout and how many exercises were stopped for pain in it. */
data class WorkoutFact(val date: LocalDate, val painStops: Int = 0)

/** A habit with what was recorded for it, as the habit rules read it. */
data class HabitFact(val habit: Habit, val log: HabitLog)

data class FocusFact(val date: LocalDate, val minutes: Int)

/** Everything the evidence is counted from. Nothing here is a score; it is what the person did. */
data class CharacterInputs(
    /** Workouts a week in the plan, or null if there is no plan. */
    val plannedWorkoutsPerWeek: Int?,
    val workouts: List<WorkoutFact>,
    /** Habits that are not archived. */
    val habits: List<HabitFact>,
    val routines: List<Routine>,
    val routineDone: Collection<RoutineDone>,
    val tasks: List<Task>,
    val journalDays: Set<LocalDate>,
    val focus: List<FocusFact>,
    /** Every day with anything done (a workout, a habit, a routine, a journal line, a focus session), over all history. */
    val activityDays: Set<LocalDate>,
)

/** The counts for one week, up to today if it is the current one. */
data class WeekFacts(
    /** Days counted: 7 for a past week, fewer for the week in progress. */
    val daysCounted: Int,
    val workoutsDone: Int,
    val workoutsPlanned: Int?,
    val habitDone: Int,
    val habitDue: Int,
    val routineDone: Int,
    val routineExpected: Int,
    val tasksDone: Int,
    val tasksPlanned: Int,
    val comebacks: Int,
    /** A quiet stretch of two or more days happened (or a comeback after one). */
    val hadBreak: Boolean,
    val painStops: Int,
    val recoveryDays: Int,
    val focusSessions: Int,
    val focusMinutes: Int,
    /** Whether the focus timer has ever been used. */
    val focusUsed: Boolean,
    val journalDays: Int,
    /** Whether anything has ever been written in the journal. */
    val journalUsed: Boolean,
)

enum class Trend { UP, SAME, QUIETER }

/** One quality: a plain line of what was done, a 0 to 1 [score] used only to compare weeks, and the trend. */
data class QualityEvidence(val quality: Quality, val line: String, val score: Double?, val trend: Trend?)

data class CharacterWeek(
    val weekStart: LocalDate,
    val facts: WeekFacts,
    val lastWeek: WeekFacts,
    val evidence: List<QualityEvidence>,
    /** The quality that was quietest last week; the person may pick another. */
    val suggestedFocus: Quality,
)

object Character {
    private const val TREND_STEP = 0.10

    fun week(inputs: CharacterInputs, today: LocalDate): CharacterWeek {
        val start = Habits.weekStart(today)
        val now = facts(inputs, start, today)
        val last = facts(inputs, start.minusDays(7), today)
        return CharacterWeek(
            weekStart = start,
            facts = now,
            lastWeek = last,
            evidence = Quality.entries.map { q ->
                val a = score(q, now)
                val b = score(q, last)
                QualityEvidence(q, line(q, now), a, if (trendable(q)) trend(a, b) else null)
            },
            suggestedFocus = autoFocus(last),
        )
    }

    /** The counts for the week starting [weekStart], only for days up to [today]. */
    fun facts(inputs: CharacterInputs, weekStart: LocalDate, today: LocalDate): WeekFacts {
        val days = (0L..6L).map { weekStart.plusDays(it) }.filter { !it.isAfter(today) }
        val workoutDates = inputs.workouts.map { it.date }.toSet()
        val activity = inputs.activityDays
        val journalUsed = inputs.journalDays.isNotEmpty()
        val focusUsed = inputs.focus.isNotEmpty()
        if (days.isEmpty()) {
            return WeekFacts(0, 0, inputs.plannedWorkoutsPerWeek, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0, focusUsed, 0, journalUsed)
        }
        val from = days.first()
        val to = days.last()
        fun inWeek(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

        val stats = inputs.habits.map { Habits.stats(it.habit, it.log, from, today, until = to) }
        val runnable = inputs.routines.filter { it.items.isNotEmpty() }.map { it.id }.toSet()
        val weekTasks = inputs.tasks.filter { inWeek(it.date) }
        val comebacks = days.count { d ->
            d in activity && activity.filter { it < d }.maxOrNull()?.let { ChronoUnit.DAYS.between(it, d) >= 3 } == true
        }
        val quiet = days.zipWithNext().any { (a, b) -> a !in activity && b !in activity }
        return WeekFacts(
            daysCounted = days.size,
            workoutsDone = inputs.workouts.count { inWeek(it.date) },
            workoutsPlanned = inputs.plannedWorkoutsPerWeek,
            habitDone = stats.sumOf { it.done },
            habitDue = stats.sumOf { it.due },
            routineDone = inputs.routineDone.count { it.routineId in runnable && inWeek(it.date) },
            routineExpected = runnable.size * days.size,
            tasksDone = weekTasks.count { it.done },
            tasksPlanned = weekTasks.size,
            comebacks = comebacks,
            hadBreak = comebacks > 0 || quiet,
            painStops = inputs.workouts.filter { inWeek(it.date) }.sumOf { it.painStops },
            recoveryDays = days.count { it !in workoutDates && it.minusDays(1) in workoutDates },
            focusSessions = inputs.focus.count { inWeek(it.date) },
            focusMinutes = inputs.focus.filter { inWeek(it.date) }.sumOf { it.minutes },
            focusUsed = focusUsed,
            journalDays = days.count { it in inputs.journalDays },
            journalUsed = journalUsed,
        )
    }

    /**
     * How the week looks for [q], 0 to 1, or null when there is nothing to base it on. Rates are of the days counted, so
     * a week in progress compares fairly with a finished one. Used to compare weeks and to pick a focus; never shown.
     */
    fun score(q: Quality, f: WeekFacts): Double? {
        if (f.daysCounted == 0) return null
        fun mean(vararg parts: Double?): Double? = parts.filterNotNull().takeIf { it.isNotEmpty() }?.average()
        return when (q) {
            Quality.CONSISTENCY -> mean(
                f.workoutsPlanned?.let { planned ->
                    val so = planned * f.daysCounted / 7.0
                    if (so > 0) (f.workoutsDone / so).coerceAtMost(1.0) else null
                },
                if (f.habitDue > 0) (f.habitDone.toDouble() / f.habitDue).coerceAtMost(1.0) else null,
            )
            Quality.DISCIPLINE -> mean(
                if (f.routineExpected > 0) (f.routineDone.toDouble() / f.routineExpected).coerceAtMost(1.0) else null,
                if (f.tasksPlanned > 0) f.tasksDone.toDouble() / f.tasksPlanned else null,
            )
            Quality.COMEBACK -> if (!f.hadBreak) null else if (f.comebacks > 0) 1.0 else 0.0
            Quality.LISTENING -> if (f.workoutsDone == 0 && f.painStops == 0) null else ((f.painStops + f.recoveryDays) / 2.0).coerceAtMost(1.0)
            Quality.FOCUS -> if (!f.focusUsed) null else (f.focusMinutes / (120.0 * f.daysCounted / 7.0)).coerceAtMost(1.0)
            Quality.REFLECTION -> if (!f.journalUsed) null else f.journalDays.toDouble() / f.daysCounted
        }
    }

    /**
     * The quality with the lowest score last week (ties: in the order of the list); Consistency if there is none. Only the
     * qualities that are about doing more are considered: a comeback or a rest day is not something to aim for.
     */
    fun autoFocus(last: WeekFacts): Quality =
        Quality.entries.filter(::trendable).mapNotNull { q -> score(q, last)?.let { q to it } }.minByOrNull { it.second }?.first
            ?: Quality.CONSISTENCY

    /** Comebacks and listening to your body are not about doing more, so they have no "up" or "quieter". */
    private fun trendable(q: Quality) = q != Quality.COMEBACK && q != Quality.LISTENING

    private fun trend(now: Double?, before: Double?): Trend? {
        if (now == null || before == null) return null
        val d = now - before
        return when {
            d >= TREND_STEP -> Trend.UP
            d <= -TREND_STEP -> Trend.QUIETER
            else -> Trend.SAME
        }
    }

    private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

    private fun minutes(n: Int): String = when {
        n < 60 -> "$n min"
        n % 60 == 0 -> "${n / 60} h"
        else -> "${n / 60} h ${n % 60} min"
    }

    /** One plain line of what was done. */
    fun line(q: Quality, f: WeekFacts): String {
        if (f.daysCounted == 0) return "The week has only just started."
        val parts: List<String> = when (q) {
            Quality.CONSISTENCY -> listOfNotNull(
                f.workoutsPlanned?.let {
                    if (f.daysCounted == 7) "Workouts: ${f.workoutsDone} of $it planned"
                    else "Workouts: ${f.workoutsDone} so far, $it planned this week"
                },
                if (f.habitDue > 0) "Habits: ${f.habitDone} of ${f.habitDue} days" else null,
            ).ifEmpty { return "Add habits or a workout plan and this will fill in." }
            Quality.DISCIPLINE -> listOfNotNull(
                if (f.routineExpected > 0) "Routines: ${f.routineDone} of ${f.routineExpected} days" else null,
                if (f.tasksPlanned > 0) "Tasks: ${f.tasksDone} of ${f.tasksPlanned} done" else null,
            ).ifEmpty { return "Add a routine or some tasks and this will fill in." }
            Quality.COMEBACK -> return when {
                f.comebacks > 0 -> "You came back after a quiet stretch ${plural(f.comebacks, "time")}."
                f.hadBreak -> "A quiet stretch, and a fresh start is always one small thing away."
                else -> "No comeback needed: you kept going."
            }
            Quality.LISTENING -> return when {
                f.painStops == 0 && f.recoveryDays == 0 -> if (f.workoutsDone == 0) "Nothing to count yet." else "No pain reported, and no rest day after training yet."
                else -> listOfNotNull(
                    if (f.painStops > 0) "Stopped for pain ${plural(f.painStops, "time")}" else null,
                    if (f.recoveryDays > 0) "${plural(f.recoveryDays, "rest day")} after training" else null,
                ).joinToString(". ") + "."
            }
            Quality.FOCUS -> return if (f.focusSessions == 0) "No focus sessions yet." else
                "${plural(f.focusSessions, "session")}, ${minutes(f.focusMinutes)}."
            Quality.REFLECTION -> return if (f.journalDays == 0) "Nothing written yet." else
                "Journal written on ${f.journalDays} of ${f.daysCounted} days."
        }
        return parts.joinToString(". ") + "."
    }
}

/** Words for the week. Implementations may only word what the numbers say; they never change a plan or a load. */
interface CoachText {
    fun weeklySummary(week: CharacterWeek, focus: Quality): String
}

/** The built-in coach: fills in a template from the counts. Nothing leaves the phone. */
object TemplateCoach : CoachText {
    override fun weeklySummary(week: CharacterWeek, focus: Quality): String {
        val f = week.facts
        val sentences = mutableListOf<String>()
        if (f.daysCounted == 0) return "A new week. Pick one small thing to start with."

        val did = listOfNotNull(
            f.workoutsDone.takeIf { it > 0 }?.let { "finished ${count(it, "workout")}" },
            f.habitDone.takeIf { it > 0 }?.let { "kept up $it habit ${if (it == 1) "day" else "days"}" },
            f.routineDone.takeIf { it > 0 }?.let { "finished a routine on $it ${if (it == 1) "day" else "days"}" },
            f.tasksDone.takeIf { it > 0 }?.let { "got ${count(it, "task")} done" },
            f.focusSessions.takeIf { it > 0 }?.let { "did ${count(it, "focus session")}" },
            f.journalDays.takeIf { it > 0 }?.let { "wrote in your journal on $it ${if (it == 1) "day" else "days"}" },
        )
        sentences += if (did.isEmpty()) "This week is just getting started." else "This ${if (f.daysCounted == 7) "week" else "week so far"} you " + join(did) + "."
        if (f.comebacks > 0) sentences += "You came back after a quiet stretch ${count(f.comebacks, "time")}, and that counts."
        if (f.painStops > 0) sentences += "You stopped when something hurt, which is the right call."
        val strongest = week.evidence.filter { it.score != null && it.score >= 0.7 && it.quality != focus }.maxByOrNull { it.score!! }
        strongest?.let { sentences += "${it.quality.label} looks solid." }
        sentences += "This week's focus is ${focus.label.lowercase()}: ${tip(focus)}"
        return sentences.joinToString(" ")
    }

    private fun count(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"

    private fun join(parts: List<String>) = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()

    private fun tip(q: Quality) = when (q) {
        Quality.CONSISTENCY -> "pick one habit and do it every day, even the smallest version."
        Quality.DISCIPLINE -> "finish one routine each day before anything else."
        Quality.COMEBACK -> "if you miss a day, do the smallest version the next day."
        Quality.LISTENING -> "notice when you are tired and take the rest day when you need it."
        Quality.FOCUS -> "try one focus session a day."
        Quality.REFLECTION -> "write two lines in your journal each evening."
    }
}

/** What was written at the end of a week. */
data class WeeklyReflection(val weekStart: LocalDate, val wentWell: String = "", val differently: String = "") {
    init {
        require(wentWell.isNotBlank() || differently.isNotBlank()) { "a reflection needs at least one answer" }
    }
}

object Reflections {
    const val MAX = 300
    const val WENT_WELL = "What went well this week?"
    const val DIFFERENTLY = "What will you do differently?"

    fun problem(wentWell: String, differently: String): String? = when {
        wentWell.isBlank() && differently.isBlank() -> "Write at least one answer."
        wentWell.trim().length > MAX || differently.trim().length > MAX -> "Keep each answer to $MAX characters."
        else -> null
    }

    fun create(weekStart: LocalDate, wentWell: String, differently: String) = WeeklyReflection(weekStart, wentWell.trim(), differently.trim())
}
