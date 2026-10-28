package com.homeworkoutcoach.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Goal rules from specs/JOURNAL_GOALS_FOCUS_CHARACTER_SPEC.md, section 2. 2026-10-26 is a Monday. */
class GoalsTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong())

    /** A 10-day goal, Monday the 26th to Wednesday the 4th. */
    private fun workouts(target: Int = 5, start: LocalDate = day(0), end: LocalDate = day(9)) =
        Goal("g", "Five workouts", GoalKind.WORKOUTS, target.toDouble(), start, end)

    private fun habitGoal(target: Int = 5) = Goal("h", "Read", GoalKind.HABIT, target.toDouble(), day(0), day(9), habitId = "read")

    private fun weightGoal(start: Double = 86.0, target: Double = 80.0) =
        Goal("w", "Reach $target kg", GoalKind.WEIGHT, target, day(0), day(29), startWeightKg = start)

    private fun p(g: Goal, today: Int, workouts: List<Int> = emptyList(), habit: List<Int> = emptyList(), weights: List<Pair<Int, Double>> = emptyList()) =
        Goals.progress(g, workouts.map(::day), habit.map(::day), weights.map { day(it.first) to it.second }, day(today))

    // ---- Workouts and habit days ----

    @Test
    fun workoutsCountFromTheStartToTodayAndNoFurther() {
        val r = p(workouts(), today = 4, workouts = listOf(-3, 0, 2, 4, 6))
        assertEquals(3.0, r.current, 0.0) // the 26th, 28th and 30th; not before the start, not tomorrow
        assertEquals(0.6, r.fraction, 1e-9)
        assertEquals(GoalState.ACTIVE, r.state)
        assertEquals(5, r.daysLeft)
    }

    @Test
    fun twoWorkoutsOnOneDayBothCount() {
        assertEquals(2.0, p(workouts(), today = 1, workouts = listOf(0, 0)).current, 0.0)
    }

    @Test
    fun habitDaysCountOncePerDay() {
        val r = p(habitGoal(), today = 5, habit = listOf(0, 1, 1, 3, 12))
        assertEquals(3.0, r.current, 0.0)
        assertEquals(0.6, r.fraction, 1e-9)
    }

    // ---- State ----

    @Test
    fun reachingTheTargetKeepsTheGoalReachedEvenBeforeTheEndAndAfter() {
        val g = workouts(target = 2)
        assertEquals(GoalState.REACHED, p(g, today = 3, workouts = listOf(0, 1)).state)
        assertEquals(1.0, p(g, today = 3, workouts = listOf(0, 1, 2)).fraction, 0.0) // never above 1
        assertEquals(GoalState.REACHED, p(g, today = 40, workouts = listOf(0, 1)).state)
        assertEquals(0, p(g, today = 3, workouts = listOf(0, 1)).daysLeft)
    }

    @Test
    fun aGoalThatHasNotStartedYetIsUpcomingAndOneThatHasPassedIsEnded() {
        val g = workouts(start = day(3), end = day(9))
        assertEquals(GoalState.UPCOMING, p(g, today = 1).state)
        assertEquals(GoalState.ENDED, p(workouts(), today = 10, workouts = listOf(0, 1)).state)
        assertEquals(2.0, p(workouts(), today = 10, workouts = listOf(0, 1, 12)).current, 0.0) // after the end does not count
        assertNull(p(workouts(), today = 10).pace)
    }

    @Test
    fun theLastDayIsStillActiveWithNoDaysLeft() {
        val r = p(workouts(), today = 9, workouts = listOf(0))
        assertEquals(GoalState.ACTIVE, r.state)
        assertEquals(0, r.daysLeft)
    }

    // ---- Pace ----

    @Test
    fun paceIsComparedWithAnEvenSpreadOverTheDays() {
        val g = workouts(target = 10) // 10 days: by the end of day n you are expected to have n
        assertEquals(Pace.ON_TRACK, p(g, today = 0).pace) // first day, nothing yet
        assertEquals(Pace.ON_TRACK, p(g, today = 4, workouts = listOf(0, 1, 2)).pace) // 3 of 10 at day 5 (expected 5): 20 points short is the edge
        assertEquals(Pace.BEHIND, p(g, today = 4, workouts = listOf(0, 1)).pace)
        assertEquals(Pace.ON_TRACK, p(g, today = 4, workouts = listOf(0, 1, 2, 3, 4)).pace)
        assertEquals(Pace.AHEAD, p(g, today = 4, workouts = listOf(0, 0, 1, 1, 2, 2)).pace)
    }

    // ---- Weight ----

    @Test
    fun weightProgressIsHowFarFromWhereYouStartedToTheTarget() {
        val g = weightGoal(start = 86.0, target = 80.0)
        val r = p(g, today = 10, weights = listOf(2 to 85.0, 8 to 83.0))
        assertEquals(83.0, r.current, 0.0) // the latest
        assertEquals(0.5, r.fraction, 1e-9)
        assertEquals(GoalState.ACTIVE, r.state)
        assertEquals(19, r.daysLeft)
    }

    @Test
    fun withNoWeighInYetNothingHasChanged() {
        val r = p(weightGoal(), today = 3)
        assertEquals(86.0, r.current, 0.0)
        assertEquals(0.0, r.fraction, 0.0)
    }

    @Test
    fun aWeightGoalCanBeToGainAsWellAsToLose() {
        val g = weightGoal(start = 60.0, target = 64.0)
        assertEquals(0.5, p(g, today = 5, weights = listOf(4 to 62.0)).fraction, 1e-9)
        assertEquals(GoalState.REACHED, p(g, today = 5, weights = listOf(4 to 64.5)).state)
        assertEquals(0.0, p(g, today = 5, weights = listOf(4 to 59.0)).fraction, 0.0) // the wrong way is no progress, not negative
    }

    @Test
    fun aWeighInAfterTheEndDoesNotChangeAnEndedGoal() {
        val g = weightGoal(start = 86.0, target = 80.0).copy(endDate = day(9))
        val r = p(g, today = 20, weights = listOf(5 to 84.0, 15 to 79.0))
        assertEquals(84.0, r.current, 0.0)
        assertEquals(GoalState.ENDED, r.state)
        // but reaching it in time counts
        assertEquals(GoalState.REACHED, p(g, today = 20, weights = listOf(8 to 79.5)).state)
    }

    // ---- Quick choices, titles, checks ----

    @Test
    fun endDaysForTheQuickChoices() {
        assertEquals(day(6), Goals.endFor(EndPreset.THIS_WEEK, day(2))) // Wednesday to that Sunday
        assertEquals(day(6), Goals.endFor(EndPreset.THIS_WEEK, day(6))) // Sunday is its own week's end
        assertEquals(LocalDate.of(2026, 10, 31), Goals.endFor(EndPreset.THIS_MONTH, day(0)))
        assertEquals(LocalDate.of(2026, 11, 24), Goals.endFor(EndPreset.THIRTY_DAYS, day(0)))
    }

    @Test
    fun suggestedTitles() {
        assertEquals("Finish 20 workouts", Goals.suggestedTitle(GoalKind.WORKOUTS, 20.0, null))
        assertEquals("Finish 1 workout", Goals.suggestedTitle(GoalKind.WORKOUTS, 1.0, null))
        assertEquals("Reading on 15 days", Goals.suggestedTitle(GoalKind.HABIT, 15.0, "Reading"))
        assertEquals("Reach 80 kg", Goals.suggestedTitle(GoalKind.WEIGHT, 80.0, null))
        assertEquals("Reach 79.5 kg", Goals.suggestedTitle(GoalKind.WEIGHT, 79.5, null))
    }

    @Test
    fun whatIsTypedIsChecked() {
        fun check(
            title: String = "Goal", kind: GoalKind = GoalKind.WORKOUTS, target: Double? = 10.0, habit: String? = null,
            weight: Double? = null, start: LocalDate = day(0), end: LocalDate = day(9),
        ) = Goals.problem(title, kind, target, habit, weight, start, end)
        assertNull(check())
        assertEquals("Give the goal a name.", check(title = " "))
        assertEquals("Keep the name to 60 characters.", check(title = "x".repeat(61)))
        assertEquals("Enter a whole number from 1 to 999.", check(target = null))
        assertEquals("Enter a whole number from 1 to 999.", check(target = 0.0))
        assertEquals("Enter a whole number from 1 to 999.", check(target = 2.5))
        assertEquals("Enter a whole number from 1 to 999.", check(target = 1000.0))
        assertEquals("Pick a habit.", check(kind = GoalKind.HABIT))
        assertNull(check(kind = GoalKind.HABIT, habit = "read"))
        assertEquals("Enter a weight from 20 to 300 kg.", check(kind = GoalKind.WEIGHT, target = 10.0, weight = 80.0))
        assertEquals("Weigh in first, or type your weight now.", check(kind = GoalKind.WEIGHT, target = 75.0))
        assertEquals("Choose a weight different from where you are now.", check(kind = GoalKind.WEIGHT, target = 80.0, weight = 80.0))
        assertNull(check(kind = GoalKind.WEIGHT, target = 79.5, weight = 84.0))
        assertEquals("Pick an end day that is not before the start.", check(end = day(-1)))
    }

    @Test
    fun aGoalMustBeValid() {
        assertThrows(IllegalArgumentException::class.java) { Goal("g", " ", GoalKind.WORKOUTS, 5.0, day(0), day(1)) }
        assertThrows(IllegalArgumentException::class.java) { Goal("g", "x", GoalKind.WORKOUTS, 0.0, day(0), day(1)) }
        assertThrows(IllegalArgumentException::class.java) { Goal("g", "x", GoalKind.WORKOUTS, 5.0, day(2), day(1)) }
        assertThrows(IllegalArgumentException::class.java) { Goal("g", "x", GoalKind.HABIT, 5.0, day(0), day(1)) }
        assertThrows(IllegalArgumentException::class.java) { Goal("g", "x", GoalKind.WEIGHT, 80.0, day(0), day(1)) }
        assertThrows(IllegalArgumentException::class.java) { Goal("g", "x", GoalKind.WEIGHT, 80.0, day(0), day(1), startWeightKg = 80.0) }
    }
}
