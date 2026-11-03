package com.homeworkoutcoach.engine

import kotlin.math.abs
import kotlin.math.floor

/** The weight and rep target for the next set inside a workout. */
data class LiveTarget(val weightKg: Double, val targetReps: Int)

/** Outcome of the live per-set rule [adjust]. [next] is what the next set will be. */
sealed interface LiveAdjustment {
    val next: LiveTarget

    data class Pain(override val next: LiveTarget) : LiveAdjustment
    data class ShortOfTarget(override val next: LiveTarget) : LiveAdjustment
    data class NotEasy(override val next: LiveTarget) : LiveAdjustment
    data class AddRep(override val next: LiveTarget) : LiveAdjustment
    data class MoveUp(override val next: LiveTarget) : LiveAdjustment
    data class NothingHeavier(override val next: LiveTarget) : LiveAdjustment

    /** The set met its target and the plan is a ramp: the next set is the next step up. */
    data class StepUp(override val next: LiveTarget) : LiveAdjustment
}

/** Why the plan for next time changed. */
enum class ProgressReason {
    /** The prototype's rule: about 10% more work than last time. Bodyweight moves and workouts saved before ramps. */
    VOLUME,

    /** The top set was lifted at a heavier weight than the plan had, so that weight is the new top. */
    NEW_TOP,

    /** The top set met its target but not the top of the range: one more rep next time. */
    MORE_REPS,

    /** The top set reached the top of the range: the top moves up a dumbbell. */
    HEAVIER,

    /** The top set reached the top of the range with nothing heavier to move to: one more set. */
    EXTRA_SET,
}

/** Outcome of the between-sessions rule [progress]. [next] is the new exercise state. */
sealed interface NextSession {
    val next: ExerciseState
    val actualVolume: Double
    val plannedVolume: Double

    /** Volume fell short of the plan: same plan again. */
    data class Repeat(override val next: ExerciseState, override val actualVolume: Double, override val plannedVolume: Double) : NextSession

    /** Volume met: next time aims for about 10% more than [actualVolume]. */
    data class Progressed(
        override val next: ExerciseState,
        override val actualVolume: Double,
        override val plannedVolume: Double,
        val nextVolume: Double,
        /** Rounded percent increase over [actualVolume]. Dumbbell steps can land a few percent off 10. */
        val increasePercent: Int,
        val reason: ProgressReason = ProgressReason.VOLUME,
    ) : NextSession

    /** The work was done but the top set was short of its target reps: same plan again. */
    data class TopSetShort(override val next: ExerciseState, override val actualVolume: Double, override val plannedVolume: Double) : NextSession

    /** Volume met but no candidate beats it (no heavier dumbbell, no room for a set). */
    data class NoOption(override val next: ExerciseState, override val actualVolume: Double, override val plannedVolume: Double) : NextSession
}

/**
 * A weighted move is planned as a ramp: the last set at the top weight, each earlier one a dumbbell step lighter (and at
 * least 10% lighter, so a fine-stepped set still ramps). Starting light and building up is how the sets are planned; the
 * same reps in each. With 11.5, 13.5 and 16 kg owned among others, a top of 16 kg over three sets is 11.5, 13.5, 16.
 */
object Ramp {
    private const val MIN_DROP = 0.9
    private const val EPS = 0.001

    /** The weight of the set before one at [kg]: a step lighter, and at least 10% lighter. [kg] again when nothing is lighter. */
    fun rungBelow(equipment: Equipment, kg: Double): Double {
        val step = equipment.nextLighter(kg) ?: return kg
        if (step <= kg * MIN_DROP + EPS) return step
        return equipment.dumbbells.lastOrNull { it <= kg * MIN_DROP + EPS } ?: step
    }

    /** The weights of [sets] sets ending at [top], lightest first. */
    fun weights(equipment: Equipment, top: Double, sets: Int): List<Double> {
        require(sets >= 1) { "a ramp needs a set" }
        val down = generateSequence(top) { rungBelow(equipment, it) }.take(sets).toList()
        return down.reversed()
    }

    /** What a state is planned as for a workout: a ramp for a weighted move, every set alike for a bodyweight one. */
    fun plan(exercise: Exercise, equipment: Equipment, state: ExerciseState): PlannedExercise =
        PlannedExercise(
            exercise.id, state.sets, state.targetReps, state.weightKg,
            if (exercise.bodyweight) emptyList() else weights(equipment, state.weightKg, state.sets),
        )
}

object Progression {
    private const val EPS = 0.001
    private const val VOLUME_STEP = 1.10
    private const val HEAVIER_PENALTY = 0.02
    private const val EXTRA_SET_PENALTY = 0.08

    /**
     * Resolve an exercise's state [st]: create it from the starting weight if missing, and snap a stored weight
     * the user no longer owns down to an owned one.
     */
    fun resolveState(exercise: Exercise, stored: ExerciseState?, equipment: Equipment, level: Level): ExerciseState {
        if (exercise.bodyweight) return stored ?: ExerciseState(weightKg = 0.0)
        val state = stored ?: ExerciseState(weightKg = equipment.snapDown((exercise.startKg ?: 0.0) * level.weightFactor))
        return if (equipment.owns(state.weightKg)) state else state.copy(weightKg = equipment.snapDown(state.weightKg))
    }

    /**
     * Live adjustment after a saved set [adjust]. [current] is the target that applied to this set.
     *
     * The next set starts from what was done, not from what was planned: the weight is the weight lifted, and when the
     * set beat its target the reps it reached become the new floor, so the next set is never worth less than the one
     * just finished (15 reps when 12 were planned means the next set asks for at least 15). A set short of its target
     * leaves the target as it was.
     */
    fun adjustLive(exercise: Exercise, equipment: Equipment, current: LiveTarget, set: SetRecord, planNext: LiveTarget? = null): LiveAdjustment {
        val base = adjustWithoutRamp(exercise, equipment, current, set)
        // On a ramp a met set goes up to the next set's planned weight, one dumbbell step at a time.
        if (planNext != null && base !is LiveAdjustment.Pain && base !is LiveAdjustment.ShortOfTarget) {
            val lifted = set.weightKg ?: current.weightKg
            val step = minOf(planNext.weightKg, equipment.nextHeavier(lifted) ?: lifted)
            if (step > base.next.weightKg + EPS) return LiveAdjustment.StepUp(LiveTarget(step, planNext.targetReps))
        }
        return base
    }

    private fun adjustWithoutRamp(exercise: Exercise, equipment: Equipment, current: LiveTarget, set: SetRecord): LiveAdjustment {
        val top = exercise.topReps
        val w = set.weightKg ?: current.weightKg
        val same = LiveTarget(w, current.targetReps)
        if (set.pain) return LiveAdjustment.Pain(same)
        if (set.reps < current.targetReps) return LiveAdjustment.ShortOfTarget(same)
        // the reps done, which are at least the target here
        val done = set.reps
        val held = LiveTarget(w, done)
        if (set.effort != Effort.EASY) return LiveAdjustment.NotEasy(held)
        if (done < top) return LiveAdjustment.AddRep(LiveTarget(w, minOf(top, done + 1)))
        val heavier = if (exercise.bodyweight) null else equipment.nextHeavier(w)
        if (heavier != null) {
            // keeps the volume of the set just done: a little heavier, a few fewer reps, never less work
            val reps = minOf(done, maxOf(6, floor(done * w / heavier).toInt() + 1))
            return LiveAdjustment.MoveUp(LiveTarget(heavier, reps))
        }
        return LiveAdjustment.NothingHeavier(held)
    }

    /** Plan for the next session from what was planned and what was done [progress]. [sets] must not be empty. */
    fun nextSession(exercise: Exercise, equipment: Equipment, planned: PlannedExercise, sets: List<LoggedSet>): NextSession {
        require(sets.isNotEmpty()) { "nextSession needs at least one logged set" }
        return if (planned.ramped && !exercise.bodyweight) nextRamped(exercise, equipment, planned, sets) else nextUniform(exercise, equipment, planned, sets)
    }

    /**
     * Plan for the next session after a ramped one: the top set leads. It adds a rep until it reaches the top of the range,
     * then the top moves up a dumbbell (or a set is added when nothing is heavier). A top weight lifted beyond the plan is
     * the new top. Work short of the plan, or a top set short of its reps, repeats the plan.
     */
    private fun nextRamped(exercise: Exercise, equipment: Equipment, planned: PlannedExercise, sets: List<LoggedSet>): NextSession {
        val top = exercise.topReps
        val plannedVol = planned.volume(bodyweight = false)
        val actualVol = sets.sumOf { it.reps * it.weightKg }
        val samePlan = ExerciseState(planned.weightKg, planned.targetReps, planned.sets)
        if (actualVol < plannedVol - EPS) return NextSession.Repeat(samePlan, actualVol, plannedVol)

        val topWeight = maxOf(planned.weightKg, sets.maxOf { it.weightKg })
        val topReps = sets.filter { abs(it.weightKg - topWeight) < EPS }.maxOfOrNull { it.reps } ?: 0

        fun progressed(next: ExerciseState, why: ProgressReason): NextSession {
            val nextVol = Ramp.weights(equipment, next.weightKg, next.sets).sumOf { it * next.targetReps }
            return NextSession.Progressed(next, actualVol, plannedVol, nextVol, jsRound((nextVol / actualVol - 1) * 100), why)
        }

        if (topWeight > planned.weightKg + EPS) {
            return progressed(ExerciseState(topWeight, minOf(top, maxOf(LOW_REPS, topReps)), planned.sets), ProgressReason.NEW_TOP)
        }
        if (topReps < planned.targetReps) return NextSession.TopSetShort(samePlan, actualVol, plannedVol)
        if (topReps < top) return progressed(samePlan.copy(targetReps = planned.targetReps + 1), ProgressReason.MORE_REPS)

        val heavier = equipment.nextHeavier(topWeight)
        return when {
            heavier != null -> {
                val reps = minOf(top, maxOf(6, floor(top * topWeight / heavier).toInt() + 1))
                progressed(ExerciseState(heavier, reps, planned.sets), ProgressReason.HEAVIER)
            }
            planned.sets < MAX_SETS -> {
                // one more set, with the reps that make the ramp about 10% more work than was done (a set is a big step)
                val sets = planned.sets + 1
                val target = actualVol * VOLUME_STEP
                val rampKg = Ramp.weights(equipment, topWeight, sets).sum()
                val reps = (LOW_REPS..top).minBy { abs(rampKg * it - target) }
                progressed(ExerciseState(topWeight, reps, sets), ProgressReason.EXTRA_SET)
            }
            else -> NextSession.NoOption(samePlan, actualVol, plannedVol)
        }
    }

    /** The prototype's rule, every set planned alike: about 10% more work than last time [progress]. */
    fun nextUniform(exercise: Exercise, equipment: Equipment, planned: PlannedExercise, sets: List<LoggedSet>): NextSession {
        require(sets.isNotEmpty()) { "nextUniform needs at least one logged set" }
        val bw = exercise.bodyweight
        val top = exercise.topReps
        fun vol(n: Int, r: Int, w: Double) = n * r * (if (bw) 1.0 else w)

        val plannedVol = vol(planned.sets, planned.targetReps, planned.weightKg)
        val actualVol = sets.sumOf { vol(1, it.reps, it.weightKg) }
        val samePlan = ExerciseState(planned.weightKg, planned.targetReps, planned.sets)

        if (actualVol < plannedVol - EPS) return NextSession.Repeat(samePlan, actualVol, plannedVol)

        val base = if (bw) planned.weightKg else maxOf(planned.weightKg, sets.maxOf { it.weightKg })
        val target = actualVol * VOLUME_STEP
        val heavier = if (bw) null else equipment.nextHeavier(base)

        data class Candidate(val sets: Int, val reps: Int, val w: Double, val vol: Double, val score: Double)
        var best: Candidate? = null
        fun consider(n: Int, r: Int, w: Double, penalty: Double) {
            val v = vol(n, r, w)
            if (v <= actualVol + EPS) return
            val score = abs(v - target) / target + penalty
            if (best == null || score < best!!.score) best = Candidate(n, r, w, v, score)
        }
        for (r in LOW_REPS..top) {
            consider(planned.sets, r, base, 0.0)
            if (heavier != null) consider(planned.sets, r, heavier, HEAVIER_PENALTY)
            if (planned.sets < MAX_SETS) consider(planned.sets + 1, r, base, EXTRA_SET_PENALTY)
        }

        val b = best ?: return NextSession.NoOption(samePlan.copy(weightKg = base), actualVol, plannedVol)
        return NextSession.Progressed(
            next = ExerciseState(b.w, b.reps, b.sets),
            actualVolume = actualVol,
            plannedVolume = plannedVol,
            nextVolume = b.vol,
            increasePercent = jsRound((b.vol / actualVol - 1) * 100),
        )
    }
}
