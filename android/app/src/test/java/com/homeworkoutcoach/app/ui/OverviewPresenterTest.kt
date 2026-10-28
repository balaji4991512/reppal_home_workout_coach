package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.ui.progress.OverviewPresenter
import com.homeworkoutcoach.engine.CharacterInputs
import com.homeworkoutcoach.engine.FocusFact
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitFact
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitLog
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.OverviewRange
import com.homeworkoutcoach.engine.Overviews
import com.homeworkoutcoach.engine.WorkoutFact
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Progress overview in words. 2026-10-26 is a Monday. */
class OverviewPresenterTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong())

    private fun ui(range: OverviewRange, doneDays: List<Int>, today: Int = 2, focus: List<FocusFact> = emptyList(), workouts: List<WorkoutFact> = emptyList(), habits: Boolean = true): com.homeworkoutcoach.app.ui.progress.OverviewUi {
        val h = Habit("h", "Habit", HabitKind.DONE, 1, HabitSchedule.Daily, day(-400))
        val facts = if (habits) listOf(HabitFact(h, HabitLog(doneDays.associate { day(it) to 1 }))) else emptyList()
        val inputs = CharacterInputs(null, workouts, facts, emptyList(), emptyList(), emptyList(), emptySet(), focus, emptySet())
        return OverviewPresenter.present(Overviews.compute(inputs, range, day(today)), day(today))
    }

    @Test
    fun theWeekIsLabelledByDayInitialsAndTodaysBarIsMarked() {
        val u = ui(OverviewRange.WEEK, listOf(0, 1, 2))
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), u.bars.map { it.label })
        assertEquals(listOf(false, false, true, false, false, false, false), u.bars.map { it.current })
        assertEquals("Habit days done, by day", u.barsTitle)
    }

    @Test
    fun weeklyBarsAreLabelledWithTheirFirstDay() {
        val u = ui(OverviewRange.MONTH, emptyList())
        assertEquals(listOf("5/10", "12/10", "19/10", "26/10"), u.bars.map { it.label })
        assertEquals(listOf(false, false, false, true), u.bars.map { it.current })
        assertEquals("Habit days done, by week", u.barsTitle)
    }

    @Test
    fun theYearIsLabelledByMonthInitial() {
        val u = ui(OverviewRange.YEAR, emptyList())
        assertEquals(listOf("N", "D", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O"), u.bars.map { it.label })
        assertTrue(u.bars.last().current)
    }

    @Test
    fun theRingShowsTheShareOfHabitDaysDone() {
        val u = ui(OverviewRange.WEEK, listOf(0, 2)) // done Monday and Wednesday (today); Tuesday missed
        assertEquals(66, u.percent)
        assertEquals(0.66f, u.fraction, 0.001f)
        assertEquals(2, u.habitsDone)
        assertEquals("out of 3 due so far", u.habitsOutOf)
        assertTrue(u.hasHabits)
    }

    @Test
    fun noHabitsMeansNothingToShow() {
        val u = ui(OverviewRange.WEEK, emptyList(), habits = false)
        assertFalse(u.hasHabits)
        assertEquals(0, u.percent)
        assertNull(u.trend)
    }

    @Test
    fun theTrendIsSaidInPlainWordsForTheSpanBefore() {
        val up = ui(OverviewRange.WEEK, listOf(-7, 0, 1, 2))
        assertEquals("up from last week", up.trend)
        val month = ui(OverviewRange.MONTH, (-14..2).toList() + (-30..-15).filter { it % 2 == 0 })
        assertEquals("up from the 4 weeks before", month.trend)
        val same = ui(OverviewRange.WEEK, (-7..2).toList())
        assertEquals("about the same as last week", same.trend)
        val quieter = ui(OverviewRange.WEEK, (-7..-1).toList() + listOf(0))
        assertEquals("quieter than last week", quieter.trend)
    }

    @Test
    fun workoutsAndFocusShowWhatCameBefore() {
        val u = ui(
            OverviewRange.WEEK, emptyList(),
            workouts = listOf(WorkoutFact(day(0)), WorkoutFact(day(-2)), WorkoutFact(day(-3))),
            focus = listOf(FocusFact(day(1), 80), FocusFact(day(-2), 25)),
        )
        assertEquals(1, u.workouts)
        assertEquals("2 before", u.workoutsBefore)
        assertEquals("1 h 20 min", u.focusTime)
        assertEquals("25 min before", u.focusBefore)
        assertEquals("none before", ui(OverviewRange.WEEK, emptyList()).focusBefore)
    }

    @Test
    fun minutesAreWrittenAsHoursAndMinutes() {
        assertEquals("None yet", OverviewPresenter.minutes(0))
        assertEquals("45 min", OverviewPresenter.minutes(45))
        assertEquals("2 h", OverviewPresenter.minutes(120))
        assertEquals("1 h 5 min", OverviewPresenter.minutes(65))
    }
}
