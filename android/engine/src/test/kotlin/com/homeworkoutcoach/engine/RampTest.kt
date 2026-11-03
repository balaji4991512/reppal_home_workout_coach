package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sets are planned as a ramp: the last at the top weight, each earlier one a dumbbell step lighter. Bench: the session that
 * started this was 15 reps at 10 kg, 12 at 13.5 and 12 at 16, and the next plan was 3 x 12 at 16 for every set.
 */
class RampTest {
    private val lib = Library.default
    private val kit = Equipment(Bench.ADJUSTABLE, lib.dumbbellStepsKg.toSet())
    private val bench = lib.exercise("bench")
    private val pushup = lib.exercise("pushup")

    private fun ramp(vararg w: Double) = PlannedExercise("bench", w.size, 12, w.last(), w.toList())
    private fun done(vararg setsDone: Pair<Int, Double>) = setsDone.map { LoggedSet(it.first, it.second, 12) }

    // ---- The ramp itself ----

    @Test
    fun aTopOfSixteenOverThreeSetsStartsAtElevenAndAHalf() {
        assertEquals(listOf(11.5, 13.5, 16.0), Ramp.weights(kit, 16.0, 3))
        assertEquals(listOf(10.0, 11.5, 13.5, 16.0), Ramp.weights(kit, 16.0, 4))
        assertEquals(listOf(13.5, 16.0), Ramp.weights(kit, 16.0, 2))
        assertEquals(listOf(16.0), Ramp.weights(kit, 16.0, 1))
    }

    @Test
    fun theLightestDumbbellIsAFloor() {
        assertEquals(listOf(2.5, 2.5, 2.5), Ramp.weights(kit, 2.5, 3))
        assertEquals(listOf(2.5, 3.5, 4.5), Ramp.weights(kit, 4.5, 3))
        assertEquals(listOf(2.5, 2.5, 3.5), Ramp.weights(kit, 3.5, 3))
    }

    @Test
    fun aFineSteppedSetStillRampsByAtLeastTenPercent() {
        val every = Equipment(Bench.ADJUSTABLE, (1..24).map { it.toDouble() }.toSet())
        assertEquals(listOf(12.0, 14.0, 16.0), Ramp.weights(every, 16.0, 3))
        // one dumbbell only: nothing to ramp with
        assertEquals(listOf(10.0, 10.0, 10.0), Ramp.weights(Equipment(Bench.FLAT, setOf(10.0)), 10.0, 3))
    }

    @Test
    fun aRampNeverGoesDownNorAboveTheTop() {
        for (top in kit.dumbbells) for (sets in 1..MAX_SETS) {
            val w = Ramp.weights(kit, top, sets)
            assertEquals("$top x $sets", sets, w.size)
            assertEquals("$top x $sets", top, w.last(), 0.0)
            assertTrue("$top x $sets", w.zipWithNext().all { (a, b) -> a <= b })
            assertTrue("$top x $sets", w.all { kit.owns(it) })
        }
    }

    @Test
    fun aBodyweightMoveIsNotRamped() {
        val p = Ramp.plan(pushup, kit, ExerciseState(0.0, 15, 3))
        assertFalse(p.ramped)
        assertEquals(45.0, p.volume(bodyweight = true), 0.0)
    }

    @Test
    fun aWeightedMoveIsPlannedFromItsState() {
        val p = Ramp.plan(bench, kit, ExerciseState(16.0, 12, 3))
        assertEquals(listOf(11.5, 13.5, 16.0), p.setWeights)
        assertEquals(16.0, p.weightKg, 0.0)
        assertEquals((11.5 + 13.5 + 16.0) * 12, p.volume(bodyweight = false), 1e-9)
    }

    @Test
    fun anOlderPlanHasEverySetAtTheSameWeight() {
        val old = PlannedExercise("bench", 3, 12, 16.0)
        assertFalse(old.ramped)
        assertEquals(listOf(16.0, 16.0, 16.0), (0 until 3).map { old.weightOf(it) })
        assertEquals(576.0, old.volume(bodyweight = false), 0.0)
    }

    // ---- Live: the next set steps up ----

    private fun live(w: Double, reps: Int, effort: Effort, planNext: LiveTarget?, lifted: Double? = null) =
        Progression.adjustLive(bench, kit, LiveTarget(w, 12), SetRecord(reps, lifted, effort), planNext)

    @Test
    fun aMetSetStepsUpToTheNextPlannedWeight() {
        val a = live(11.5, 12, Effort.JUST_RIGHT, LiveTarget(13.5, 12))
        assertEquals(LiveAdjustment.StepUp(LiveTarget(13.5, 12)), a)
        // an easy set at the top of the range already moves up on its own, to the same weight, for a few fewer reps
        assertEquals(LiveAdjustment.MoveUp(LiveTarget(13.5, 11)), live(11.5, 12, Effort.EASY, LiveTarget(13.5, 12)))
    }

    @Test
    fun aSetShortOfItsTargetOrWithPainDoesNotStepUp() {
        assertTrue(live(11.5, 9, Effort.HARD, LiveTarget(13.5, 12)) is LiveAdjustment.ShortOfTarget)
        assertEquals(LiveTarget(11.5, 12), live(11.5, 9, Effort.HARD, LiveTarget(13.5, 12)).next)
        val pain = Progression.adjustLive(bench, kit, LiveTarget(11.5, 12), SetRecord(12, null, Effort.JUST_RIGHT, setOf(SetFlag.PAIN)), LiveTarget(13.5, 12))
        assertTrue(pain is LiveAdjustment.Pain)
    }

    @Test
    fun aVeryEasySetCanGoFurtherThanTheRampAlready() {
        // 15 reps at 11.5 is past the top of the range: the live rule's own move up (13.5 for 13) already reaches the planned 13.5
        val a = live(11.5, 15, Effort.EASY, LiveTarget(13.5, 12))
        assertEquals(LiveAdjustment.MoveUp(LiveTarget(13.5, 13)), a)
    }

    @Test
    fun theStepUpIsOneDumbbellAtATime() {
        // the middle set was held at 11.5 (the first was short), so the last set's 16 is two steps away: 13.5 comes first
        val a = live(11.5, 12, Effort.JUST_RIGHT, LiveTarget(16.0, 12))
        assertEquals(LiveAdjustment.StepUp(LiveTarget(13.5, 12)), a)
    }

    @Test
    fun liftingMoreThanThePlannedNextSetLeavesTheRampBehind() {
        val a = live(11.5, 12, Effort.JUST_RIGHT, LiveTarget(13.5, 12), lifted = 16.0)
        assertTrue(a is LiveAdjustment.NotEasy)
        assertEquals(LiveTarget(16.0, 12), a.next)
    }

    @Test
    fun withoutARampTheLiveRuleIsAsBefore() {
        assertEquals(LiveAdjustment.NotEasy(LiveTarget(11.5, 12)), live(11.5, 12, Effort.JUST_RIGHT, null))
    }

    // ---- Next session ----

    @Test
    fun reachingTheTopRepsOnTheTopSetMovesTheTopUp() {
        val n = Progression.nextSession(bench, kit, ramp(11.5, 13.5, 16.0), done(12 to 11.5, 12 to 13.5, 12 to 16.0))
        assertTrue(n is NextSession.Progressed)
        n as NextSession.Progressed
        assertEquals(ProgressReason.HEAVIER, n.reason)
        assertEquals(ExerciseState(18.0, 11, 3), n.next) // 18 kg for 11 keeps the work about level
        assertEquals((13.5 + 16.0 + 18.0) * 11, n.nextVolume, 1e-9)
    }

    @Test
    fun aTopSetBelowTheTopRepsAddsARep() {
        val plan = PlannedExercise("bench", 3, 10, 16.0, listOf(11.5, 13.5, 16.0))
        val n = Progression.nextSession(bench, kit, plan, done(10 to 11.5, 10 to 13.5, 11 to 16.0)) as NextSession.Progressed
        assertEquals(ProgressReason.MORE_REPS, n.reason)
        assertEquals(ExerciseState(16.0, 11, 3), n.next)
    }

    @Test
    fun lighterSetsDoNotMoveTheTopWeight() {
        // lots of reps early do not count as the top set: the top set was short, so the plan stays
        val n = Progression.nextSession(bench, kit, ramp(11.5, 13.5, 16.0), done(15 to 11.5, 14 to 13.5, 10 to 16.0))
        assertTrue(n is NextSession.TopSetShort)
        assertEquals(ExerciseState(16.0, 12, 3), n.next)
    }

    @Test
    fun workShortOfThePlanRepeatsIt() {
        val n = Progression.nextSession(bench, kit, ramp(11.5, 13.5, 16.0), done(12 to 11.5, 10 to 13.5, 9 to 16.0))
        assertTrue(n is NextSession.Repeat)
        assertEquals(ExerciseState(16.0, 12, 3), n.next)
    }

    @Test
    fun aWeightLiftedBeyondThePlanIsTheNewTop() {
        // planned to 13.5, and the top set went to 16 for 12: the next plan ramps up to 16 (11.5, 13.5, 16), not 18
        val n = Progression.nextSession(bench, kit, ramp(10.0, 11.5, 13.5), done(12 to 11.5, 12 to 13.5, 12 to 16.0)) as NextSession.Progressed
        assertEquals(ProgressReason.NEW_TOP, n.reason)
        assertEquals(ExerciseState(16.0, 12, 3), n.next)
        assertEquals(listOf(11.5, 13.5, 16.0), Ramp.weights(kit, n.next.weightKg, n.next.sets))
    }

    @Test
    fun aNewTopKeepsTheRepsItWasLiftedForWithinTheRange() {
        fun reps(r: Int) = (Progression.nextSession(bench, kit, ramp(10.0, 11.5, 13.5), done(15 to 11.5, 15 to 13.5, r to 16.0)) as NextSession.Progressed).next.targetReps
        assertEquals(12, reps(15)) // never above the top of the range
        assertEquals(10, reps(10))
        assertEquals(LOW_REPS, reps(5)) // and never below 8
    }

    @Test
    fun withNothingHeavierASetIsAddedThenTheSamePlan() {
        // 24 kg is the heaviest setting; a step down is at least 10% lighter, so three sets ramp 18, 20.5, 24
        val three = Ramp.plan(bench, kit, ExerciseState(24.0, 12, 3))
        assertEquals(listOf(18.0, 20.5, 24.0), three.setWeights)
        val a = Progression.nextSession(bench, kit, three, three.setWeights.map { LoggedSet(12, it, 12) }) as NextSession.Progressed
        assertEquals(ProgressReason.EXTRA_SET, a.reason)
        // 750 kg done; four sets over 16, 18, 20.5, 24 are 78.5 kg a rep: 11 reps is 863, nearer 10% more (825) than 10 reps (785)
        assertEquals(ExerciseState(24.0, 11, 4), a.next)

        val four = Ramp.plan(bench, kit, ExerciseState(24.0, 12, 4))
        val b = Progression.nextSession(bench, kit, four, four.setWeights.map { LoggedSet(12, it, 12) })
        assertTrue(b is NextSession.NoOption)
        assertEquals(ExerciseState(24.0, 12, 4), b.next)
    }

    @Test
    fun aPlanSavedBeforeRampsStillProgressesByTheOldRule() {
        // 3 x 12 at 10, done as 15 at 10, 12 at 13.5, 12 at 16: the heaviest weight and about 10% more work, as before
        val old = PlannedExercise("bench", 3, 12, 10.0)
        val n = Progression.nextSession(bench, kit, old, done(15 to 10.0, 12 to 13.5, 12 to 16.0))
        assertTrue(n is NextSession.Progressed)
        assertEquals(ProgressReason.VOLUME, (n as NextSession.Progressed).reason)
        assertEquals(ExerciseState(16.0, 12, 3), n.next)
    }

    @Test
    fun theNextPlanForThatSessionStartsAtElevenAndAHalf() {
        // and that next state, planned as a ramp, is the thing the user asked for
        val state = ExerciseState(16.0, 12, 3)
        assertEquals(listOf(11.5, 13.5, 16.0), Ramp.plan(bench, kit, state).setWeights)
    }

    @Test
    fun bodyweightMovesProgressByTheOldRule() {
        val plan = PlannedExercise("pushup", 3, 12, 0.0)
        val n = Progression.nextSession(pushup, kit, plan, listOf(LoggedSet(15, 0.0, 12), LoggedSet(15, 0.0, 12), LoggedSet(15, 0.0, 12)))
        assertTrue(n is NextSession.Progressed)
        assertEquals(ProgressReason.VOLUME, (n as NextSession.Progressed).reason)
        assertEquals(0.0, n.next.weightKg, 0.0)
    }
}
