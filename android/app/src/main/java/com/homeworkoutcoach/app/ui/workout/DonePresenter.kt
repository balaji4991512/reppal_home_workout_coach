package com.homeworkoutcoach.app.ui.workout

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.plan.SessionResult
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.week.SessionCardUi
import com.homeworkoutcoach.app.ui.week.SessionCards
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Muscle
import com.homeworkoutcoach.engine.NextSession
import com.homeworkoutcoach.engine.Volume
import kotlin.math.roundToLong

data class DoneUi(
    /** Weighted kg total, or null when only bodyweight moves were done. */
    val totalKg: Long?,
    /** "up 8% from last time", or the first-time note. */
    val kgComparison: String?,
    /** "36 bodyweight reps, up 5% from last time" */
    val repsLine: String?,
    val muscles: List<DoneMuscleUi>,
    val card: SessionCardUi,
    val changes: List<ChangeUi>,
)

data class DoneMuscleUi(val muscle: String, val comparison: String?, val value: String)

data class ChangeUi(
    val name: String,
    /** "Reps: 12, 12, 12. Load 180 kg. Was 3 × 12 at 5 kg." */
    val summary: String,
    val message: String,
    /** "Next: 3 × 11 at 6 kg" */
    val next: String,
    val up: Boolean,
)

/** Workout done [vDone, sumCard]. [history] is every saved workout before this one, oldest first. */
class DonePresenter(private val lib: Library) {

    fun present(result: SessionResult, history: List<Workout>): DoneUi {
        val w = result.workout
        val now = Volume.summarize(lib, w.plan, w.sets, w.painIds)
        val prevWorkout = history.lastOrNull { it.label == w.label }
        val prev = prevWorkout?.let { Volume.summarize(lib, it.plan, it.sets, it.painIds) }

        val muscles = Muscle.entries.filter { now.kgByMuscle[it] != null || now.repsByMuscle[it] != null }.map { m ->
            val kg = now.kgByMuscle[m]
            val reps = now.repsByMuscle[m]
            DoneMuscleUi(
                muscle = lib.muscleLabels.getValue(m),
                comparison = kg?.let { Copy.change(it, prev?.kgByMuscle?.get(m)) },
                value = listOfNotNull(kg?.let { "${it.roundToLong()} kg" }, reps?.let { "$it reps" }).joinToString(", "),
            )
        }

        return DoneUi(
            totalKg = now.liftedKg.takeIf { it > 0 }?.roundToLong(),
            kgComparison = if (now.liftedKg > 0) {
                Copy.change(now.liftedKg, prev?.liftedKg) ?: "First time doing this session, so there is nothing to compare yet."
            } else null,
            repsLine = if (now.bodyweightReps > 0) {
                "${now.bodyweightReps} bodyweight reps" +
                    (Copy.change(now.bodyweightReps.toDouble(), prev?.bodyweightReps?.toDouble())?.let { ", $it" } ?: "")
            } else null,
            muscles = muscles,
            card = SessionCards.card(lib, w),
            changes = result.changes.map { c ->
                val e = c.exercise
                val reps = c.sets.map { it.reps }
                val load = if (e.bodyweight) "Total ${reps.sum()} reps." else "Load ${c.sets.sumOf { it.reps * it.weightKg }.roundToLong()} kg."
                val next = c.next
                ChangeUi(
                    name = e.name,
                    summary = "Reps: ${reps.joinToString(", ")}. $load Was ${Copy.prescription(e, c.was)}.",
                    message = c.outcome?.let { Copy.nextSessionNote(e, it) } ?: Copy.PAIN_NEXT,
                    next = "Next: " + if (next == null) "Swapped out next time" else Copy.prescription(e, next),
                    up = c.outcome is NextSession.Progressed,
                )
            },
        )
    }
}
