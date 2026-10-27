package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-muscle weekly set targets and what they build. */
class TargetsTest {
    private val lib = Library.default
    private val planner = Planner(lib)
    private val kit = Equipment(Bench.ADJUSTABLE, lib.dumbbellStepsKg.toSet())

    private fun count(day: Day, m: Muscle) = day.items.count { lib.exercise(it.exerciseId).primary.first() == m }

    @Test
    fun everyMuscleHasATargetThatGrowsWithLevel() {
        Muscle.entries.forEach { m ->
            val (b, i, a) = Level.entries.map { WeeklyTargets.PER_MUSCLE.sets(m, it) }
            assertTrue("$m", b < i && i <= a)
            Level.entries.forEach { l ->
                val r = WeeklyTargets.PER_MUSCLE.range(m, l)
                assertTrue("$m $l", r.low <= WeeklyTargets.PER_MUSCLE.sets(m, l) && WeeklyTargets.PER_MUSCLE.sets(m, l) <= r.high)
            }
        }
    }

    @Test
    fun beginnerChestDayHasFourChestAndTwoTricepsExercises() {
        val s = PlanSettings(kit, daysPerWeek = 4, split = Split.BODY_PART)
        val chest = planner.buildDay(Planner.BODY_PART[0], s)
        assertEquals(4, count(chest, Muscle.CHEST))
        assertEquals(2, count(chest, Muscle.TRICEPS))
        // the prototype's tables give 3 and 2
        val old = Planner(lib, WeeklyTargets.prototype(lib)).buildDay(Planner.BODY_PART[0], s)
        assertEquals(3, count(old, Muscle.CHEST))
    }

    @Test
    fun beginnerDaysStayWithinSevenExercises() {
        Split.entries.filter { it != Split.AUTO }.forEach { split ->
            (1..7).forEach { d ->
                planner.buildWeek(PlanSettings(kit, daysPerWeek = d, split = split)).forEach { assertTrue(it.items.size <= 7) }
            }
        }
    }

    @Test
    fun prototypeRangesHalveForArms() {
        val p = WeeklyTargets.prototype(lib)
        assertEquals(SetRange(6, 10), p.range(Muscle.CHEST, Level.BEGINNER))
        assertEquals(SetRange(3, 5), p.range(Muscle.BICEPS, Level.BEGINNER))
    }
}
