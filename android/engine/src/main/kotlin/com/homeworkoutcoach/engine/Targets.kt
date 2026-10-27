package com.homeworkoutcoach.engine

import kotlin.math.ceil
import kotlin.math.floor

/** How many sets a week each muscle should get, per level. */
interface WeeklyTargets {
    /** Direct sets a week that muscle-label days are built towards [buildM]. */
    fun sets(m: Muscle, level: Level): Int

    /** Planned sets (secondary muscles at half) the planned volume card calls on target [volCard]. */
    fun range(m: Muscle, level: Level): SetRange

    /** Most exercises in one muscle-label day; over this, the muscle with the most gives one up. */
    fun dayCap(level: Level): Int

    companion object {
        /** The prototype's tables: one target for big and one for small muscles [TGT, volCard]. Kept for the golden tests. */
        fun prototype(lib: Library): WeeklyTargets = object : WeeklyTargets {
            private val halfRange = setOf(Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS)

            override fun sets(m: Muscle, level: Level): Int {
                val t = lib.weeklyDirectSetTargets.getValue(level)
                return if (m in lib.smallMuscles) t.small else t.big
            }

            override fun range(m: Muscle, level: Level): SetRange {
                val r = lib.weeklySetRanges.getValue(level)
                return if (m in halfRange) SetRange(r.low / 2, r.high / 2) else r
            }

            override fun dayCap(level: Level) = if (level == Level.BEGINNER) 6 else 7
        }

        /**
         * One target per muscle, a little above the prototype's. Back and the big leg and chest muscles get the most;
         * hamstrings and glutes less as they share hinges and squats; arms and rear delts less again as presses and
         * rows already work them.
         */
        val PER_MUSCLE: WeeklyTargets = object : WeeklyTargets {
            // beginner, intermediate, advanced
            private val table = mapOf(
                Muscle.CHEST to listOf(11, 15, 18),
                Muscle.LATS to listOf(12, 16, 20),
                Muscle.QUADS to listOf(11, 15, 18),
                Muscle.HAMS to listOf(9, 12, 14),
                Muscle.GLUTES to listOf(9, 12, 14),
                Muscle.SHOULDERS to listOf(9, 12, 15),
                Muscle.REAR_DELTS to listOf(6, 9, 12),
                Muscle.BICEPS to listOf(7, 10, 12),
                Muscle.TRICEPS to listOf(7, 10, 12),
                Muscle.ABS to listOf(6, 9, 12),
            )

            override fun sets(m: Muscle, level: Level): Int = table.getValue(m)[level.ordinal]

            // Planned sets also count secondary work at half, so allow some room above the direct target.
            override fun range(m: Muscle, level: Level): SetRange {
                val t = sets(m, level)
                return SetRange(floor(t * 0.7).toInt(), ceil(t * 1.6).toInt())
            }

            // One more than the prototype so the higher targets fit; about 60 minutes for a beginner.
            override fun dayCap(level: Level) = if (level == Level.BEGINNER) 7 else 8
        }
    }
}
