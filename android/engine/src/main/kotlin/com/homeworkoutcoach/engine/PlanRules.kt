package com.homeworkoutcoach.engine

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The numbers the planner builds towards: weekly sets per muscle, exercises per day, sets per exercise and the
 * preset days' slots. [prototype] reproduces the web prototype; [CURRENT] is what the app uses.
 */
interface PlanRules {
    /** Direct sets a week that muscle-label days are built towards [buildM]. */
    fun sets(m: Muscle, level: Level): Int

    /** Planned sets (secondary muscles at half) the planned volume card calls on target [volCard]. */
    fun range(m: Muscle, level: Level): SetRange

    /** Most exercises in one muscle-label day; over this, the muscle with the most gives one up. */
    fun dayCap(level: Level): Int

    /** Sets each exercise starts with on day [label] when its main muscle is [m]. */
    fun setsPerExercise(label: String, m: Muscle): Int

    /** Slots of preset day [label] (slot -> candidate ids, in order). */
    fun presetSlots(lib: Library, label: String): Map<String, List<String>>

    companion object {
        /** Full body days: the presets that train the whole body in one session. */
        val FULL_BODY = setOf("A", "B", "F")

        /** The prototype's tables: one target for big and one for small muscles [TGT, volCard]. Kept for the golden tests. */
        fun prototype(lib: Library): PlanRules = object : PlanRules {
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

            override fun setsPerExercise(label: String, m: Muscle) = DEFAULT_SETS

            override fun presetSlots(lib: Library, label: String) = lib.presetPlans.getValue(label)
        }

        /**
         * One target per muscle, a little above the prototype's. Back and the big leg and chest muscles get the most;
         * hamstrings and glutes less as they share hinges and squats; arms and rear delts less again as presses and
         * rows already work them. Split days keep to 5 exercises and give the big lifts 4 sets to reach the targets;
         * full body days carry 6 to 7 exercises at 3 sets.
         */
        val CURRENT: PlanRules = object : PlanRules {
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
            private val bigLifts = setOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS, Muscle.HAMS, Muscle.GLUTES, Muscle.SHOULDERS)

            /** Full body A and B gain slots borrowed from the other presets: (day, slot, preset to borrow from). */
            private val extraSlots = listOf(
                Triple("A", "Shoulders", "B"),
                Triple("A", "Biceps", "U1"),
                Triple("B", "Triceps", "U2"),
            )

            override fun sets(m: Muscle, level: Level): Int = table.getValue(m)[level.ordinal]

            // Planned sets also count secondary work at half, so allow some room above the direct target.
            override fun range(m: Muscle, level: Level): SetRange {
                val t = sets(m, level)
                return SetRange(floor(t * 0.7).toInt(), ceil(t * 1.6).toInt())
            }

            override fun dayCap(level: Level) = SPLIT_DAY_CAP

            override fun setsPerExercise(label: String, m: Muscle) =
                if (label !in FULL_BODY && m in bigLifts) MAX_SETS else DEFAULT_SETS

            override fun presetSlots(lib: Library, label: String): Map<String, List<String>> {
                val base = lib.presetPlans.getValue(label)
                if (label !in FULL_BODY) return base.entries.take(SPLIT_DAY_CAP).associate { it.key to it.value }
                return base + extraSlots.filter { it.first == label }.associate { (_, slot, from) ->
                    slot to lib.presetPlans.getValue(from).getValue(slot)
                }
            }
        }

        private const val SPLIT_DAY_CAP = 5
    }
}
