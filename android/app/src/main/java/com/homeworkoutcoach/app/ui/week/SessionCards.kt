package com.homeworkoutcoach.app.ui.week

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.engine.ExerciseOutcome
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Volume
import kotlin.math.roundToLong

/** The session summary card shared by This week, Workout done and History. */
object SessionCards {
    /** Workout summary card [sessCard]. */
    fun card(lib: Library, w: Workout): SessionCardUi {
        val s = Volume.summarize(lib, w.plan, w.sets, w.painIds)
        val totals = listOfNotNull(
            if (s.plannedKg > 0) "${s.liftedKg.roundToLong()} kg lifted, ${s.plannedKg.roundToLong()} kg planned" else null,
            if (s.plannedBodyweightReps > 0) "${s.bodyweightReps} reps done, ${s.plannedBodyweightReps} planned" else null,
        ).joinToString(" · ")
        return SessionCardUi(
            percent = s.percent ?: 0,
            reached = s.targetReached,
            totals = totals,
            rows = s.rows.map { r ->
                val e = lib.exercise(r.planned.exerciseId)
                val reps = r.sets.map { it.reps }
                val weights = r.sets.map { it.weightKg }.distinct().sorted()
                val did = when {
                    reps.isEmpty() -> "No sets logged"
                    e.bodyweight -> reps.joinToString(", ") + " reps"
                    weights.size < 2 -> reps.joinToString(", ") + " reps at ${Copy.kg(weights[0])} kg"
                    else -> r.sets.joinToString(", ") { "${it.reps} at ${Copy.kg(it.weightKg)} kg" }
                }
                val planned = "${r.planned.sets} × ${r.planned.targetReps}" +
                    if (e.bodyweight) "" else " at ${Copy.kg(r.planned.weightKg)} kg"
                SessionRowUi(
                    name = e.name,
                    detail = if (r.outcome == ExerciseOutcome.HIT) did else "$did · planned $planned",
                    outcome = r.outcome,
                )
            },
        )
    }
}
