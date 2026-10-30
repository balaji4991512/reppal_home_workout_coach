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
    ) : NextSession

    /** Volume met but no candidate beats it (no heavier dumbbell, no room for a set). */
    data class NoOption(override val next: ExerciseState, override val actualVolume: Double, override val plannedVolume: Double) : NextSession
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
    fun adjustLive(exercise: Exercise, equipment: Equipment, current: LiveTarget, set: SetRecord): LiveAdjustment {
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
