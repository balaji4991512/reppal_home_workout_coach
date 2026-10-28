package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.routine
import com.homeworkoutcoach.app.data.storedHabit
import com.homeworkoutcoach.app.data.task
import com.homeworkoutcoach.app.ui.habits.HabitsPresenter
import com.homeworkoutcoach.app.ui.planner.PlannerPresenter
import com.homeworkoutcoach.app.ui.routines.RoutinesPresenter
import com.homeworkoutcoach.app.ui.today.TodayPresenter
import com.homeworkoutcoach.app.ui.today.WorkoutCardPresenter
import com.homeworkoutcoach.app.ui.today.WorkoutCardUi
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.RoutineDone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val WED = LocalDate.of(2026, 10, 28) // a Wednesday; a 3-day plan trains Mon, Wed, Fri

class WorkoutCardTest {
    private val lib = Library.default
    private val planner = Planner(lib)
    private val settings = UserSettings(
        started = true, bench = Bench.FLAT, dumbbellsKg = listOf(5.0, 8.0), daysPerWeek = 3,
        planStartEpochDay = WED.minusDays(30).toEpochDay(),
    )

    private fun weekOn(day: LocalDate, workouts: List<Workout>) =
        WeekPresenter(lib).present(settings, emptyMap(), emptyMap(), workouts, day, ZoneOffset.UTC)

    private fun card(day: LocalDate, s: UserSettings = settings, workouts: List<Workout> = emptyList()) =
        WorkoutCardPresenter.present(s, emptyMap(), if (s.started) weekOn(day, workouts) else null, workouts, lib, planner, day, ZoneOffset.UTC)

    @Test
    fun noPlanYet() {
        assertEquals(WorkoutCardUi.NoPlan, card(WED, UserSettings(started = false)))
    }

    @Test
    fun onATrainingDayTodaysOwnWorkoutIsOffered() {
        // Wednesday is the second session of the week: Full body B, not "the next one in line"
        val c = card(WED) as WorkoutCardUi.Next
        assertEquals("Full body B", c.title)
        assertEquals(weekOn(WED, emptyList()).hero.summary, c.detail)
        assertEquals("0 of 3 this week", c.week) // Monday was not done; it stays skipped
        assertTrue(c.canStart)
        assertEquals("Full body A", (card(WED.minusDays(2)) as WorkoutCardUi.Next).title) // Monday
        assertEquals("Full body A", (card(WED.plusDays(2)) as WorkoutCardUi.Next).title) // Friday
    }

    @Test
    fun onARestDayItSaysWhenTheNextOneIsAndOffersNothingToStart() {
        assertEquals(WorkoutCardUi.Rest("Full body B", "Wed", false), card(WED.minusDays(1))) // Tuesday
        assertEquals("Fri", (card(WED.plusDays(1)) as WorkoutCardUi.Rest).nextDay) // Thursday
        // Saturday and Sunday: nothing is left this week, and no other day's workout is offered
        val saturday = card(WED.plusDays(3)) as WorkoutCardUi.Rest
        assertEquals("", saturday.nextDay)
        assertFalse(saturday.canStart)
        assertEquals("", (card(WED.plusDays(4)) as WorkoutCardUi.Rest).nextDay)
    }

    @Test
    fun aMakeUpDoneTodayDoesNotMakeTodaysOwnWorkoutDone() {
        val makeUp = Workout(
            dateMillis = WED.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), label = "A", plan = emptyList(), sets = emptyMap(),
            madeUpForEpochDay = WED.minusDays(2).toEpochDay(),
        )
        assertTrue(card(WED, workouts = listOf(makeUp)) is WorkoutCardUi.Next)
    }

    @Test
    fun aWorkoutFinishedTodayShowsAsDone() {
        fun at(day: LocalDate) = Workout(dateMillis = day.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), label = "B", plan = emptyList(), sets = emptyMap())
        assertEquals(WorkoutCardUi.Done("Full body B"), card(WED, workouts = listOf(at(WED.minusDays(2)), at(WED))))
        // yesterday's workout does not make today done
        assertTrue(card(WED, workouts = listOf(at(WED.minusDays(1)))) is WorkoutCardUi.Next)
    }
}

class TodayPresenterTest {
    private val routinesP = RoutinesPresenter()
    private val habitsP = HabitsPresenter()
    private val tasksP = PlannerPresenter()

    private val next = WorkoutCardUi.Next("Full body A", "5 exercises, about 45 min", "0 of 3 this week", true)

    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(WED, LocalTime.of(h, m))

    /** Builds the day. [done] lists what is finished: habit ids with an amount, routine ids and task ids. */
    private fun day(
        now: LocalDateTime,
        workout: WorkoutCardUi = next,
        routines: List<com.homeworkoutcoach.engine.Routine> = emptyList(),
        routinesDone: Set<String> = emptySet(),
        habits: List<com.homeworkoutcoach.app.data.StoredHabit> = emptyList(),
        habitAmounts: Map<String, Int> = emptyMap(),
        workoutDays: Set<LocalDate> = emptySet(),
        sourceDays: Map<AutoSource, Set<LocalDate>> = emptyMap(),
        tasks: List<com.homeworkoutcoach.engine.Task> = emptyList(),
        name: String = "Asha Rao",
    ) = TodayPresenter.present(
        name, now, workout,
        routinesP.present(routines, routinesDone.map { RoutineDone(it, WED) }.toSet(), WED, now.toLocalTime()),
        habitsP.present(habits, habitAmounts.mapValues { mapOf(WED to it.value) }, workoutDays, WED, sourceDays),
        tasksP.present(tasks, WED, WED),
    )

    @Test
    fun theGreetingFollowsThePartOfTheDayAndUsesTheFirstName() {
        assertEquals("Good morning, Asha", day(at(7)).greeting)
        assertEquals("Good afternoon, Asha", day(at(13)).greeting)
        assertEquals("Good evening, Asha", day(at(20)).greeting)
        assertEquals("Good evening, Asha", day(at(2)).greeting) // the small hours still belong to the evening
        assertEquals("Good morning", day(at(7), name = "  ").greeting)
        assertEquals("Wednesday 28 October", day(at(7)).date)
    }

    @Test
    fun theSummaryCountsTheWorkoutRoutinesHabitsAndTasksButNotTheirMirrors() {
        val morning = routine("m", "Morning", DayPart.MORNING, steps = 3)
        val habits = listOf(
            storedHabit("water", kind = HabitKind.COUNT, target = 8),
            storedHabit("gym", auto = AutoSource.WORKOUT), // mirrors the workout: not counted again
            storedHabit("mr", auto = AutoSource.MORNING_ROUTINE), // mirrors the Morning routine
        )
        val tasks = listOf(task("a", done = true), task("b"))
        val ui = day(at(7, 30), routines = listOf(morning), habits = habits, tasks = tasks)
        // workout 1 + routine 1 + water 1 + two tasks = 5; one task is done
        assertEquals("1 of 5 done", ui.summary)
        assertEquals(1 to 5, ui.done to ui.total)
        assertEquals(listOf("water", "gym", "mr"), ui.habits.map { it.id }) // all still shown
        assertEquals(listOf("m"), ui.routines.map { it.id })
    }

    @Test
    fun theMorningRoutineHabitCountsOnItsOwnOnceTheMorningRoutineIsNotShown() {
        val morning = routine("m", "Morning", DayPart.MORNING)
        val mr = storedHabit("mr", auto = AutoSource.MORNING_ROUTINE)
        val afternoon = day(at(14), routines = listOf(morning), habits = listOf(mr), workout = WorkoutCardUi.Rest("A", "Fri", true))
        assertTrue(afternoon.routines.isEmpty())
        assertEquals("0 of 1 done", afternoon.summary)
        val noRoutine = day(at(7), habits = listOf(mr), workout = WorkoutCardUi.NoPlan)
        assertEquals("0 of 1 done", noRoutine.summary)
    }

    @Test
    fun everythingFinishedIsAllDone() {
        val morning = routine("m", "Morning", DayPart.MORNING)
        val ui = day(
            at(9),
            workout = WorkoutCardUi.Done("Full body A"),
            routines = listOf(morning), routinesDone = setOf("m"),
            habits = listOf(storedHabit("journal")), habitAmounts = mapOf("journal" to 1),
            tasks = listOf(task("a", done = true)),
        )
        assertEquals("All done for today.", ui.summary)
    }

    @Test
    fun aDayWithNothingInItSaysSo() {
        assertEquals(0, day(at(9), workout = WorkoutCardUi.NoPlan).total)
        assertEquals("Nothing planned today.", day(at(9), workout = WorkoutCardUi.NoPlan).summary)
        // a rest day is not something to do
        assertEquals("Nothing planned today.", day(at(9), workout = WorkoutCardUi.Rest("A", "Fri", true)).summary)
    }

    @Test
    fun aRoutineWithNoStepsIsShownButNotCounted() {
        val ui = day(at(7), workout = WorkoutCardUi.NoPlan, routines = listOf(routine("m", "Morning", DayPart.MORNING, steps = 0)))
        assertEquals(1, ui.routines.size)
        assertEquals("Nothing planned today.", ui.summary)
    }

    @Test
    fun theWorkoutHabitThatTickedItselfCountsViaTheWorkoutCard() {
        val gym = storedHabit("gym", auto = AutoSource.WORKOUT)
        val ui = day(at(18), workout = WorkoutCardUi.Done("Full body A"), habits = listOf(gym), workoutDays = setOf(WED))
        assertTrue(ui.habits.single().done)
        assertEquals("All done for today.", ui.summary) // 1 of 1: the workout, not twice
    }

    @Test
    fun anEmptyHabitListInvitesAddingOne() {
        assertFalse(day(at(9)).hasHabits)
        assertTrue(day(at(9), habits = listOf(storedHabit("a"))).hasHabits)
        // a habit not due today still means the user has habits
        val sunday = storedHabit("s", schedule = com.homeworkoutcoach.engine.HabitSchedule.Weekdays(setOf(java.time.DayOfWeek.SUNDAY)))
        val ui = day(at(9), habits = listOf(sunday))
        assertTrue(ui.hasHabits)
        assertTrue(ui.habits.isEmpty())
    }

    @Test
    fun theJournalLineIsAQuietExtraNotPartOfTheCount() {
        val habit = storedHabit("j", name = "Journal", auto = AutoSource.JOURNAL)
        val none = day(at(21), workout = WorkoutCardUi.NoPlan)
        assertFalse(none.journalWritten)
        assertEquals("Nothing planned today.", none.summary)
        val written = TodayPresenter.present(
            "Asha", at(21), WorkoutCardUi.NoPlan,
            routinesP.present(emptyList(), emptySet(), WED, LocalTime.of(21, 0)),
            habitsP.present(listOf(habit), emptyMap(), emptySet(), WED, mapOf(AutoSource.JOURNAL to setOf(WED))),
            tasksP.present(emptyList(), WED, WED),
            journalWritten = true,
        )
        assertTrue(written.journalWritten)
        assertTrue(written.habits.single().done) // the Journal habit ticked itself
        assertEquals("All done for today.", written.summary) // counted once, as the habit
    }

    @Test
    fun theTasksComeThroughWithWhatWasCarriedOver() {
        val ui = day(at(9), tasks = listOf(task("t"), task("old", date = WED.minusDays(2))))
        assertEquals(listOf("t"), ui.tasks.tasks.map { it.id })
        assertEquals(listOf("old"), ui.tasks.carriedOver.map { it.id })
        assertEquals("0 of 2 done", ui.summary) // workout + today's task; the carried one is not counted
    }
}
