package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance examples from docs/BUILD_PLAN.md plus hand-checked cases for each rule. */
class AcceptanceTest {
    private val lib = Library.default
    private val planner = Planner(lib)
    private val allDumbbells = lib.dumbbellStepsKg.toSet()
    private val fullKit = Equipment(Bench.ADJUSTABLE, allDumbbells)

    private fun next(id: String, plan: PlannedExercise, vararg sets: Pair<Int, Double>) =
        Progression.nextSession(lib.exercise(id), fullKit, plan, sets.map { LoggedSet(it.first, it.second, plan.targetReps) })

    @Test
    fun theDumbbellsAreTheFifteenSettingsOfAnAdjustableSet() {
        assertEquals(listOf(2.5, 3.5, 4.5, 5.5, 6.5, 8.0, 9.0, 10.0, 11.5, 13.5, 16.0, 18.0, 20.5, 22.5, 24.0), lib.dumbbellStepsKg)
        assertEquals(15, lib.dumbbellStepsKg.size)
    }

    @Test
    fun anOldWeightMovesToTheNearestSettingAndTheLighterOneWhenHalfway() {
        assertEquals(4.5, Library.snapToDumbbellSet(5.0), 0.0) // 4.5 and 5.5 are equally near: the lighter
        assertEquals(5.5, Library.snapToDumbbellSet(6.0), 0.0)
        assertEquals(8.0, Library.snapToDumbbellSet(8.0), 0.0) // already a setting
        assertEquals(10.0, Library.snapToDumbbellSet(10.4), 0.0)
        assertEquals(2.5, Library.snapToDumbbellSet(1.0), 0.0)
        assertEquals(24.0, Library.snapToDumbbellSet(30.0), 0.0)
    }

    @Test
    fun libraryLoads() {
        assertEquals(56, lib.exercises.size)
        assertEquals(10, lib.muscleLabels.size)
        assertEquals((1..7).toSet(), lib.weekSequences.keys)
    }

    // ---- Next-session progression ----

    @Test
    fun heavierThanPlannedMovesToNextDumbbell() {
        // planned 4.5 kg, but done at 9 kg: the next dumbbell above 9 on the adjustable set is 10 kg
        val r = next("bench", PlannedExercise("bench", 3, 12, 4.5), 15 to 9.0, 12 to 9.0, 12 to 9.0)
        assertEquals(351.0, r.actualVolume, 0.0)
        assertEquals(ExerciseState(10.0, 12, 3), r.next)
        r as NextSession.Progressed
        assertEquals(360.0, r.nextVolume, 0.0)
        assertEquals(3, r.increasePercent)
    }

    @Test
    fun exactPlanDoneAddsAboutTenPercent() {
        // 3 x 12 at 4.5 kg is 162 kg; 5.5 kg for 11 reps is 181.5 kg, about 12% more
        val r = next("bench", PlannedExercise("bench", 3, 12, 4.5), 12 to 4.5, 12 to 4.5, 12 to 4.5) as NextSession.Progressed
        assertEquals(162.0, r.actualVolume, 0.0)
        assertEquals(ExerciseState(5.5, 11, 3), r.next)
        assertEquals(181.5, r.nextVolume, 0.0)
        assertEquals(12, r.increasePercent)
    }

    @Test
    fun moveUpMidSessionCountsHeaviestWeight() {
        val r = next("bench", PlannedExercise("bench", 3, 12, 4.5), 12 to 4.5, 11 to 5.5, 12 to 5.5) as NextSession.Progressed
        assertEquals(180.5, r.actualVolume, 0.0)
        assertEquals(ExerciseState(5.5, 12, 3), r.next)
        assertEquals(10, r.increasePercent)
    }

    @Test
    fun shortVolumeRepeatsPlan() {
        val r = next("bench", PlannedExercise("bench", 3, 12, 5.0), 10 to 5.0, 9 to 5.0, 8 to 5.0)
        assertTrue(r is NextSession.Repeat)
        assertEquals(135.0, r.actualVolume, 0.0)
        assertEquals(ExerciseState(5.0, 12, 3), r.next)
    }

    @Test
    fun bodyweightProgressesInReps() {
        val r = next("pushup", PlannedExercise("pushup", 3, 12, 0.0), 15 to 0.0, 14 to 0.0, 14 to 0.0)
        assertEquals(43.0, r.actualVolume, 0.0)
        assertEquals(ExerciseState(0.0, 15, 3), r.next)
    }

    // ---- Live adjustment ----

    @Test
    fun liveRules() {
        val bench = lib.exercise("bench")
        val kit = Equipment(Bench.FLAT, setOf(5.0, 6.0))
        fun adj(target: Int, reps: Int, effort: Effort, w: Double = 5.0) =
            Progression.adjustLive(bench, kit, LiveTarget(w, target), SetRecord(reps, null, effort))

        assertEquals(LiveTarget(5.0, 10), adj(10, 9, Effort.EASY).next)
        assertTrue(adj(10, 9, Effort.EASY) is LiveAdjustment.ShortOfTarget)
        assertTrue(adj(10, 10, Effort.HARD) is LiveAdjustment.NotEasy)
        assertEquals(LiveTarget(5.0, 11), adj(10, 10, Effort.EASY).next)
        // easy at the top: 12 x 5 kg -> 6 kg for floor(12 * 5 / 6) + 1 = 11
        assertEquals(LiveAdjustment.MoveUp(LiveTarget(6.0, 11)), adj(12, 12, Effort.EASY))
        assertTrue(adj(12, 12, Effort.EASY, w = 6.0) is LiveAdjustment.NothingHeavier)

        val pain = Progression.adjustLive(bench, kit, LiveTarget(5.0, 12), SetRecord(12, null, Effort.EASY, setOf(SetFlag.PAIN)))
        assertEquals(LiveAdjustment.Pain(LiveTarget(5.0, 12)), pain)

        val pushup = lib.exercise("pushup")
        val bw = Progression.adjustLive(pushup, kit, LiveTarget(0.0, 15), SetRecord(15, null, Effort.EASY))
        assertTrue(bw is LiveAdjustment.NothingHeavier)
    }

    @Test
    fun theNextSetStartsFromWhatWasDoneNotWhatWasPlanned() {
        val bench = lib.exercise("bench") // top of the range is 12
        val kit = Equipment(Bench.FLAT, setOf(5.0, 6.0, 8.0, 16.0))
        fun adj(target: Int, reps: Int, effort: Effort, planned: Double, lifted: Double? = null) =
            Progression.adjustLive(bench, kit, LiveTarget(planned, target), SetRecord(reps, lifted, effort))

        // planned 12 x 5 kg, did 15 x 16 kg: the next set is at 16 kg and 15 reps, not the plan
        assertEquals(LiveAdjustment.NotEasy(LiveTarget(16.0, 15)), adj(12, 15, Effort.JUST_RIGHT, 5.0, 16.0))
        assertEquals(LiveAdjustment.NotEasy(LiveTarget(16.0, 15)), adj(12, 15, Effort.HARD, 5.0, 16.0))
        // beating a target below the top, still hard: the reps reached are the new target
        assertEquals(LiveAdjustment.NotEasy(LiveTarget(5.0, 11)), adj(10, 11, Effort.HARD, 5.0))
        // easy and below the top: one more than was done
        assertEquals(LiveAdjustment.AddRep(LiveTarget(5.0, 12)), adj(10, 11, Effort.EASY, 5.0))
        // easy and already past the top: moves up, keeping the volume (15 x 5 = 75 -> 13 x 6 = 78)
        assertEquals(LiveAdjustment.MoveUp(LiveTarget(6.0, 13)), adj(12, 15, Effort.EASY, 5.0))
        // nothing heavier: stays at what was done
        assertEquals(LiveAdjustment.NothingHeavier(LiveTarget(16.0, 15)), adj(12, 15, Effort.EASY, 5.0, 16.0))
        // short of the target: unchanged, as before
        assertEquals(LiveAdjustment.ShortOfTarget(LiveTarget(16.0, 12)), adj(12, 10, Effort.EASY, 5.0, 16.0))
    }

    @Test
    fun theNextSetIsNeverWorthLessThanTheOneJustDone() {
        val bench = lib.exercise("bench")
        val pushup = lib.exercise("pushup")
        val owned = setOf(2.5, 5.0, 6.5, 8.0, 10.0, 12.0, 16.0, 20.0)
        val kit = Equipment(Bench.FLAT, owned)
        for (e in listOf(bench, pushup)) for (target in 6..15) for (reps in target..30) for (effort in Effort.entries) for (w in owned) {
            val weight = if (e.bodyweight) 0.0 else w
            val a = Progression.adjustLive(e, kit, LiveTarget(weight, target), SetRecord(reps, if (e.bodyweight) null else w, effort))
            val ctx = "${e.id} target $target did $reps x $w ($effort) -> ${a.next}"
            val done = reps * (if (e.bodyweight) 1.0 else w)
            val next = a.next.targetReps * (if (e.bodyweight) 1.0 else a.next.weightKg)
            assertTrue(ctx, next >= done - 1e-9)
        }
    }

    // ---- Starting weights ----

    @Test
    fun startingWeightsAndSnapping() {
        val goblet = lib.exercise("goblet") // startKg 8
        val kit = Equipment(Bench.NONE, setOf(5.0, 10.0, 12.0))
        assertEquals(5.0, Progression.resolveState(goblet, null, kit, Level.BEGINNER).weightKg, 0.0)
        assertEquals(10.0, Progression.resolveState(goblet, null, kit, Level.INTERMEDIATE).weightKg, 0.0)
        assertEquals(12.0, Progression.resolveState(goblet, null, kit, Level.ADVANCED).weightKg, 0.0)
        assertEquals(ExerciseState(12.0, 12, 3), Progression.resolveState(goblet, null, kit, Level.ADVANCED))
        // nothing light enough: lightest owned
        assertEquals(10.0, Progression.resolveState(goblet, null, Equipment(Bench.NONE, setOf(10.0, 20.0)), Level.BEGINNER).weightKg, 0.0)
        // stored weight no longer owned: snap down, keep reps and sets
        val stored = ExerciseState(14.0, 9, 4)
        assertEquals(ExerciseState(12.0, 9, 4), Progression.resolveState(goblet, stored, kit, Level.BEGINNER))
        assertEquals(ExerciseState(5.0, 9, 4), Progression.resolveState(goblet, stored.copy(weightKg = 4.0), kit, Level.BEGINNER))
    }

    // ---- Planner ----

    @Test
    fun bodyPartSplitWithNoEquipment() {
        val s = PlanSettings(Equipment(Bench.NONE, emptySet()), Level.BEGINNER, daysPerWeek = 4, split = Split.BODY_PART)
        val week = planner.buildWeek(s)
        assertEquals(listOf("pushup", "wpush", "cpush"), week[0].items.map { it.exerciseId })
        assertTrue("Biceps" in week[1].missing)
        assertEquals("Chest and triceps", planner.dayName(week[0].label))
    }

    @Test
    fun everyCombinationBuildsWithoutDuplicates() {
        val dumbbellSets = listOf(emptySet(), setOf(5.0), setOf(2.0, 4.0, 8.0), setOf(10.0, 20.0), allDumbbells)
        for (bench in Bench.entries) for (db in dumbbellSets) for (days in 1..7) for (split in Split.entries) for (level in Level.entries) {
            val s = PlanSettings(Equipment(bench, db), level, days, split)
            val seq = planner.weekSequence(s)
            assertEquals(days, seq.size)
            for (day in planner.buildWeek(s)) {
                val ids = day.items.map { it.exerciseId }
                assertEquals("$s ${day.label}", ids.size, ids.toSet().size)
                assertTrue(ids.all { s.equipment.canDo(lib.exercise(it)) })
                if (day.label.startsWith("M:")) assertTrue(ids.size <= planner.rules.dayCap(level))
            }
        }
    }

    @Test
    fun repeatedMuscleDaysVaryExercises() {
        val s = PlanSettings(fullKit, Level.BEGINNER, daysPerWeek = 6, split = Split.PUSH_PULL_LEGS)
        val seq = planner.weekSequence(s)
        assertEquals(listOf(Planner.PPL[0], Planner.PPL[1], Planner.PPL[2], Planner.PPL[0] + "#1", Planner.PPL[1] + "#1", Planner.PPL[2] + "#1"), seq)
        val week = planner.buildWeek(s)
        assertTrue(week[0].items.map { it.exerciseId } != week[3].items.map { it.exerciseId })
        assertEquals("Push", planner.dayName(seq[3]))
    }

    @Test
    fun excludedAndSwappedExercises() {
        val base = PlanSettings(fullKit, daysPerWeek = 3)
        val day = planner.buildDay("A", base)
        assertEquals("bench", day.items[0].exerciseId)
        val excluded = planner.buildDay("A", base.copy(excluded = setOf("bench")))
        assertEquals("floor", excluded.items[0].exerciseId)

        val alts = planner.swapAlternatives(day.items[0], day, base)
        assertTrue("bench" !in alts && "floor" in alts)
        val swapped = planner.buildDay("A", base.copy(swaps = mapOf("bench" to "floor")))
        assertEquals(DayItem("Push", "floor", "bench"), swapped.items[0])
        assertTrue(swapped.items[0].swapped)
        // a swap to an excluded exercise is ignored
        val ignored = planner.buildDay("A", base.copy(swaps = mapOf("bench" to "floor"), excluded = setOf("floor")))
        assertEquals("bench", ignored.items[0].exerciseId)
    }

    @Test
    fun concernsMapToExclusions() {
        val ids = KeywordConcernMapper(lib).excludedIds("Lower back issues, can't do deadlifts")
        assertTrue("rdl" in ids && "brow" in ids && "gm" in ids)
        assertFalse("bench" in ids)
        assertTrue(KeywordConcernMapper(lib).excludedIds("").isEmpty())
    }

    // ---- Volume and summary ----

    @Test
    fun summaryHitShortStopped() {
        val plan = listOf(
            PlannedExercise("bench", 3, 12, 5.0),
            PlannedExercise("pushup", 3, 12, 0.0),
            PlannedExercise("goblet", 3, 12, 8.0),
        )
        val sets = mapOf(
            "bench" to List(3) { LoggedSet(12, 5.0, 12) },
            "pushup" to listOf(LoggedSet(12, 0.0, 12), LoggedSet(10, 0.0, 12), LoggedSet(9, 0.0, 12)),
            "goblet" to listOf(LoggedSet(6, 8.0, 12)),
        )
        val s = Volume.summarize(lib, plan, sets, pain = setOf("goblet"))
        assertEquals(listOf(ExerciseOutcome.HIT, ExerciseOutcome.SHORT, ExerciseOutcome.STOPPED), s.rows.map { it.outcome })
        assertEquals(180.0 + 48.0, s.liftedKg, 0.0)
        assertEquals(180.0 + 288.0, s.plannedKg, 0.0)
        assertEquals(31, s.bodyweightReps)
        assertEquals(36, s.plannedBodyweightReps)
        assertEquals(49, s.percent) // 228 / 468
        assertFalse(s.targetReached)
        assertEquals(180.0, s.kgByMuscle[Muscle.CHEST]!!, 0.0) // bodyweight reps never count as kg
        assertEquals(31, s.repsByMuscle[Muscle.CHEST])
        assertNull(s.kgByMuscle[Muscle.TRICEPS]) // primary muscle only
    }

    @Test
    fun summaryHitOnLiveTargets() {
        // moved up mid-session: 172 kg < 180 kg planned, but every live target was met, so Hit
        val plan = listOf(PlannedExercise("bench", 3, 12, 5.0))
        val moved = listOf(LoggedSet(12, 5.0, 12), LoggedSet(7, 8.0, 7), LoggedSet(7, 8.0, 7))
        assertEquals(ExerciseOutcome.HIT, Volume.summarize(lib, plan, mapOf("bench" to moved), emptySet()).rows.single().outcome)
        // one set under its live target and volume short: Short
        val missed = listOf(LoggedSet(12, 5.0, 12), LoggedSet(7, 8.0, 7), LoggedSet(6, 8.0, 7))
        assertEquals(ExerciseOutcome.SHORT, Volume.summarize(lib, plan, mapOf("bench" to missed), emptySet()).rows.single().outcome)
        // not all planned sets done: Short
        val partial = Volume.summarize(lib, plan, mapOf("bench" to listOf(LoggedSet(12, 5.0, 12))), emptySet()).rows.single()
        assertEquals(ExerciseOutcome.SHORT, partial.outcome)
    }

    @Test
    fun bodyweightOnlyPercentUsesReps() {
        val plan = listOf(PlannedExercise("pushup", 3, 10, 0.0))
        val s = Volume.summarize(lib, plan, mapOf("pushup" to List(3) { LoggedSet(10, 0.0, 10) }), emptySet())
        assertEquals(100, s.percent)
        assertTrue(s.targetReached)
        assertEquals(0.0, s.liftedKg, 0.0)
    }

    @Test
    fun plannedWeekCountsKgAndRepsSeparately() {
        val s = PlanSettings(fullKit, daysPerWeek = 1)
        val week = planner.buildWeek(s)
        val state = { id: String -> Progression.resolveState(lib.exercise(id), null, s.equipment, s.level) }
        val v = Volume.plannedWeek(lib, s.level, week, state)
        // F day: bench 8 kg (chest), dead bug bodyweight (abs)
        assertEquals(3 * 12 * 8.0, v.getValue(Muscle.CHEST).kg, 0.0)
        assertEquals(0, v.getValue(Muscle.CHEST).reps)
        assertEquals(36, v.getValue(Muscle.ABS).reps)
        assertEquals(0.0, v.getValue(Muscle.ABS).kg, 0.0)
        assertEquals(Muscle.entries.toSet(), v.keys)
    }

    @Test
    fun personalRecords() {
        val s = { reps: Int, w: Double -> listOf(LoggedSet(reps, w, 12)) }
        assertEquals(
            listOf(false, true, false, true),
            Volume.personalRecords(false, listOf(s(10, 5.0), s(12, 5.0), s(10, 6.0), s(11, 6.0))),
        )
        assertEquals(listOf(false, false, true), Volume.personalRecords(true, listOf(s(10, 0.0), s(9, 0.0), s(11, 0.0))))
    }

    // ---- Set input ----

    @Test
    fun setValidation() {
        val bench = lib.exercise("bench")
        val kit = Equipment(Bench.FLAT, setOf(5.0, 8.0))
        val live = LiveTarget(5.0, 12)
        fun check(t: String) = KeywordSetParser.validate(KeywordSetParser.parse(t), bench, kit, live)

        assertEquals(SetCheck.Ok(SetRecord(12, 5.0, Effort.EASY)), check("Twelve reps, easy"))
        assertEquals(SetCheck.Ok(SetRecord(10, 8.0, Effort.HARD)), check("Ten reps at 8 kg, hard"))
        assertEquals(SetCheck.WeightNotOwned(6.0), check("10 reps at 6 kg"))
        assertEquals(SetCheck.NoReps, check("felt fine"))
        assertEquals(SetCheck.NoReps, check("40 reps"))
        val pain = check("Eight reps, sharp pain in my back") as SetCheck.Ok
        assertTrue(pain.record.pain)
        assertEquals("sharp pain in my back", pain.record.comment)

        val pushup = KeywordSetParser.validate(KeywordSetParser.parse("15 reps at 8 kg"), lib.exercise("pushup"), kit, LiveTarget(0.0, 15))
        assertNull((pushup as SetCheck.Ok).record.weightKg)
    }

    @Test
    fun niceStepAndMinutes() {
        assertEquals(50.0, Volume.niceStep(45.0), 1e-9)
        assertEquals(200.0, Volume.niceStep(120.0), 1e-9)
        assertEquals(1.0, Volume.niceStep(0.0), 1e-9)
        val day = Planner(lib, PlanRules.prototype(lib)).buildDay("A", PlanSettings(fullKit))
        assertEquals(45, planner.estimateMinutes(day) { ExerciseState(5.0) }) // 5 exercises x 3 sets: (15 * 2.5 + 8) / 5 = 9.1 -> 9 x 5
    }
}
