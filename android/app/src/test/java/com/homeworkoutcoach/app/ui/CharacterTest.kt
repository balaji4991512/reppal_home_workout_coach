package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.CharacterFocus
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.storedHabit
import com.homeworkoutcoach.app.ui.character.CharacterInputsBuilder
import com.homeworkoutcoach.app.ui.character.CharacterPresenter
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Character
import com.homeworkoutcoach.engine.CoachText
import com.homeworkoutcoach.engine.CharacterWeek
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.Quality
import com.homeworkoutcoach.engine.WeeklyReflection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val WED = LocalDate.of(2026, 10, 28) // a Wednesday
private val MON = LocalDate.of(2026, 10, 26)
private val ZONE = ZoneOffset.UTC

private fun at(day: LocalDate, hour: Int = 9) = day.atTime(hour, 0).toInstant(ZONE).toEpochMilli()

class CharacterInputsBuilderTest {
    private fun build(
        workouts: List<Workout> = emptyList(),
        habits: List<com.homeworkoutcoach.app.data.StoredHabit> = emptyList(),
        entries: Map<String, Map<LocalDate, Int>> = emptyMap(),
        journal: List<JournalEntry> = emptyList(),
        focus: List<FocusSession> = emptyList(),
        planned: Int? = 3,
    ) = CharacterInputsBuilder.build(planned, workouts, habits, entries, emptyList(), emptyList(), emptyList(), journal, focus, WED, ZONE)

    private fun workout(day: LocalDate, pain: Set<String> = emptySet()) =
        Workout(dateMillis = at(day), label = "A", plan = emptyList(), sets = emptyMap(), painIds = pain)

    @Test
    fun workoutsKeepTheirPainStopsAndLocalDay() {
        val i = build(workouts = listOf(workout(MON, setOf("bench", "rdl")), workout(WED)))
        assertEquals(listOf(MON to 2, WED to 0), i.workouts.map { it.date to it.painStops })
        assertEquals(3, i.plannedWorkoutsPerWeek)
    }

    @Test
    fun archivedHabitsAreLeftOut() {
        val live = storedHabit("live")
        val old = storedHabit("old", archived = true)
        assertEquals(listOf("live"), build(habits = listOf(live, old)).habits.map { it.habit.id })
    }

    @Test
    fun everyKindOfActivityMakesAnActiveDay() {
        val gym = storedHabit("gym", auto = AutoSource.WORKOUT)
        val water = storedHabit("water")
        val i = build(
            workouts = listOf(workout(MON.minusDays(10))),
            habits = listOf(gym, water),
            entries = mapOf("water" to mapOf(MON.minusDays(2) to 1, MON.minusDays(1) to 0)), // a 0 is not activity
            journal = listOf(JournalEntry(MON.minusDays(6), "x")),
            focus = listOf(FocusSession("f", at(MON.minusDays(4)), 25, 25, true)),
        )
        assertEquals(
            setOf(MON.minusDays(10), MON.minusDays(6), MON.minusDays(4), MON.minusDays(2)),
            i.activityDays,
        )
    }

    @Test
    fun theWorkoutHabitCountsOnWorkoutDaysThroughItsLog() {
        val gym = storedHabit("gym", auto = AutoSource.WORKOUT)
        val i = build(workouts = listOf(workout(MON)), habits = listOf(gym))
        val week = Character.week(i, WED)
        assertEquals(1, week.facts.habitDone)
        assertEquals(1, week.facts.workoutsDone)
    }

    @Test
    fun focusSessionsCarryTheirActualMinutes() {
        val i = build(focus = listOf(FocusSession("f", at(MON), 45, 20, false)))
        assertEquals(listOf(MON to 20), i.focus.map { it.date to it.minutes })
    }
}

class CharacterPresenterTest {
    private val capturing = object : CoachText {
        var focus: Quality? = null
        override fun weeklySummary(week: CharacterWeek, focus: Quality): String {
            this.focus = focus
            return "SUMMARY for ${focus.label}"
        }
    }
    private val presenter = CharacterPresenter(capturing)
    private val week = Character.week(
        CharacterInputsBuilder.build(null, emptyList(), emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), WED, ZONE),
        WED,
    )

    @Test
    fun theWeekIsLabelledAndAllSixQualitiesAreShown() {
        val ui = presenter.present(week, null, emptyList())
        assertEquals("26 Oct to 1 Nov", ui.weekLabel)
        assertEquals(Quality.entries, ui.cards.map { it.quality })
        assertEquals(Quality.entries.map { it.label }, ui.cards.map { it.label })
    }

    @Test
    fun theSuggestionIsTheFocusUntilThePersonPicksAnother() {
        val ui = presenter.present(week, null, emptyList())
        assertEquals(week.suggestedFocus, ui.focus)
        assertEquals(listOf(true), ui.cards.filter { it.isFocus }.map { true })
        assertEquals("SUMMARY for ${week.suggestedFocus.label}", ui.summary)
        val picked = presenter.present(week, CharacterFocus(MON.toEpochDay(), Quality.REFLECTION.name), emptyList())
        assertEquals(Quality.REFLECTION, picked.focus)
        assertEquals(week.suggestedFocus, picked.suggested)
        assertEquals(Quality.REFLECTION, capturing.focus)
        assertTrue(picked.cards.single { it.isFocus }.quality == Quality.REFLECTION)
    }

    @Test
    fun aChoiceFromAnEarlierWeekIsForgotten() {
        val old = CharacterFocus(MON.minusDays(7).toEpochDay(), Quality.FOCUS.name)
        assertEquals(week.suggestedFocus, presenter.present(week, old, emptyList()).focus)
        // and so is one that names a quality that no longer exists
        val odd = CharacterFocus(MON.toEpochDay(), "KINDNESS")
        assertEquals(week.suggestedFocus, presenter.present(week, odd, emptyList()).focus)
    }

    @Test
    fun trendsAreWordedGently() {
        val habit = storedHabit("h")
        val i = CharacterInputsBuilder.build(
            null, emptyList(), listOf(habit), mapOf("h" to (0..9).associate { WED.minusDays(it.toLong() + 3) to 1 }), // last week done, this week not
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), WED, ZONE,
        )
        val ui = presenter.present(Character.week(i, WED), null, emptyList())
        val consistency = ui.cards.first { it.quality == Quality.CONSISTENCY }
        assertEquals("quieter than last week", consistency.trend)
        assertNull(ui.cards.first { it.quality == Quality.COMEBACK }.trend)
    }

    @Test
    fun theReflectionForThisWeekAndTheEarlierOnes() {
        val all = listOf(
            WeeklyReflection(MON, "Trained", "Sleep"),
            WeeklyReflection(MON.minusDays(7), "Walked", ""),
            WeeklyReflection(MON.minusDays(21), "", "Plan meals"),
            WeeklyReflection(MON.minusDays(14), "Read", "Less phone"),
        )
        val ui = presenter.present(week, null, all)
        assertTrue(ui.reflection.hasEntry)
        assertEquals("Trained", ui.reflection.wentWell)
        assertEquals("26 Oct to 1 Nov", ui.reflection.weekLabel)
        assertEquals(listOf("19 Oct to 25 Oct", "12 Oct to 18 Oct", "5 Oct to 11 Oct"), ui.earlier.map { it.weekLabel })
        assertEquals("Walked", ui.earlier.first().wentWell)
        val none = presenter.present(week, null, emptyList())
        assertFalse(none.reflection.hasEntry)
        assertEquals("", none.reflection.wentWell)
        assertTrue(none.earlier.isEmpty())
    }

    @Test
    fun atMostFourEarlierWeeksAreListed() {
        val many = (1..8).map { WeeklyReflection(MON.minusDays(7L * it), "w$it") }
        assertEquals(4, presenter.present(week, null, many).earlier.size)
    }
}
