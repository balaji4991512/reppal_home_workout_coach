package com.homeworkoutcoach.app.ui.character

import com.homeworkoutcoach.app.data.CharacterFocus
import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.Character
import com.homeworkoutcoach.engine.CharacterInputs
import com.homeworkoutcoach.engine.CharacterWeek
import com.homeworkoutcoach.engine.CoachText
import com.homeworkoutcoach.engine.FocusFact
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.HabitFact
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.Quality
import com.homeworkoutcoach.engine.Reflections
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.TemplateCoach
import com.homeworkoutcoach.engine.Trend
import com.homeworkoutcoach.engine.WeeklyReflection
import com.homeworkoutcoach.engine.WorkoutFact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Turns what is stored into what the Character rules count from. */
object CharacterInputsBuilder {
    fun build(
        plannedWorkoutsPerWeek: Int?,
        workouts: List<Workout>,
        habits: List<StoredHabit>,
        entries: Map<String, Map<LocalDate, Int>>,
        routines: List<Routine>,
        routineDone: Collection<RoutineDone>,
        tasks: List<Task>,
        journal: List<JournalEntry>,
        focus: List<FocusSession>,
        today: LocalDate,
        zone: ZoneId,
    ): CharacterInputs {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val workoutDays = HabitLogs.workoutDays(workouts, zone)
        val journalDays = journal.map { it.date }.toSet()
        val sourceDays = HabitLogs.sourceDays(routines, routineDone, journalDays)
        val live = habits.filterNot { it.habit.archived }
        val facts = live.map { HabitFact(it.habit, HabitLogs.log(it.habit, entries[it.habit.id].orEmpty(), workoutDays, sourceDays)) }

        // Every day with anything done, over all history, to find the quiet stretches and the comebacks.
        val habitDays = facts.flatMap { f ->
            generateSequence(f.habit.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.filter { Habits.isDone(f.habit, f.log, it) }.toList()
        }
        val focusDays = focus.map { day(it.startMillis) }
        val activity = (workoutDays + journalDays + routineDone.map { it.date } + focusDays + habitDays).toSet()
        return CharacterInputs(
            plannedWorkoutsPerWeek = plannedWorkoutsPerWeek,
            workouts = workouts.map { WorkoutFact(day(it.dateMillis), it.painIds.size) },
            habits = facts,
            routines = routines,
            routineDone = routineDone,
            tasks = tasks,
            journalDays = journalDays,
            focus = focus.map { FocusFact(day(it.startMillis), it.actualMinutes) },
            activityDays = activity,
        )
    }
}

data class QualityCardUi(
    val quality: Quality,
    val label: String,
    val line: String,
    /** "up from last week", "about the same as last week", "quieter than last week", or null. */
    val trend: String?,
    val isFocus: Boolean,
)

data class ReflectionUi(
    /** "26 Oct to 1 Nov". */
    val weekLabel: String,
    val wentWell: String,
    val differently: String,
    val hasEntry: Boolean,
)

data class EarlierReflectionUi(val weekLabel: String, val wentWell: String, val differently: String)

data class CharacterUi(
    val weekLabel: String,
    /** The week in plain words, from the coach. */
    val summary: String,
    val cards: List<QualityCardUi>,
    val focus: Quality,
    val suggested: Quality,
    val reflection: ReflectionUi,
    val earlier: List<EarlierReflectionUi>,
)

class CharacterPresenter(private val coach: CoachText = TemplateCoach) {
    fun present(week: CharacterWeek, chosen: CharacterFocus?, reflections: List<WeeklyReflection>): CharacterUi {
        val focus = chosen?.takeIf { it.weekStartEpochDay == week.weekStart.toEpochDay() }
            ?.let { c -> Quality.entries.firstOrNull { it.name == c.quality } } ?: week.suggestedFocus
        val mine = reflections.firstOrNull { it.weekStart == week.weekStart }
        return CharacterUi(
            weekLabel = weekLabel(week.weekStart),
            summary = coach.weeklySummary(week, focus),
            cards = week.evidence.map { e ->
                QualityCardUi(
                    quality = e.quality,
                    label = e.quality.label,
                    line = e.line,
                    trend = when (e.trend) {
                        Trend.UP -> "up from last week"
                        Trend.SAME -> "about the same as last week"
                        Trend.QUIETER -> "quieter than last week"
                        null -> null
                    },
                    isFocus = e.quality == focus,
                )
            },
            focus = focus,
            suggested = week.suggestedFocus,
            reflection = ReflectionUi(weekLabel(week.weekStart), mine?.wentWell.orEmpty(), mine?.differently.orEmpty(), mine != null),
            earlier = reflections.filter { it.weekStart != week.weekStart }.sortedByDescending { it.weekStart }.take(4)
                .map { EarlierReflectionUi(weekLabel(it.weekStart), it.wentWell, it.differently) },
        )
    }

    companion object {
        /** "26 Oct to 1 Nov" for the week that starts on [monday]. */
        fun weekLabel(monday: LocalDate): String {
            fun part(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
            return "${part(monday)} to ${part(monday.plusDays(6))}"
        }
    }
}

/** What the Character screen does with the week's reflection. */
object ReflectionRules {
    fun problem(wentWell: String, differently: String): String? = Reflections.problem(wentWell, differently)
}
