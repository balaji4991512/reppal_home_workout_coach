package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's plan rules: per-muscle targets, 5-exercise split days with 4-set big lifts, fuller full body days. */
class PlanRulesTest {
    private val lib = Library.default
    private val planner = Planner(lib)
    private val kit = Equipment(Bench.ADJUSTABLE, lib.dumbbellStepsKg.toSet())

    private fun count(day: Day, m: Muscle) = day.items.count { lib.exercise(it.exerciseId).primary.first() == m }

    @Test
    fun everyMuscleHasATargetThatGrowsWithLevel() {
        Muscle.entries.forEach { m ->
            val (b, i, a) = Level.entries.map { PlanRules.CURRENT.sets(m, it) }
            assertTrue("$m", b < i && i <= a)
            Level.entries.forEach { l ->
                val r = PlanRules.CURRENT.range(m, l)
                assertTrue("$m $l", r.low <= PlanRules.CURRENT.sets(m, l) && PlanRules.CURRENT.sets(m, l) <= r.high)
            }
        }
    }

    @Test
    fun chestDayIsThreeChestLiftsAtFourSetsAndTwoTricepsAtThree() {
        val s = PlanSettings(kit, daysPerWeek = 4, split = Split.BODY_PART)
        val chest = planner.buildDay(Planner.BODY_PART[0], s)
        assertEquals(5, chest.items.size)
        assertEquals(3, count(chest, Muscle.CHEST))
        assertEquals(2, count(chest, Muscle.TRICEPS))
        val sets = chest.items.map { lib.exercise(it.exerciseId).primary.first() to it.sets }
        assertTrue(sets.all { (m, n) -> n == if (m == Muscle.CHEST) 4 else 3 })
    }

    @Test
    fun repeatedChestDaysAreFullAtThreeSets() {
        // 5 days of body part: chest and triceps comes round twice
        val week = planner.buildWeek(PlanSettings(kit, daysPerWeek = 5, split = Split.BODY_PART))
        val chestDays = week.filter { it.label.startsWith(Planner.BODY_PART[0]) }
        assertEquals(2, chestDays.size)
        chestDays.forEach { day ->
            assertEquals(5, day.items.size)
            assertTrue(day.items.all { it.sets == 3 })
        }
        // trained once a week, back keeps 4 sets on its big lifts
        assertEquals(4, week.first { it.label == Planner.BODY_PART[1] }.items.first().sets)
    }

    @Test
    fun splitDaysHaveAtMostFiveExercises() {
        Split.entries.forEach { split ->
            (1..7).forEach { d ->
                Level.entries.forEach { level ->
                    planner.buildWeek(PlanSettings(kit, level, d, split)).filter { it.label !in PlanRules.FULL_BODY }
                        .forEach { assertTrue("$split $d ${it.label}", it.items.size <= 5) }
                }
            }
        }
    }

    @Test
    fun fullBodyDaysHaveSixToSevenExercisesAtThreeSets() {
        val s = PlanSettings(kit, daysPerWeek = 3)
        val a = planner.buildDay("A", s)
        val b = planner.buildDay("B", s)
        assertEquals(listOf("Push", "Pull", "Squat", "Hinge", "Core", "Shoulders", "Biceps"), a.items.map { it.slot })
        assertEquals(listOf("Push", "Shoulders", "Pull", "Squat", "Hinge", "Core", "Triceps"), b.items.map { it.slot })
        assertEquals(6, planner.buildDay("F", s).items.size)
        assertTrue((a.items + b.items).all { it.sets == 3 })
    }

    @Test
    fun upperDayDropsItsSixthSlot() {
        val u2 = planner.buildDay("U2", PlanSettings(kit, daysPerWeek = 4))
        assertEquals(listOf("Push", "Pull", "Lats", "Rear delts", "Triceps"), u2.items.map { it.slot })
    }

    @Test
    fun swapsKeepTheDaysSets() {
        val s = PlanSettings(kit, daysPerWeek = 4, split = Split.BODY_PART)
        val first = planner.buildDay(Planner.BODY_PART[0], s).items.first()
        val alt = planner.swapAlternatives(first, planner.buildDay(Planner.BODY_PART[0], s), s).first()
        val swapped = planner.buildDay(Planner.BODY_PART[0], s.copy(swaps = mapOf(first.exerciseId to alt))).items.first()
        assertEquals(alt, swapped.exerciseId)
        assertEquals(4, swapped.sets)
    }

    @Test
    fun prototypeRulesAreUnchanged() {
        val p = PlanRules.prototype(lib)
        assertEquals(SetRange(6, 10), p.range(Muscle.CHEST, Level.BEGINNER))
        assertEquals(SetRange(3, 5), p.range(Muscle.BICEPS, Level.BEGINNER))
        val old = Planner(lib, p)
        val s = PlanSettings(kit, daysPerWeek = 4, split = Split.BODY_PART)
        assertEquals(3, count(old.buildDay(Planner.BODY_PART[0], s), Muscle.CHEST))
        assertEquals(5, old.buildDay("A", s).items.size)
        assertTrue(old.buildDay(Planner.BODY_PART[0], s).items.all { it.sets == 3 })
    }

    @Test
    fun atLeastOnlyRaises() {
        assertEquals(4, ExerciseState(5.0, sets = 3).atLeast(4).sets)
        assertEquals(4, ExerciseState(5.0, sets = 4).atLeast(3).sets)
    }
}
