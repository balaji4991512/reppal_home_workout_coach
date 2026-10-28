package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.data.goal
import com.homeworkoutcoach.app.ui.goals.GoalDraft
import com.homeworkoutcoach.app.ui.goals.GoalFormat
import com.homeworkoutcoach.app.ui.goals.GoalInputs
import com.homeworkoutcoach.app.ui.goals.GoalsPresenter
import com.homeworkoutcoach.engine.EndPreset
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.GoalState
import com.homeworkoutcoach.engine.Goals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY = LocalDate.of(2026, 10, 28) // a Wednesday

class GoalsPresenterTest {
    private val presenter = GoalsPresenter()

    private fun day(n: Int) = TODAY.plusDays(n.toLong())

    @Test
    fun formattingNumbersAndDays() {
        assertEquals("12", GoalFormat.number(12.0))
        assertEquals("84.5", GoalFormat.number(84.5))
        assertEquals("Last day", GoalFormat.daysLeft(0))
        assertEquals("1 day left", GoalFormat.daysLeft(1))
        assertEquals("18 days left", GoalFormat.daysLeft(18))
    }

    @Test
    fun anActiveWorkoutGoalShowsProgressDaysLeftAndPace() {
        val g = goal("w", "Twelve workouts", GoalKind.WORKOUTS, 12.0, start = day(-10), end = day(19)) // 30 days
        val dates = listOf(-9, -7, -5, -3, -1, 0).map(::day)
        val ui = presenter.present(listOf(g), emptyMap(), dates, emptyMap(), emptyList(), TODAY)
        val row = ui.active.single()
        assertEquals("Twelve workouts", row.title)
        assertEquals("6 of 12 workouts", row.detail)
        assertEquals(0.5f, row.fraction, 1e-6f)
        assertEquals("19 days left, ahead of the pace", row.status) // 6 of 12 at day 11 of 30
        assertEquals("1 active", ui.summary)
    }

    @Test
    fun aHabitGoalNamesItsHabitOrSaysItWasRemoved() {
        val g = goal("h", "Read", GoalKind.HABIT, 10.0, start = day(-5), end = day(24))
        val done = mapOf("read" to setOf(day(-4), day(-2), day(0)))
        assertEquals("3 of 10 days · Reading", presenter.present(listOf(g), mapOf("read" to "Reading"), emptyList(), done, emptyList(), TODAY).active.single().detail)
        assertEquals("3 of 10 days · habit removed", presenter.present(listOf(g), emptyMap(), emptyList(), done, emptyList(), TODAY).active.single().detail)
    }

    @Test
    fun aWeightGoalShowsTheLatestWeight() {
        val g = goal("kg", kind = GoalKind.WEIGHT, start = day(-3), end = day(40)) // from 86 to 80
        val ui = presenter.present(listOf(g), emptyMap(), emptyList(), emptyMap(), listOf(day(-1) to 84.5), TODAY)
        assertEquals("84.5 kg now, goal 80 kg", ui.active.single().detail)
        assertEquals(0.25f, ui.active.single().fraction, 1e-6f)
    }

    @Test
    fun reachedEndedAndUpcomingGoalsAreSeparated() {
        val reached = goal("r", "Done early", target = 2.0, start = day(-5), end = day(10))
        val ended = goal("e", "Missed", target = 10.0, start = day(-30), end = day(-2))
        val later = goal("u", "Later", target = 5.0, start = day(3), end = day(20))
        val dates = listOf(day(-4), day(-3), day(-20), day(-10))
        val ui = presenter.present(listOf(ended, reached, later), emptyMap(), dates, emptyMap(), emptyList(), TODAY)
        assertEquals(listOf("Done early"), ui.reached.map { it.title })
        assertEquals("Reached", ui.reached.single().status)
        assertEquals("Ended with 4 of 10", ui.ended.single().status)
        assertEquals("Starts Sat 31 Oct", ui.active.single().status)
        assertEquals(GoalState.UPCOMING, ui.active.single().state)
    }

    @Test
    fun theSummaryReadsGently() {
        assertEquals("Pick something bigger to work towards.", presenter.present(emptyList(), emptyMap(), emptyList(), emptyMap(), emptyList(), TODAY).summary)
        val ended = goal("e", target = 10.0, start = day(-30), end = day(-2))
        assertEquals("Nothing active right now.", presenter.present(listOf(ended), emptyMap(), emptyList(), emptyMap(), emptyList(), TODAY).summary)
    }

    @Test
    fun theFirstWeighInOfEachDayIsUsed() {
        fun at(day: LocalDate, h: Int, kg: Double) = BodyWeight(1, day.atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), kg, WeightSource.SCALE)
        val points = GoalInputs.firstWeighInPerDay(
            listOf(at(TODAY, 18, 84.9), at(TODAY, 7, 84.1), at(TODAY.minusDays(1), 8, 85.0)), ZoneOffset.UTC,
        )
        assertEquals(listOf(TODAY.minusDays(1) to 85.0, TODAY to 84.1), points)
    }
}

class GoalDraftTest {
    private val draft = GoalDraft.new(TODAY)

    @Test
    fun aNewDraftEndsIn30DaysAndNeedsATarget() {
        assertEquals(LocalDate.of(2026, 11, 26), draft.endDate)
        assertEquals("Enter a whole number from 1 to 999.", draft.problem(null, null, TODAY))
    }

    @Test
    fun theNameFollowsTheTargetUntilItIsTyped() {
        val d = draft.copy(targetText = "20")
        assertEquals("Finish 20 workouts", d.effectiveTitle(null))
        assertEquals("Finish 25 workouts", d.copy(targetText = "25").effectiveTitle(null))
        val typed = d.copy(title = "Get strong", titleEdited = true)
        assertEquals("Get strong", typed.copy(targetText = "30").effectiveTitle(null))
        assertNull(d.problem(null, null, TODAY))
    }

    @Test
    fun aHabitGoalNeedsAHabit() {
        val d = draft.copy(kind = GoalKind.HABIT, targetText = "15")
        assertEquals("Pick a habit.", d.problem(null, null, TODAY))
        val ok = d.copy(habitId = "read")
        assertNull(ok.problem("Reading", null, TODAY))
        assertEquals("Reading on 15 days", ok.effectiveTitle("Reading"))
    }

    @Test
    fun aWeightGoalStartsFromTheLatestWeighInOrWhatWasTyped() {
        val d = draft.copy(kind = GoalKind.WEIGHT, targetText = "80")
        assertEquals("Weigh in first, or type your weight now.", d.problem(null, null, TODAY))
        assertNull(d.problem(null, 84.5, TODAY))
        assertEquals(84.5, d.fromWeight(84.5)!!, 0.0)
        assertEquals(90.0, d.copy(weightNowText = "90").fromWeight(84.5)!!, 0.0) // typed wins over the weigh-in
        assertEquals("Choose a weight different from where you are now.", d.problem(null, 80.0, TODAY))
        val g = d.toGoal("id", null, 84.5, TODAY)
        assertEquals(84.5, g.startWeightKg!!, 0.0)
        assertEquals("Reach 80 kg", g.title)
        assertNull(d.copy(kind = GoalKind.WORKOUTS, targetText = "5").toGoal("id", null, 84.5, TODAY).startWeightKg)
    }

    @Test
    fun aCommaWorksAsADecimalPoint() {
        assertEquals(79.5, draft.copy(targetText = "79,5").target!!, 0.0)
    }

    @Test
    fun editingKeepsTheIdStartAndStartingWeight() {
        val original = goal("kg", "My goal", GoalKind.WEIGHT, start = TODAY.minusDays(10), end = TODAY.plusDays(20))
        val d = GoalDraft.from(original)
        assertEquals(original, d.toGoal("ignored", null, 70.0, TODAY)) // 70.0 is ignored: the starting weight was kept
        val moved = d.copy(endDate = TODAY.plusDays(50)).toGoal("ignored", null, 70.0, TODAY)
        assertEquals(TODAY.minusDays(10), moved.startDate)
        assertEquals(86.0, moved.startWeightKg!!, 0.0)
    }

    @Test
    fun theEndDayMustNotBeBeforeTheStart() {
        val d = draft.copy(targetText = "5", endDate = TODAY.minusDays(1))
        assertEquals("Pick an end day that is not before the start.", d.problem(null, null, TODAY))
        assertEquals(Goals.endFor(EndPreset.THIS_WEEK, TODAY), draft.copy(endDate = Goals.endFor(EndPreset.THIS_WEEK, TODAY)).endDate)
        assertTrue(d.copy(endDate = TODAY).problem(null, null, TODAY) == null)
    }
}
