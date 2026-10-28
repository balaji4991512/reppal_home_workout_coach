package com.homeworkoutcoach.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Character evidence from specs/JOURNAL_GOALS_FOCUS_CHARACTER_SPEC.md, section 4. 2026-10-26 is a Monday. */
class CharacterTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong()) // 0 = this Monday, -7 = last Monday

    private fun daily(id: String = "h", start: Int = -30) =
        Habit(id, id, HabitKind.DONE, 1, HabitSchedule.Daily, day(start))

    private fun habit(h: Habit, vararg doneDays: Int) = HabitFact(h, HabitLog(doneDays.associate { day(it) to 1 }))

    private fun inputs(
        planned: Int? = null,
        workouts: List<WorkoutFact> = emptyList(),
        habits: List<HabitFact> = emptyList(),
        routines: List<Routine> = emptyList(),
        routineDone: List<RoutineDone> = emptyList(),
        tasks: List<Task> = emptyList(),
        journal: Set<LocalDate> = emptySet(),
        focus: List<FocusFact> = emptyList(),
        activity: Set<LocalDate>? = null,
    ): CharacterInputs {
        val acts = activity ?: (workouts.map { it.date } + journal + focus.map { it.date }).toSet()
        return CharacterInputs(planned, workouts, habits, routines, routineDone, tasks, journal, focus, acts)
    }

    private fun w(n: Int, pain: Int = 0) = WorkoutFact(day(n), pain)

    private fun morning(vararg steps: String) = Routine("m", "Morning", DayPart.MORNING, steps.mapIndexed { i, s -> RoutineItem("s$i", s) })

    // ---- Week facts ----

    @Test
    fun aFinishedWeekCountsAllSevenDaysAndTheCurrentOneOnlyUpToToday() {
        val i = inputs()
        assertEquals(7, Character.facts(i, mon.minusDays(7), day(2)).daysCounted)
        assertEquals(3, Character.facts(i, mon, day(2)).daysCounted)
        assertEquals(7, Character.facts(i, mon, day(6)).daysCounted)
        assertEquals(0, Character.facts(i, mon.plusDays(7), day(2)).daysCounted) // a week that has not started
    }

    @Test
    fun workoutsHabitsRoutinesAndTasksAreCountedInTheWeek() {
        val h = daily()
        val i = inputs(
            planned = 3,
            workouts = listOf(w(-1), w(0), w(2)), // one the week before
            habits = listOf(habit(h, -2, 0, 1, 2)),
            routines = listOf(morning("a", "b")),
            routineDone = listOf(RoutineDone("m", day(0)), RoutineDone("m", day(1)), RoutineDone("m", day(-3))),
            tasks = listOf(
                Task("t1", "A", day(0), done = true), Task("t2", "B", day(1)), Task("t3", "C", day(2), done = true),
                Task("t4", "D", day(5)), // later this week: not counted yet
            ),
        )
        val f = Character.facts(i, mon, day(2))
        assertEquals(2, f.workoutsDone)
        assertEquals(3, f.workoutsPlanned)
        assertEquals(3, f.habitDone)
        assertEquals(3, f.habitDue)
        assertEquals(2, f.routineDone)
        assertEquals(3, f.routineExpected)
        assertEquals(2, f.tasksDone)
        assertEquals(3, f.tasksPlanned)
    }

    @Test
    fun aRoutineWithNoStepsIsNotExpected() {
        val empty = Routine("e", "Empty", DayPart.MORNING)
        val f = Character.facts(inputs(routines = listOf(empty)), mon, day(2))
        assertEquals(0, f.routineExpected)
    }

    @Test
    fun aComebackIsAnActiveDayAfterTwoOrMoreQuietDays() {
        // active on Friday the 23rd, then nothing Sat, Sun, Mon, and active again on Tuesday the 27th
        val active = setOf(day(-3), day(1))
        val f = Character.facts(inputs(activity = active), mon, day(2))
        assertEquals(1, f.comebacks)
        assertTrue(f.hadBreak)
        // one quiet day in between is not a break
        val quick = setOf(day(-2), day(0), day(1), day(2))
        val g = Character.facts(inputs(activity = quick), mon, day(2))
        assertEquals(0, g.comebacks)
        assertFalse(g.hadBreak)
    }

    @Test
    fun aQuietStretchWithoutAComebackYetIsStillABreak() {
        val f = Character.facts(inputs(activity = setOf(day(0))), mon, day(3)) // Tue, Wed, Thu quiet
        assertEquals(0, f.comebacks)
        assertTrue(f.hadBreak)
    }

    @Test
    fun painStopsAndRestDaysAfterTraining() {
        val i = inputs(workouts = listOf(w(0, pain = 1), w(2), w(3, pain = 2)))
        val f = Character.facts(i, mon, day(5))
        assertEquals(3, f.painStops)
        // rest days that followed a training day: Tuesday (after Mon), Friday (after Thu); Wednesday trained, Saturday follows a rest day
        assertEquals(2, f.recoveryDays)
    }

    @Test
    fun focusAndJournalAreCounted() {
        val i = inputs(
            journal = setOf(day(0), day(2), day(-4)),
            focus = listOf(FocusFact(day(0), 25), FocusFact(day(1), 45), FocusFact(day(-2), 30)),
        )
        val f = Character.facts(i, mon, day(2))
        assertEquals(2, f.journalDays)
        assertEquals(2, f.focusSessions)
        assertEquals(70, f.focusMinutes)
        assertTrue(f.journalUsed && f.focusUsed)
        val never = Character.facts(inputs(), mon, day(2))
        assertFalse(never.journalUsed || never.focusUsed)
    }

    // ---- Scores, lines and trends ----

    @Test
    fun consistencyIsAnEvenAverageOfWhatThereIsToMeasure() {
        val h = daily()
        // finished week: 2 of 4 planned workouts (0.5) and 7 of 7 habit days (1.0)
        val f = Character.facts(inputs(planned = 4, workouts = listOf(w(-7), w(-5)), habits = listOf(habit(h, -7, -6, -5, -4, -3, -2, -1))), mon.minusDays(7), day(6))
        assertEquals(0.75, Character.score(Quality.CONSISTENCY, f)!!, 1e-9)
        assertEquals("Workouts: 2 of 4 planned. Habits: 7 of 7 days.", Character.line(Quality.CONSISTENCY, f))
        // with only habits it is just the habits
        val g = Character.facts(inputs(habits = listOf(habit(h, -7, -6))), mon.minusDays(7), day(6))
        assertEquals(2.0 / 7, Character.score(Quality.CONSISTENCY, g)!!, 1e-9)
    }

    @Test
    fun aWeekInProgressIsComparedByRateNotByTotal() {
        // 3 workouts planned a week; by Wednesday (3 days) one finished: expected 1.29 so far
        val f = Character.facts(inputs(planned = 3, workouts = listOf(w(0))), mon, day(2))
        assertEquals(1 / (3 * 3 / 7.0), Character.score(Quality.CONSISTENCY, f)!!, 1e-9)
        assertEquals("Workouts: 1 so far, 3 planned this week.", Character.line(Quality.CONSISTENCY, f))
    }

    @Test
    fun nothingToMeasureMeansNoScoreAndAHelpfulLine() {
        val f = Character.facts(inputs(), mon, day(2))
        assertNull(Character.score(Quality.CONSISTENCY, f))
        assertEquals("Add habits or a workout plan and this will fill in.", Character.line(Quality.CONSISTENCY, f))
        assertNull(Character.score(Quality.DISCIPLINE, f))
        assertEquals("Add a routine or some tasks and this will fill in.", Character.line(Quality.DISCIPLINE, f))
        assertNull(Character.score(Quality.FOCUS, f)) // never used: not a gap, just not started
        assertNull(Character.score(Quality.REFLECTION, f))
        assertEquals("No focus sessions yet.", Character.line(Quality.FOCUS, f))
        assertEquals("Nothing written yet.", Character.line(Quality.REFLECTION, f))
    }

    @Test
    fun disciplineFromRoutinesAndTasks() {
        val f = Character.facts(
            inputs(
                routines = listOf(morning("a")),
                routineDone = listOf(RoutineDone("m", day(0)), RoutineDone("m", day(1))),
                tasks = listOf(Task("a", "A", day(0), done = true), Task("b", "B", day(1))),
            ),
            mon, day(3),
        )
        // routines 2 of 4 days (0.5), tasks 1 of 2 (0.5)
        assertEquals(0.5, Character.score(Quality.DISCIPLINE, f)!!, 1e-9)
        assertEquals("Routines: 2 of 4 days. Tasks: 1 of 2 done.", Character.line(Quality.DISCIPLINE, f))
    }

    @Test
    fun comebackLinesAreKindWhetherOrNotThereWasABreak() {
        val steady = Character.facts(inputs(activity = (0..3).map(::day).toSet()), mon, day(3))
        assertEquals("No comeback needed: you kept going.", Character.line(Quality.COMEBACK, steady))
        assertNull(Character.score(Quality.COMEBACK, steady))
        val back = Character.facts(inputs(activity = setOf(day(-3), day(1))), mon, day(2))
        assertEquals("You came back after a quiet stretch 1 time.", Character.line(Quality.COMEBACK, back))
        assertEquals(1.0, Character.score(Quality.COMEBACK, back)!!, 0.0)
        val still = Character.facts(inputs(activity = setOf(day(0))), mon, day(3))
        assertEquals(0.0, Character.score(Quality.COMEBACK, still)!!, 0.0)
        assertEquals("A quiet stretch, and a fresh start is always one small thing away.", Character.line(Quality.COMEBACK, still))
    }

    @Test
    fun listeningLines() {
        val f = Character.facts(inputs(workouts = listOf(w(0, pain = 1), w(2))), mon, day(3))
        assertEquals("Stopped for pain 1 time. 2 rest days after training.", Character.line(Quality.LISTENING, f))
        assertEquals(1.0, Character.score(Quality.LISTENING, f)!!, 0.0)
        val none = Character.facts(inputs(), mon, day(3))
        assertEquals("Nothing to count yet.", Character.line(Quality.LISTENING, none))
        assertNull(Character.score(Quality.LISTENING, none))
    }

    @Test
    fun focusAndReflectionLines() {
        val i = inputs(journal = setOf(day(0), day(2)), focus = listOf(FocusFact(day(0), 25), FocusFact(day(1), 50)))
        val f = Character.facts(i, mon, day(3))
        assertEquals("2 sessions, 1 h 15 min.", Character.line(Quality.FOCUS, f))
        assertEquals("Journal written on 2 of 4 days.", Character.line(Quality.REFLECTION, f))
        assertEquals(0.5, Character.score(Quality.REFLECTION, f)!!, 1e-9)
    }

    @Test
    fun trendsComparedWithLastWeekAndNoneWhereItMakesNoSense() {
        val h = daily()
        // last week 7 of 7, this week (3 days) 1 of 3: quieter
        val quiet = Character.week(inputs(habits = listOf(habit(h, -7, -6, -5, -4, -3, -2, -1, 0))), day(2))
        assertEquals(Trend.QUIETER, quiet.evidence.first { it.quality == Quality.CONSISTENCY }.trend)
        // up
        val up = Character.week(inputs(habits = listOf(habit(h, -7, 0, 1, 2))), day(2))
        assertEquals(Trend.UP, up.evidence.first { it.quality == Quality.CONSISTENCY }.trend)
        // about the same
        val same = Character.week(inputs(habits = listOf(habit(h, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2))), day(2))
        assertEquals(Trend.SAME, same.evidence.first { it.quality == Quality.CONSISTENCY }.trend)
        // comeback and listening have no trend, and nothing to compare means none
        assertNull(quiet.evidence.first { it.quality == Quality.COMEBACK }.trend)
        assertNull(quiet.evidence.first { it.quality == Quality.LISTENING }.trend)
        assertNull(Character.week(inputs(), day(2)).evidence.first { it.quality == Quality.CONSISTENCY }.trend)
        assertEquals(Quality.entries, quiet.evidence.map { it.quality })
    }

    // ---- The suggested focus ----

    @Test
    fun theSuggestedFocusIsTheQuietestQualityLastWeek() {
        val h = daily()
        // last week: habits all done (consistency 1.0), journal used but written 1 of 7 days (reflection 0.14)
        val i = inputs(habits = listOf(habit(h, -7, -6, -5, -4, -3, -2, -1)), journal = setOf(day(-5)))
        assertEquals(Quality.REFLECTION, Character.week(i, day(2)).suggestedFocus)
    }

    @Test
    fun withNothingToGoOnTheSuggestionIsConsistency() {
        assertEquals(Quality.CONSISTENCY, Character.week(inputs(), day(2)).suggestedFocus)
        assertEquals(Quality.CONSISTENCY, Character.autoFocus(Character.facts(inputs(), mon.minusDays(7), day(2))))
    }

    @Test
    fun aComebackOrARestDayIsNeverTheSuggestedFocus() {
        // a quiet stretch and no comeback scores 0 for Comeback, yet it is not what is suggested
        val i = inputs(habits = listOf(habit(daily(), -7, -6, -5, -4, -3, -2, -1)), activity = setOf(day(-7)))
        val week = Character.week(i, day(2))
        assertEquals(0.0, Character.score(Quality.COMEBACK, week.lastWeek)!!, 0.0)
        assertEquals(Quality.CONSISTENCY, week.suggestedFocus)
    }

    @Test
    fun tiesFallToTheFirstInTheList() {
        val h = daily()
        // consistency 0.0 and discipline 0.0 both: consistency comes first
        val i = inputs(
            habits = listOf(habit(h)),
            routines = listOf(morning("a")),
        )
        assertEquals(Quality.CONSISTENCY, Character.week(i, day(2)).suggestedFocus)
    }

    // ---- The summary in plain words ----

    @Test
    fun theTemplateCoachOnlyWordsWhatTheNumbersSay() {
        val h = daily()
        val i = inputs(
            planned = 3,
            workouts = listOf(w(0), w(2, pain = 1)),
            habits = listOf(habit(h, 0, 1, 2)),
            activity = setOf(day(-3), day(0), day(1), day(2)),
            tasks = listOf(Task("a", "A", day(1), done = true)),
        )
        val week = Character.week(i, day(2))
        val text = TemplateCoach.weeklySummary(week, Quality.REFLECTION)
        assertTrue(text, text.startsWith("This week so far you finished 2 workouts, kept up 3 habit days and got 1 task done."))
        assertTrue(text, text.contains("You came back after a quiet stretch 1 time, and that counts."))
        assertTrue(text, text.contains("You stopped when something hurt, which is the right call."))
        assertTrue(text, text.endsWith("This week's focus is reflection: write two lines in your journal each evening."))
    }

    @Test
    fun anEmptyWeekGetsAGentleLine() {
        val week = Character.week(inputs(), day(1))
        val text = TemplateCoach.weeklySummary(week, Quality.CONSISTENCY)
        assertTrue(text, text.startsWith("This week is just getting started."))
        assertTrue(text, text.endsWith("pick one habit and do it every day, even the smallest version."))
        assertEquals("A new week. Pick one small thing to start with.", TemplateCoach.weeklySummary(Character.week(inputs(), day(1)).copy(facts = Character.facts(inputs(), mon.plusDays(7), day(1))), Quality.FOCUS))
    }

    // ---- Weekly reflection ----

    @Test
    fun theReflectionNeedsAnAnswerAndHasALimit() {
        assertEquals("Write at least one answer.", Reflections.problem("", " "))
        assertNull(Reflections.problem("Trained three times", ""))
        assertNull(Reflections.problem("a".repeat(300), "b".repeat(300)))
        assertEquals("Keep each answer to 300 characters.", Reflections.problem("a".repeat(301), ""))
        assertEquals(WeeklyReflection(mon, "Good", "Sleep"), Reflections.create(mon, " Good ", " Sleep\n"))
        assertThrows(IllegalArgumentException::class.java) { WeeklyReflection(mon, " ", "") }
        assertEquals("What went well this week?", Reflections.WENT_WELL)
        assertEquals("What will you do differently?", Reflections.DIFFERENTLY)
    }
}
