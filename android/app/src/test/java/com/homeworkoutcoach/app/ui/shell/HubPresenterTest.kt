package com.homeworkoutcoach.app.ui.shell

import com.homeworkoutcoach.engine.CharacterInputs
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.FocusFact
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitFact
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitLog
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.RoutineItem
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.WorkoutFact
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live lines and the big numbers on the hubs. 2026-10-28 is a Wednesday. */
class HubPresenterTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val monday = LocalDate.of(2026, 10, 26)

    private fun habit(id: String, ticked: Boolean) =
        HabitFact(
            Habit(id, id, HabitKind.DONE, 1, HabitSchedule.Daily, today.minusDays(60)),
            HabitLog(if (ticked) mapOf(today to 1) else emptyMap()),
        )

    private fun routine(id: String) = Routine(id, id, DayPart.MORNING, listOf(RoutineItem("s", "step")))

    private fun hub(
        started: Boolean = true,
        planned: Int? = 3,
        workouts: List<WorkoutFact> = emptyList(),
        habits: List<HabitFact> = emptyList(),
        routines: List<Routine> = emptyList(),
        done: List<RoutineDone> = emptyList(),
        tasks: List<Task> = emptyList(),
        journal: Set<LocalDate> = emptySet(),
        focus: List<FocusFact> = emptyList(),
        goals: List<Goal> = emptyList(),
        weight: Pair<LocalDate, Double>? = null,
        focusQuality: String? = null,
    ) = HubInputs(
        started, Level.BEGINNER, 3,
        CharacterInputs(planned, workouts, habits, routines, done, tasks, journal, focus, emptySet()),
        goals, weight, focusQuality, today,
    )

    @Test
    fun trainCountsThisWeeksWorkoutsAndTheLastWeighIn() {
        val ui = HubPresenter.present(
            Tab.Train,
            hub(workouts = listOf(WorkoutFact(monday.minusDays(1)), WorkoutFact(monday), WorkoutFact(today)), weight = today.minusDays(1) to 83.6),
        )
        val hero = ui.hero!!
        assertEquals("2/3", hero.big)
        assertEquals("workouts this week", hero.label)
        assertEquals(2f / 3, hero.fraction!!, 0.001f)
        assertEquals("2 of 3 this week", ui.lines.getValue("week"))
        assertEquals("1 to go this week", hero.sub)
        assertEquals(2f / 3, ui.fractions.getValue("week"), 0.001f)
        assertEquals("83.6 kg, Tue 27 Oct", ui.lines.getValue("body"))
    }

    @Test
    fun trainBeforeThePlanIsSetUpInvitesSettingItUp() {
        val ui = HubPresenter.present(Tab.Train, hub(started = false, planned = null))
        assertEquals("Set up your plan to begin", ui.hero!!.label)
        assertNull(ui.hero!!.fraction)
        assertTrue(ui.lines.isEmpty())
    }

    @Test
    fun planCountsTodaysHabitsRoutinesTasksAndFocus() {
        val ui = HubPresenter.present(
            Tab.Plan,
            hub(
                habits = listOf(habit("a", true), habit("b", false), habit("c", true)),
                routines = listOf(routine("m"), routine("e")),
                done = listOf(RoutineDone("m", today), RoutineDone("e", today.minusDays(1))), // yesterday does not count
                tasks = listOf(Task("t1", "One", today, done = true), Task("t2", "Two", today), Task("t3", "Later", today.plusDays(1))),
                focus = listOf(FocusFact(today, 25), FocusFact(today, 15), FocusFact(today.minusDays(1), 60)),
            ),
        )
        assertEquals("2/3", ui.hero!!.big)
        assertEquals("habits today", ui.hero!!.label)
        assertEquals("2 of 3 done today", ui.lines.getValue("habits"))
        assertEquals("1 of 2 done today", ui.lines.getValue("routines"))
        assertEquals("1 of 2 done today", ui.lines.getValue("planner"))
        assertEquals("40 min today", ui.lines.getValue("focus"))
        assertEquals("1 to go today", ui.hero!!.sub)
        assertEquals(2f / 3, ui.fractions.getValue("habits"), 0.001f)
        assertEquals(0.5f, ui.fractions.getValue("routines"), 0.001f)
        assertEquals(0.5f, ui.fractions.getValue("planner"), 0.001f)
        assertNull(ui.fractions["focus"]) // minutes have no goal to fill
    }

    @Test
    fun anEmptyPlanTabInvitesAddingThings() {
        val ui = HubPresenter.present(Tab.Plan, hub())
        assertEquals("Add a habit to begin", ui.hero!!.label)
        assertEquals("Add your first habit", ui.lines.getValue("habits"))
        assertEquals("Add a routine", ui.lines.getValue("routines"))
        assertEquals("Nothing planned today", ui.lines.getValue("planner"))
        assertEquals("No session yet today", ui.lines.getValue("focus"))
    }

    @Test
    fun reflectShowsTheWeekGoalsJournalAndFocus() {
        val goal = { id: String, from: Int, to: Int -> Goal(id, id, GoalKind.WORKOUTS, 5.0, today.plusDays(from.toLong()), today.plusDays(to.toLong())) }
        val ui = HubPresenter.present(
            Tab.Reflect,
            hub(
                habits = listOf(habit("a", true)),
                goals = listOf(goal("run", -3, 10), goal("later", 5, 20), goal("old", -30, -5)),
                journal = setOf(today),
                focusQuality = "Consistency",
            ),
        )
        assertEquals("Written today", ui.lines.getValue("journal"))
        assertEquals("1 running", ui.lines.getValue("goals"))
        assertEquals("This week: consistency", ui.lines.getValue("character"))
        assertTrue(ui.hero!!.big.endsWith("%"))
        assertEquals("of habit days this week", ui.hero!!.label)
    }

    @Test
    fun reflectWithNothingYetSaysSo() {
        val ui = HubPresenter.present(Tab.Reflect, hub())
        assertEquals("–", ui.hero!!.big)
        assertEquals("Set your first goal", ui.lines.getValue("goals"))
        assertEquals("Not written yet today", ui.lines.getValue("journal"))
        assertTrue(ui.fractions.isEmpty())
        assertNull(ui.lines["character"])
        assertNull(ui.lines["progress"])
    }

    @Test
    fun aFinishedWeekAndDayAreSaidWarmly() {
        val done = hub(workouts = listOf(WorkoutFact(monday), WorkoutFact(today.minusDays(1)), WorkoutFact(today)), habits = listOf(habit("a", true)))
        assertEquals("Week complete. Well done.", HubPresenter.present(Tab.Train, done).hero!!.sub)
        assertEquals("All done today. Nice.", HubPresenter.present(Tab.Plan, done).hero!!.sub)
        assertEquals(1f, HubPresenter.present(Tab.Reflect, hub(journal = setOf(today))).fractions.getValue("journal"), 0f)
    }

    @Test
    fun meShowsThePlanInALine() {
        assertEquals("Beginner, 3 days a week", HubPresenter.present(Tab.Me, hub()).lines.getValue("setup"))
        assertTrue(HubPresenter.present(Tab.Me, hub(started = false)).lines.isEmpty())
        assertNull(HubPresenter.present(Tab.Today, hub()).hero)
    }
}
