package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.SetupDraft
import com.homeworkoutcoach.app.ui.week.ArrangePresenter
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.app.ui.week.WeekdayUi
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Split
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeTest {
    private val lib = Library.default
    private val presenter = ArrangePresenter(lib)
    private val bodyPart = UserSettings(
        started = true, bench = Bench.ADJUSTABLE, dumbbellsKg = listOf(5.0, 8.0, 10.0), daysPerWeek = 4, split = Split.BODY_PART,
    )

    @Test
    fun startsFromThePlansOwnDays() {
        val draft = presenter.initial(bodyPart, emptyMap())
        assertEquals(listOf(0, 1, 3, 4), draft)
        val ui = presenter.present(bodyPart, emptyMap(), draft, null)
        assertEquals("Chest and triceps", ui.rows[0].title)
        assertNull(ui.rows[2].title)
        assertTrue(ui.ok && ui.canSave && ui.isDefault)
    }

    @Test
    fun movingLegsToMondaySwapsWithChest() {
        val draft = presenter.move(listOf(0, 1, 3, 4), session = 2, weekday = 0)
        assertEquals(listOf(3, 1, 0, 4), draft)
        val ui = presenter.present(bodyPart, emptyMap(), draft, null)
        assertEquals("Legs", ui.rows[0].title)
        assertEquals("Chest and triceps", ui.rows[3].title)
        assertTrue(ui.canSave)
        assertFalse(ui.isDefault)
    }

    @Test
    fun movingToARestDayLeavesTheOldDayFree() {
        val draft = presenter.move(listOf(0, 1, 3, 4), session = 3, weekday = 6)
        assertEquals(listOf(0, 1, 3, 6), draft)
    }

    @Test
    fun backToBackFullBodyIsBlocked() {
        val s = bodyPart.copy(daysPerWeek = 3, split = Split.FULL_BODY)
        val ui = presenter.present(s, emptyMap(), listOf(0, 1, 4), null)
        assertFalse(ui.canSave)
        assertTrue(ui.problems.single().endsWith(": Monday and Tuesday"))
    }

    @Test
    fun savedLayoutDrivesTheWeekAndSurvivesUnchangedSetup() = runTest {
        val settings = InMemorySettingsRepository(bodyPart)
        val monday = java.time.LocalDate.of(2026, 10, 26)
        val actions = PlanActions(settings, InMemoryTrainingRepository(), KeywordConcernMapper(lib), today = { monday })
        actions.arrangeWeek(listOf(3, 1, 0, 4))
        val s = settings.settings.first()
        val week = WeekPresenter(lib, Planner(lib)).present(s, emptyMap(), emptyMap(), emptyList(), monday, java.time.ZoneOffset.UTC)
        assertEquals("Legs", (week.days[0] as WeekdayUi.Training).name)

        // Rebuilding the same plan keeps the layout; changing the days a week drops it.
        val draft = SetupDraft.from(s, Planner.DEFAULT_CUSTOM)
        actions.buildPlan(draft)
        assertEquals(listOf(3, 1, 0, 4), settings.settings.first().sessionWeekdays)
        actions.buildPlan(draft.copy(daysPerWeek = 3))
        assertNull(settings.settings.first().sessionWeekdays)
    }
}
