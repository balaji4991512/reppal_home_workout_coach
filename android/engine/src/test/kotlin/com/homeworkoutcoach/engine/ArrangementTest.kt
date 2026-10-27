package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's own weekday arrangement and the back-to-back recovery check. */
class ArrangementTest {
    private val lib = Library.default
    private val planner = Planner(lib)
    private val kit = Equipment(Bench.ADJUSTABLE, lib.dumbbellStepsKg.toSet())
    private val bodyPart = PlanSettings(kit, Level.BEGINNER, daysPerWeek = 4, split = Split.BODY_PART)

    @Test
    fun defaultLayoutIsUnchanged() {
        assertEquals(Planner.BODY_PART, planner.weekSequence(bodyPart))
        assertEquals(listOf(0, 1, 3, 4), planner.weekdays(bodyPart))
        assertTrue(Recovery.clashes(lib, planner, bodyPart).isEmpty())
    }

    @Test
    fun legsCanMoveToMonday() {
        // default order: chest+triceps, back+biceps, legs, shoulders+abs. Legs (index 2) to Monday, chest to Thursday.
        val s = bodyPart.copy(sessionWeekdays = listOf(3, 1, 0, 4))
        assertEquals(
            listOf("M:quads,hams,glutes", "M:lats,biceps", "M:chest,triceps", "M:shoulders,reardelts,abs"),
            planner.weekSequence(s),
        )
        assertEquals(listOf(0, 1, 3, 4), planner.weekdays(s))
        assertEquals("Legs", planner.dayName(planner.weekSequence(s).first()))
        assertTrue(Recovery.newClashes(lib, planner, s).isEmpty())
    }

    @Test
    fun backToBackSameMusclesAreCaught() {
        // full body on Mon, Tue, Wed: every big muscle twice in a row
        val s = PlanSettings(kit, daysPerWeek = 3, split = Split.FULL_BODY, sessionWeekdays = listOf(0, 1, 2))
        val clashes = Recovery.newClashes(lib, planner, s)
        assertTrue(clashes.any { it.muscle == Muscle.CHEST && it.firstDay == 0 && it.secondDay == 1 })
        assertTrue(clashes.any { it.muscle == Muscle.QUADS && it.firstDay == 1 && it.secondDay == 2 })
        assertTrue(clashes.none { it.muscle == Muscle.ABS })
        // the default Mon, Wed, Fri has none
        assertTrue(Recovery.clashes(lib, planner, s.copy(sessionWeekdays = null)).isEmpty())
    }

    @Test
    fun sundayWrapsToMonday() {
        // full body A on Sunday and B on Monday: back to back across the week boundary
        val s = PlanSettings(kit, daysPerWeek = 2, split = Split.FULL_BODY, sessionWeekdays = listOf(6, 0))
        assertEquals(listOf("B", "A"), planner.weekSequence(s))
        assertEquals(listOf(0, 6), planner.weekdays(s))
        assertTrue(Recovery.clashes(lib, planner, s).any { it.firstDay == 6 && it.secondDay == 0 })
    }

    @Test
    fun theDefaultIsNeverBlockedEvenWhenItHasClashes() {
        // 7 days a week: back-to-back work can't be avoided, so the default layout is always allowed
        val seven = PlanSettings(kit, daysPerWeek = 7)
        assertTrue(Recovery.clashes(lib, planner, seven).isNotEmpty())
        assertTrue(Recovery.newClashes(lib, planner, seven).isEmpty())
    }

    @Test
    fun invalidLayoutsFallBackToTheDefault() {
        listOf(listOf(0, 1), listOf(0, 0, 1, 2), listOf(0, 1, 2, 9)).forEach { bad ->
            val s = bodyPart.copy(sessionWeekdays = bad)
            assertEquals(Planner.BODY_PART, planner.weekSequence(s))
            assertEquals(listOf(0, 1, 3, 4), planner.weekdays(s))
        }
    }
}
