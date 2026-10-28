package com.homeworkoutcoach.app.ui.today

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.habits.HabitRowUi
import com.homeworkoutcoach.app.ui.habits.HabitsUi
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.planner.PlannerUi
import com.homeworkoutcoach.app.ui.routines.RoutineRowUi
import com.homeworkoutcoach.app.ui.routines.RoutinesUi
import com.homeworkoutcoach.app.ui.week.WeekUi
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Routines
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The Workout part of the Today screen. */
sealed interface WorkoutCardUi {
    /** No workout plan has been set up yet. */
    data object NoPlan : WorkoutCardUi

    /** A training day with a workout to do: [detail] is "5 exercises, about 45 min", [week] is "1 of 3 this week". */
    data class Next(val title: String, val detail: String, val week: String, val canStart: Boolean) : WorkoutCardUi

    /** A workout was finished today. */
    data class Done(val title: String) : WorkoutCardUi

    /** Not a training day. [nextTitle] is the workout that is up next, [nextDay] the day it is planned for ("Fri"). */
    data class Rest(val nextTitle: String, val nextDay: String, val canStart: Boolean) : WorkoutCardUi
}

object WorkoutCardPresenter {
    fun present(
        settings: UserSettings,
        swaps: Map<String, String>,
        week: WeekUi?,
        workouts: List<Workout>,
        lib: Library,
        planner: Planner,
        today: LocalDate,
        zone: ZoneId,
    ): WorkoutCardUi {
        if (!settings.started || week == null) return WorkoutCardUi.NoPlan
        workouts.lastOrNull { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() == today }
            ?.let { return WorkoutCardUi.Done(planner.dayName(it.label)) }
        val hero = week.hero
        val trainingDays = planner.weekdays(settings.planSettings(swaps)).sorted()
        val todayIndex = today.dayOfWeek.value - 1
        if (todayIndex in trainingDays) {
            return WorkoutCardUi.Next(hero.title, hero.summary, "${hero.done} of ${hero.total} this week", hero.canStart)
        }
        val next = trainingDays.firstOrNull { it > todayIndex } ?: trainingDays.first()
        return WorkoutCardUi.Rest(hero.title, lib.dayNames[next].take(3), hero.canStart)
    }
}

data class TodayUi(
    /** "Good afternoon, Asha". */
    val greeting: String,
    /** "Wednesday 28 October". */
    val date: String,
    /** "5 of 9 done". */
    val summary: String,
    val workout: WorkoutCardUi,
    /** The routines for the current part of the day. */
    val routines: List<RoutineRowUi>,
    /** The habits due today. */
    val habits: List<HabitRowUi>,
    /** Whether any habit exists, so an empty list can invite adding one. */
    val hasHabits: Boolean,
    /** Today's tasks, and what was carried over. */
    val tasks: PlannerUi,
)

/** Puts the day together: the workout, the routines for now, the habits due and the tasks. */
object TodayPresenter {
    fun present(
        name: String,
        now: LocalDateTime,
        workout: WorkoutCardUi,
        routines: RoutinesUi,
        habits: HabitsUi,
        tasks: PlannerUi,
    ): TodayUi {
        val part = Routines.partOf(now.toLocalTime())
        val routinesNow = routines.routines.filter { it.isNow }

        // What counts towards "x of y done". A habit that only mirrors something already counted (the Workout habit
        // next to the workout, the Morning routine habit next to the Morning routine) is not counted twice.
        val workoutCounted = workout is WorkoutCardUi.Next || workout is WorkoutCardUi.Done
        val countedRoutines = routinesNow.filter { it.canRun }
        val countedHabits = habits.today.filterNot { h ->
            (h.source == AutoSource.WORKOUT && workoutCounted) ||
                (h.source == AutoSource.MORNING_ROUTINE && countedRoutines.any { it.partKey == DayPart.MORNING }) ||
                (h.source == AutoSource.NIGHT_ROUTINE && countedRoutines.any { it.partKey == DayPart.EVENING })
        }
        val total = (if (workoutCounted) 1 else 0) + countedRoutines.size + countedHabits.size + tasks.tasks.size
        val done = (if (workout is WorkoutCardUi.Done) 1 else 0) +
            countedRoutines.count { it.doneToday } + countedHabits.count { it.done } + tasks.tasks.count { it.done }

        val first = name.trim().substringBefore(' ')
        val hello = when (part) {
            DayPart.MORNING -> "Good morning"
            DayPart.AFTERNOON -> "Good afternoon"
            DayPart.EVENING -> "Good evening"
        }
        return TodayUi(
            greeting = if (first.isEmpty()) hello else "$hello, $first",
            date = PlannerFormat.dayLong(now.toLocalDate()),
            summary = when {
                total == 0 -> "Nothing planned today."
                done == total -> "All done for today."
                else -> "$done of $total done"
            },
            workout = workout,
            routines = routinesNow,
            habits = habits.today,
            hasHabits = habits.today.isNotEmpty() || habits.other.isNotEmpty(),
            tasks = tasks,
        )
    }
}
