package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.engine.Exercise
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.LiveAdjustment
import com.homeworkoutcoach.engine.LiveTarget
import com.homeworkoutcoach.engine.NextSession
import com.homeworkoutcoach.engine.Split
import kotlin.math.roundToLong

/** Wording and number formats shared by screens. Text follows the prototype. */
object Copy {
    /** "5" for whole kilos, "2.5" otherwise. */
    fun kg(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

    /** "3 × 12 at 5 kg", or "3 × 12" for bodyweight. [presc] */
    fun prescription(e: Exercise, s: ExerciseState): String =
        "${s.sets} × ${s.targetReps}" + if (e.bodyweight) "" else " at ${kg(s.weightKg)} kg"

    fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

    /** One line after each set saying what the next set will be [adjust]. Null after a pain report. */
    fun liveNote(e: Exercise, cp: LiveTarget, a: LiveAdjustment): String? {
        val u = if (e.bodyweight) "" else "${kg(a.next.weightKg)} kg, "
        return when (a) {
            is LiveAdjustment.Pain -> null
            is LiveAdjustment.ShortOfTarget -> "Short of ${cp.targetReps}, so the next set stays at $u${cp.targetReps} reps."
            is LiveAdjustment.NotEasy -> "Next set stays at $u${cp.targetReps} reps."
            is LiveAdjustment.AddRep -> "Easy, so the next set goes up to ${a.next.targetReps} reps" +
                (if (e.bodyweight) "" else " at ${kg(a.next.weightKg)} kg") + "."
            is LiveAdjustment.MoveUp -> "Easy at the top of the range, so the next set moves up to ${kg(a.next.weightKg)} kg for ${a.next.targetReps} reps."
            is LiveAdjustment.NothingHeavier -> "Easy, but there is nothing heavier to move up to, so the next set stays at $u${cp.targetReps} reps."
        }
    }

    /** Why the next session changes, or not [progress]. */
    fun nextSessionNote(e: Exercise, n: NextSession): String {
        val u = if (e.bodyweight) "reps" else "kg"
        val got = n.actualVolume.roundToLong()
        val exp = n.plannedVolume.roundToLong()
        return when (n) {
            is NextSession.Repeat -> "Volume was $got of $exp $u, short of the target, so the same plan again."
            is NextSession.Progressed -> "Volume was $got $u" +
                (if (n.actualVolume > n.plannedVolume + 0.001) " (plan was $exp)" else "") +
                ", so next time aims for about 10% more than that: ${n.nextVolume.roundToLong()} $u (+${n.increasePercent}%)."
            is NextSession.NoOption -> "Volume target met at $got $u, but there is no heavier option or room for another set, so the same plan again."
        }
    }

    const val PAIN_NEXT = "You reported pain, so this exercise is removed from your plan. Check with your doctor or physio before trying it again."

    /** "up 12% from last time", or null without an earlier figure. */
    fun change(now: Double, before: Double?): String? {
        if (before == null || before == 0.0) return null
        val pct = kotlin.math.abs(kotlin.math.floor((now - before) / before * 100 + 0.5).toLong())
        return "${if (now >= before) "up" else "down"} $pct% from last time"
    }

    fun levelName(l: Level) = when (l) {
        Level.BEGINNER -> "Beginner"
        Level.INTERMEDIATE -> "Intermediate"
        Level.ADVANCED -> "Advanced"
    }

    /** "Your training so far" options and the level each one means. */
    val EXPERIENCE = listOf(
        "New to lifting, or back after a long break" to Level.BEGINNER,
        "Under 6 months of steady training" to Level.BEGINNER,
        "6 months to 2 years" to Level.INTERMEDIATE,
        "More than 2 years" to Level.ADVANCED,
    )

    val SPLITS = listOf(
        Split.AUTO to "Auto",
        Split.FULL_BODY to "Full body",
        Split.UPPER_LOWER to "Upper / lower",
        Split.PUSH_PULL_LEGS to "Push / pull / legs",
        Split.BODY_PART to "Body-part",
        Split.CUSTOM to "Choose my own",
    )

    fun splitNote(split: Split, days: Int): String = when (split) {
        Split.AUTO -> "Recommended for beginners. " + DAYS_NOTE.getValue(days)
        Split.FULL_BODY -> "Every session trains the whole body. Works well for 2 to 3 days a week."
        Split.UPPER_LOWER -> "Alternates upper body and lower body sessions."
        Split.PUSH_PULL_LEGS -> "Push (chest, shoulders, triceps), pull (back, rear delts, biceps) and legs. Fits 3 or 6 days a week best."
        Split.BODY_PART -> "One or two muscle groups a day: chest and triceps, back and biceps, legs, then shoulders and abs."
        Split.CUSTOM -> "Choose the muscles for each training day. The plan picks exercises from your equipment."
    }

    private val DAYS_NOTE = mapOf(
        1 to "Once a week: one full body session.",
        2 to "Full body, alternating A and B.",
        3 to "Full body, alternating A and B. A good fit for most beginners.",
        4 to "Upper/lower split. Each half of the body twice a week.",
        5 to "Upper/lower split plus an arms, shoulders and core day.",
        6 to "Upper/lower split plus two lighter days for arms, shoulders, glutes and core. A lot for a beginner.",
        7 to "No full rest day. Beginners usually do better with 3 or 4 days, so treat the extra days as optional.",
    )
}
