package com.homeworkoutcoach.app.ui.shell

import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.progress.OverviewPresenter
import com.homeworkoutcoach.engine.CharacterInputs
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.OverviewRange
import com.homeworkoutcoach.engine.Overviews
import com.homeworkoutcoach.engine.Tasks
import java.time.LocalDate

/** What the hub summaries are counted from. */
data class HubInputs(
    val started: Boolean,
    val level: Level,
    val daysPerWeek: Int,
    val inputs: CharacterInputs,
    val goals: List<Goal>,
    /** The latest weigh-in: its day and kilograms. */
    val latestWeight: Pair<LocalDate, Double>?,
    /** The quality chosen as this week's focus ("Consistency"), or null. */
    val characterFocus: String?,
    val today: LocalDate,
)

/**
 * The big number at the top of a hub: [fraction] fills a ring (null for none), [big] is the number or word inside it,
 * [label] says what it counts and [sub] adds a line.
 */
data class HubHeroUi(val fraction: Float?, val big: String, val label: String, val sub: String?)

/** A hub's top card, and a live line for each page card (by route): what is happening there right now. */
data class HubUi(
    val hero: HubHeroUi?,
    val lines: Map<String, String>,
    /** 0 to 1 for the pages that have something to fill, by route: the little bar on a tile. */
    val fractions: Map<String, Float> = emptyMap(),
)

/** Puts today's numbers on the hubs. Nothing here is a score, and nothing is flagged: a quiet day is said plainly. */
object HubPresenter {
    fun present(tab: Tab, h: HubInputs): HubUi = when (tab) {
        Tab.Today -> HubUi(null, emptyMap())
        Tab.Train -> train(h)
        Tab.Plan -> plan(h)
        Tab.Reflect -> reflect(h)
        Tab.Me -> HubUi(
            null,
            if (h.started) mapOf("setup" to "${Copy.levelName(h.level)}, ${Copy.count(h.daysPerWeek, "day")} a week") else emptyMap(),
        )
    }

    private fun train(h: HubInputs): HubUi {
        val monday = Habits.weekStart(h.today)
        val done = h.inputs.workouts.count { !it.date.isBefore(monday) && !it.date.isAfter(h.today) }
        val planned = h.inputs.plannedWorkoutsPerWeek
        val hero = if (!h.started || planned == null) {
            HubHeroUi(null, "–", "Set up your plan to begin", null)
        } else {
            HubHeroUi(
                (done.toFloat() / planned).coerceIn(0f, 1f), "$done/$planned", "workouts this week",
                if (done >= planned) "Week complete. Well done." else "${planned - done} to go this week",
            )
        }
        val lines = buildMap {
            if (planned != null) put("week", "${done} of $planned this week")
            h.latestWeight?.let { (day, kg) -> put("body", "${Copy.kg(kg)} kg, ${PlannerFormat.dayShort(day, h.today)}") }
        }
        val fractions = buildMap { if (planned != null && planned > 0) put("week", (done.toFloat() / planned).coerceIn(0f, 1f)) }
        return HubUi(hero, lines, fractions)
    }

    private fun plan(h: HubInputs): HubUi {
        val i = h.inputs
        val due = i.habits.filter { Habits.isDue(it.habit, it.log, h.today) }
        val habitsDone = due.count { Habits.isDone(it.habit, it.log, h.today) }
        val tasks = Tasks.dayPlan(i.tasks, h.today, h.today)
        val routinesDone = i.routines.count { r -> i.routineDone.any { it.routineId == r.id && it.date == h.today } }
        val focusMinutes = i.focus.filter { it.date == h.today }.sumOf { it.minutes }
        val hero = if (due.isEmpty()) {
            HubHeroUi(null, "–", if (i.habits.isEmpty()) "Add a habit to begin" else "No habits due today", null)
        } else {
            HubHeroUi(
                habitsDone.toFloat() / due.size, "$habitsDone/${due.size}", "habits today",
                if (habitsDone == due.size) "All done today. Nice." else "${due.size - habitsDone} to go today",
            )
        }
        val lines = buildMap {
            put("habits", if (i.habits.isEmpty()) "Add your first habit" else if (due.isEmpty()) "None due today" else "$habitsDone of ${due.size} done today")
            put("routines", if (i.routines.isEmpty()) "Add a routine" else "$routinesDone of ${i.routines.size} done today")
            put("planner", if (tasks.total == 0) "Nothing planned today" else "${tasks.done} of ${tasks.total} done today")
            put("focus", if (focusMinutes > 0) "${HabitFormat.minutes(focusMinutes)} today" else "No session yet today")
        }
        val fractions = buildMap {
            if (due.isNotEmpty()) put("habits", habitsDone.toFloat() / due.size)
            if (i.routines.isNotEmpty()) put("routines", routinesDone.toFloat() / i.routines.size)
            if (tasks.total > 0) put("planner", tasks.done.toFloat() / tasks.total)
        }
        return HubUi(hero, lines, fractions)
    }

    private fun reflect(h: HubInputs): HubUi {
        val week = OverviewPresenter.present(Overviews.compute(h.inputs, OverviewRange.WEEK, h.today), h.today)
        val hero = if (!week.hasHabits) {
            HubHeroUi(null, "–", "Add a habit and your week shows here", null)
        } else {
            HubHeroUi(week.fraction, "${week.percent}%", "of habit days this week", week.trend)
        }
        val running = h.goals.count { !h.today.isBefore(it.startDate) && !h.today.isAfter(it.endDate) }
        val lines = buildMap {
            if (week.hasHabits) put("progress", "${week.percent}% of habit days this week")
            put("goals", if (h.goals.isEmpty()) "Set your first goal" else if (running == 0) Copy.count(h.goals.size, "goal") else "$running running")
            put("journal", if (h.today in h.inputs.journalDays) "Written today" else "Not written yet today")
            h.characterFocus?.let { put("character", "This week: ${it.lowercase()}") }
        }
        val fractions = buildMap {
            if (week.hasHabits) put("progress", week.fraction)
            if (h.today in h.inputs.journalDays) put("journal", 1f)
        }
        return HubUi(hero, lines, fractions)
    }
}
