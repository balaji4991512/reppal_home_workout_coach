package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.engine.Exercise
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.Split

/** Wording and number formats shared by screens. Text follows the prototype. */
object Copy {
    /** "5" for whole kilos, "2.5" otherwise. */
    fun kg(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

    /** "3 × 12 at 5 kg", or "3 × 12" for bodyweight. [presc] */
    fun prescription(e: Exercise, s: ExerciseState): String =
        "${s.sets} × ${s.targetReps}" + if (e.bodyweight) "" else " at ${kg(s.weightKg)} kg"

    fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

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
