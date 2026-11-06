package com.homeworkoutcoach.app.plan

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.Day
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.Exercise
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordSetParser
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LiveAdjustment
import com.homeworkoutcoach.engine.LiveTarget
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.NextSession
import com.homeworkoutcoach.engine.ParsedSet
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Progression
import com.homeworkoutcoach.engine.Ramp
import com.homeworkoutcoach.engine.SetCheck
import com.homeworkoutcoach.engine.SetRecord

/**
 * One workout in progress, one exercise at a time [start, cur, confirmSet]. Immutable: each change returns a new
 * session. [plan] lines up with the day's items and holds the prescription each exercise started with.
 *
 * Any exercise can be opened again ([goTo]) to correct a set ([editSet], [deleteSet]); an exercise can be skipped
 * ([skip]) or replaced by another for this workout ([replace]). The workout is finished once every exercise has all its
 * sets, was stopped for pain or was skipped.
 */
data class WorkoutSession(
    val lib: Library,
    val equipment: Equipment,
    val day: Day,
    val plan: List<PlannedExercise>,
    val index: Int = 0,
    val sets: Map<String, List<LoggedSet>> = emptyMap(),
    /** Live target per exercise after in-workout adjustments. */
    val live: Map<String, LiveTarget> = emptyMap(),
    val pain: List<String> = emptyList(),
    /** Exercises left out of this workout. Their plan stays as it was for next time. */
    val skipped: Set<String> = emptySet(),
    /** The last live adjustment, for the "next set will be" line. */
    val lastAdjustment: LiveAdjustment? = null,
) {
    val finished: Boolean get() = index >= day.items.size
    val exercise: Exercise get() = lib.exercise(day.items[index].exerciseId)
    val slot: String get() = day.items[index].slot
    val planned: PlannedExercise get() = plan[index]

    /** The set to log next on the exercise shown: one past those logged. */
    val setIndex: Int get() = sets[exercise.id].orEmpty().size

    /** What the current set should be: the plan for this set (a ramp starts light), overridden by any live change [cur]. */
    val target: LiveTarget
        get() = live[exercise.id] ?: LiveTarget(planned.weightOf(setIndex), planned.targetReps)

    val repsDone: List<Int> get() = sets[exercise.id].orEmpty().map { it.reps }

    /** The sets logged on the exercise shown. */
    val logged: List<LoggedSet> get() = sets[exercise.id].orEmpty()

    /** Nothing more to log on exercise [i]: all its sets are in, it was stopped for pain, or it was skipped. */
    fun complete(i: Int): Boolean {
        val id = day.items[i].exerciseId
        return id in pain || id in skipped || sets[id].orEmpty().size >= plan[i].sets
    }

    /** Whether exercise [i] was left out. */
    fun isSkipped(i: Int): Boolean = day.items[i].exerciseId in skipped

    /** Every exercise has nothing more to log, so the workout can be finished. */
    val allComplete: Boolean get() = day.items.indices.all(::complete)

    val anyLogged: Boolean get() = sets.values.any { it.isNotEmpty() }

    /** Skipping is allowed unless it would end a workout with nothing logged. */
    val canSkip: Boolean get() = !finished && (anyLogged || day.items.indices.any { it != index && !complete(it) })

    fun check(parsed: ParsedSet): SetCheck = KeywordSetParser.validate(parsed, exercise, equipment, target)

    /** Save a set: log it with the target that applied, adjust the next one, move on when the exercise is complete [confirmSet, adjust]. */
    fun save(record: SetRecord): WorkoutSession {
        check(!finished) { "Workout already finished" }
        check(!complete(index)) { "Nothing more to log on this exercise" }
        val e = exercise
        val cp = target
        val logged = LoggedSet(
            reps = record.reps,
            weightKg = if (e.bodyweight) 0.0 else record.weightKg ?: cp.weightKg,
            targetReps = cp.targetReps,
            effort = record.effort,
            comment = record.comment,
        )
        val adjustment = Progression.adjustLive(e, equipment, cp, record, planNext(setIndex))
        val next = copy(
            sets = sets + (e.id to sets[e.id].orEmpty() + logged),
            live = live + (e.id to adjustment.next),
            // Pain: nothing further for this exercise.
            pain = if (record.pain && e.id !in pain) pain + e.id else pain,
            lastAdjustment = adjustment,
        )
        return if (next.complete(index)) next.copy(index = next.nextOpen()) else next
    }

    /** On a ramp the set after [i] has its own planned weight, which a met set steps up to. */
    private fun planNext(i: Int): LiveTarget? =
        if (planned.ramped && i + 1 < planned.sets) LiveTarget(planned.weightOf(i + 1), planned.targetReps) else null

    /** The first exercise after the one shown with something left to log, then any before it; past the end if none. */
    fun nextOpen(): Int {
        val n = day.items.size
        return ((index + 1 until n) + (0 until index)).firstOrNull { !complete(it) } ?: n
    }

    /** Show exercise [i], to log on it or to correct its sets. */
    fun goTo(i: Int): WorkoutSession {
        require(i in day.items.indices) { "No exercise $i" }
        return copy(index = i, lastAdjustment = null)
    }

    /** Set [i] of the exercise shown, corrected. Its target stays the one it was logged against. */
    fun editSet(i: Int, reps: Int, weightKg: Double, effort: com.homeworkoutcoach.engine.Effort): WorkoutSession {
        val list = logged
        require(i in list.indices) { "No set $i" }
        val fixed = list[i].copy(reps = reps, weightKg = if (exercise.bodyweight) 0.0 else weightKg, effort = effort)
        return withLogged(list.toMutableList().also { it[i] = fixed })
    }

    /** Takes set [i] of the exercise shown off, so it can be done again. */
    fun deleteSet(i: Int): WorkoutSession {
        val list = logged
        require(i in list.indices) { "No set $i" }
        return withLogged(list.filterIndexed { j, _ -> j != i })
    }

    /** The exercise shown no longer counts as stopped for pain (it was tapped by mistake). */
    fun undoPain(): WorkoutSession = copy(pain = pain - exercise.id, lastAdjustment = null)

    /** Leaves the exercise shown out of this workout and moves on. */
    fun skip(): WorkoutSession {
        check(canSkip) { "Nothing would be left to log" }
        val next = copy(skipped = skipped + exercise.id, lastAdjustment = null)
        return next.copy(index = next.nextOpen())
    }

    /** Takes the exercise shown back into the workout after a skip. */
    fun unskip(): WorkoutSession = copy(skipped = skipped - exercise.id, lastAdjustment = null)

    /**
     * Does [with] in place of the exercise shown, for this workout only. Anything logged on the old one is dropped. The
     * slot keeps its base exercise, so the plan is unchanged for next time.
     */
    fun replace(with: PlannedExercise): WorkoutSession {
        val old = exercise.id
        require(with.exerciseId == old || day.items.none { it.exerciseId == with.exerciseId }) { "Already in this workout" }
        val items = day.items.toMutableList().also { it[index] = it[index].copy(exerciseId = with.exerciseId) }
        return copy(
            day = day.copy(items = items),
            plan = plan.toMutableList().also { it[index] = with },
            sets = sets - old,
            live = live - old,
            pain = pain - old,
            skipped = skipped - old,
            lastAdjustment = null,
        )
    }

    /**
     * The exercise shown with [list] as its sets, and its next-set target worked out again from them, as if they had
     * been logged that way.
     */
    private fun withLogged(list: List<LoggedSet>): WorkoutSession {
        val e = exercise
        var target = LiveTarget(planned.weightOf(0), planned.targetReps)
        var touched = false
        list.forEachIndexed { i, s ->
            val record = SetRecord(s.reps, if (e.bodyweight) null else s.weightKg, s.effort)
            target = Progression.adjustLive(e, equipment, target, record, planNext(i)).next
            touched = true
        }
        return copy(
            sets = if (list.isEmpty()) sets - e.id else sets + (e.id to list),
            live = if (touched) live + (e.id to target) else live - e.id,
            lastAdjustment = null,
        )
    }

    /** Ends the workout once every exercise is complete; the screen offers it when the last one is reached another way. */
    fun finishNow(): WorkoutSession {
        check(allComplete) { "Exercises still to do" }
        return copy(index = day.items.size)
    }

    /** Results for Workout done, the record to save and the next-session states. */
    fun finish(dateMillis: Long, madeUpForEpochDay: Long? = null): SessionResult {
        check(finished) { "Workout not finished" }
        val changes = plan.map { p ->
            val e = lib.exercise(p.exerciseId)
            val done = sets[p.exerciseId].orEmpty()
            val was = ExerciseState(p.weightKg, p.targetReps, p.sets)
            if (p.exerciseId in pain || done.isEmpty()) {
                ExerciseChange(e, done, was, next = null, outcome = null, skipped = p.exerciseId !in pain)
            } else {
                val n = Progression.nextSession(e, equipment, p, done)
                ExerciseChange(e, done, was, n.next, n)
            }
        }
        return SessionResult(
            workout = Workout(
                dateMillis = dateMillis, label = day.label, plan = plan, sets = sets, painIds = pain.toSet(),
                madeUpForEpochDay = madeUpForEpochDay,
            ),
            changes = changes,
            nextStates = changes.mapNotNull { c -> c.next?.let { c.exercise.id to it } }.toMap(),
            painIds = pain,
        )
    }

    companion object {
        fun start(lib: Library, equipment: Equipment, day: Day, stateOf: (String) -> ExerciseState) = WorkoutSession(
            lib = lib,
            equipment = equipment,
            day = day,
            plan = day.items.map { item ->
                Ramp.plan(lib.exercise(item.exerciseId), equipment, stateOf(item.exerciseId).atLeast(item.sets))
            },
        )
    }
}

/** What changes for one exercise. [next] is null when it was stopped for pain or [skipped] (nothing logged). */
data class ExerciseChange(
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    val was: ExerciseState,
    val next: ExerciseState?,
    val outcome: NextSession?,
    val skipped: Boolean = false,
)

data class SessionResult(
    val workout: Workout,
    val changes: List<ExerciseChange>,
    val nextStates: Map<String, ExerciseState>,
    val painIds: List<String>,
)
