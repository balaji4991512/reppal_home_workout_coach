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
import com.homeworkoutcoach.engine.SetCheck
import com.homeworkoutcoach.engine.SetRecord

/**
 * One workout in progress, one exercise at a time [start, cur, confirmSet]. Immutable: each saved set returns a new
 * session. [plan] lines up with the day's items and holds the prescription each exercise started with.
 */
data class WorkoutSession(
    val lib: Library,
    val equipment: Equipment,
    val day: Day,
    val plan: List<PlannedExercise>,
    val index: Int = 0,
    val setIndex: Int = 0,
    val sets: Map<String, List<LoggedSet>> = emptyMap(),
    /** Live target per exercise after in-workout adjustments. */
    val live: Map<String, LiveTarget> = emptyMap(),
    val pain: List<String> = emptyList(),
    /** The last live adjustment, for the "next set will be" line. */
    val lastAdjustment: LiveAdjustment? = null,
) {
    val finished: Boolean get() = index >= day.items.size
    val exercise: Exercise get() = lib.exercise(day.items[index].exerciseId)
    val slot: String get() = day.items[index].slot
    val planned: PlannedExercise get() = plan[index]

    /** What the current set should be: the plan, overridden by any live change [cur]. */
    val target: LiveTarget
        get() = live[exercise.id] ?: LiveTarget(planned.weightKg, planned.targetReps)

    val repsDone: List<Int> get() = sets[exercise.id].orEmpty().map { it.reps }

    fun check(parsed: ParsedSet): SetCheck = KeywordSetParser.validate(parsed, exercise, equipment, target)

    /** Save a set: log it with the target that applied, adjust the next one, move on [confirmSet, adjust]. */
    fun save(record: SetRecord): WorkoutSession {
        check(!finished) { "Workout already finished" }
        val e = exercise
        val cp = target
        val logged = LoggedSet(
            reps = record.reps,
            weightKg = if (e.bodyweight) 0.0 else record.weightKg ?: cp.weightKg,
            targetReps = cp.targetReps,
            effort = record.effort,
            comment = record.comment,
        )
        val adjustment = Progression.adjustLive(e, equipment, cp, record)
        // Pain: nothing further for this exercise.
        var nextSet = if (record.pain) planned.sets else setIndex + 1
        var nextIndex = index
        if (nextSet >= planned.sets) {
            nextSet = 0
            nextIndex++
        }
        return copy(
            index = nextIndex,
            setIndex = nextSet,
            sets = sets + (e.id to sets[e.id].orEmpty() + logged),
            live = live + (e.id to adjustment.next),
            pain = if (record.pain && e.id !in pain) pain + e.id else pain,
            lastAdjustment = adjustment,
        )
    }

    /** Results for Workout done, the record to save and the next-session states. */
    fun finish(dateMillis: Long): SessionResult {
        check(finished) { "Workout not finished" }
        val changes = plan.map { p ->
            val e = lib.exercise(p.exerciseId)
            val done = sets[p.exerciseId].orEmpty()
            val was = ExerciseState(p.weightKg, p.targetReps, p.sets)
            if (p.exerciseId in pain || done.isEmpty()) {
                ExerciseChange(e, done, was, next = null, outcome = null)
            } else {
                val n = Progression.nextSession(e, equipment, p, done)
                ExerciseChange(e, done, was, n.next, n)
            }
        }
        return SessionResult(
            workout = Workout(dateMillis = dateMillis, label = day.label, plan = plan, sets = sets, painIds = pain.toSet()),
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
                val s = stateOf(item.exerciseId).atLeast(item.sets)
                PlannedExercise(item.exerciseId, s.sets, s.targetReps, s.weightKg)
            },
        )
    }
}

/** What changes for one exercise. [next] is null when it was stopped for pain. */
data class ExerciseChange(
    val exercise: Exercise,
    val sets: List<LoggedSet>,
    val was: ExerciseState,
    val next: ExerciseState?,
    val outcome: NextSession?,
)

data class SessionResult(
    val workout: Workout,
    val changes: List<ExerciseChange>,
    val nextStates: Map<String, ExerciseState>,
    val painIds: List<String>,
)
