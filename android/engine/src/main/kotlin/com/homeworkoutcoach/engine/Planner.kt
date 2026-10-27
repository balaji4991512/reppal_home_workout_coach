package com.homeworkoutcoach.engine

/** Everything the planner needs to lay out a week. */
data class PlanSettings(
    val equipment: Equipment,
    val level: Level = Level.BEGINNER,
    val daysPerWeek: Int = 3,
    val split: Split = Split.AUTO,
    /** Custom split: muscles per training day, in the order the user ticked them. Missing days use defaults. */
    val customDays: List<List<Muscle>> = emptyList(),
    val excluded: Set<String> = emptySet(),
    /** baseId -> chosen exercise id. */
    val swaps: Map<String, String> = emptyMap(),
    /**
     * The user's own arrangement: the weekday (0 = Monday) for each session of the default sequence, in default order.
     * Null, or not a valid layout for this sequence, means the default weekdays.
     */
    val sessionWeekdays: List<Int>? = null,
) {
    init {
        require(daysPerWeek in 1..7) { "daysPerWeek must be 1..7" }
    }
}

/** One exercise in a day. [slot] is the preset slot name or the muscle label. [baseId] is the id before swaps. */
data class DayItem(val slot: String, val exerciseId: String, val baseId: String = exerciseId) {
    val swapped: Boolean get() = exerciseId != baseId
}

/** A built training day. [missing] lists slots nothing in the user's equipment covers. */
data class Day(val label: String, val items: List<DayItem>, val missing: List<String>)

class Planner(
    private val lib: Library = Library.default,
    /** Weekly sets per muscle; [WeeklyTargets.prototype] reproduces the web prototype. */
    val targets: WeeklyTargets = WeeklyTargets.PER_MUSCLE,
) {

    // ---- Weekly schedule [wkSeq, occ] ----

    /** The week's sessions in the order they fall, following the user's arrangement when there is one. */
    fun weekSequence(s: PlanSettings): List<String> {
        val base = defaultSequence(s)
        val layout = layout(s, base.size) ?: return base
        return base.indices.sortedBy { layout[it] }.map { base[it] }
    }

    /** Weekday (0 = Monday) of each session in [weekSequence], in the same order. */
    fun weekdays(s: PlanSettings): List<Int> =
        layout(s, defaultSequence(s).size)?.sorted() ?: workoutWeekdays(s.daysPerWeek)

    /** The arrangement if it is a valid one for [sessions] sessions: distinct weekdays in 0..6. */
    private fun layout(s: PlanSettings, sessions: Int): List<Int>? =
        s.sessionWeekdays?.takeIf { w -> w.size == sessions && w.distinct().size == sessions && w.all { it in 0..6 } }

    /** The plan's own order [wkSeq], before any arrangement by the user. */
    fun defaultSequence(s: PlanSettings): List<String> {
        val d = s.daysPerWeek
        fun cycle(a: List<String>) = withOccurrences(List(d) { a[it % a.size] })
        return when (s.split) {
            Split.AUTO -> lib.weekSequences.getValue(d)
            Split.FULL_BODY -> cycle(listOf("A", "B"))
            Split.UPPER_LOWER -> cycle(listOf("U1", "L1", "U2", "L2"))
            Split.PUSH_PULL_LEGS -> cycle(PPL)
            Split.BODY_PART -> cycle(BODY_PART)
            Split.CUSTOM -> withOccurrences(List(d) { i -> muscleLabel(customDay(s, i)) })
        }
    }

    /** Weekday indexes (0 = Monday) the training days fall on. */
    fun workoutWeekdays(daysPerWeek: Int): List<Int> = lib.workoutDayIndexes.getValue(daysPerWeek)

    fun customDay(s: PlanSettings, index: Int): List<Muscle> = s.customDays.getOrNull(index) ?: DEFAULT_CUSTOM[index]

    // ---- Building a day [build -> buildRaw -> applySw] ----

    fun buildWeek(s: PlanSettings): List<Day> = weekSequence(s).map { buildDay(it, s) }

    fun buildDay(label: String, s: PlanSettings): Day = applySwaps(buildRaw(label, s), s)

    private fun available(id: String, s: PlanSettings): Boolean =
        id !in s.excluded && s.equipment.canDo(lib.exercise(id))

    private fun buildRaw(label: String, s: PlanSettings): Day {
        if (label.startsWith("M:")) return buildMuscleDay(label, s)
        val items = mutableListOf<DayItem>()
        val missing = mutableListOf<String>()
        for ((slot, ids) in lib.presetPlans.getValue(label)) {
            val id = ids.filter { i -> items.none { it.exerciseId == i } }.firstOrNull { available(it, s) }
            if (id != null) items += DayItem(slot, id) else missing += slot
        }
        return Day(label, items, missing)
    }

    /** [buildM] */
    private fun buildMuscleDay(label: String, s: PlanSettings): Day {
        val parts = label.substring(2).split('#')
        val occurrence = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val muscles = parseMuscles(parts[0]).sortedBy { if (it in lib.bigMuscles) 0 else 1 }
        val cap = targets.dayCap(s.level)
        val sequence = weekSequence(s)

        val count = muscles.associateWith { m ->
            maxOf(1, jsRound(targets.sets(m, s.level).toDouble() / frequency(m, sequence) / 3))
        }.toMutableMap()
        while (count.values.sum() > cap) {
            val most = muscles.maxOf { count.getValue(it) }
            if (most <= 1) break
            val m = muscles.last { count.getValue(it) == most }
            count[m] = most - 1
        }

        val items = mutableListOf<DayItem>()
        val missing = mutableListOf<String>()
        for (m in muscles) {
            val groups = lib.musclePreferenceGroups[m].orEmpty()
            val r = if (groups.isEmpty()) 0 else occurrence % groups.size
            var got = 0
            for (group in groups.drop(r) + groups.take(r)) {
                if (got >= count.getValue(m)) break
                val id = group.firstOrNull { id -> available(id, s) && items.none { it.exerciseId == id } }
                if (id != null) {
                    items += DayItem(lib.muscleLabels.getValue(m), id)
                    got++
                }
            }
            if (got == 0) missing += lib.muscleLabels.getValue(m)
        }
        return Day(label, items.take(cap), missing)
    }

    /** Training days this week that include [m] (muscle-label days only), at least 1. [mfreq] */
    private fun frequency(m: Muscle, sequence: List<String>): Int =
        maxOf(1, sequence.count { it.startsWith("M:") && m in parseMuscles(it.substring(2).split('#')[0]) })

    private fun applySwaps(day: Day, s: PlanSettings): Day {
        val used = day.items.map { it.exerciseId }.toMutableSet()
        return day.copy(items = day.items.map { item ->
            val to = s.swaps[item.exerciseId]
            if (to != null && to in lib.exercises && available(to, s) && to !in used) {
                used += to
                DayItem(item.slot, to, baseId = item.exerciseId)
            } else {
                DayItem(item.slot, item.exerciseId)
            }
        })
    }

    /** Swap sheet options: same first primary muscle, available, not excluded, not already in the day. [swAlts] */
    fun swapAlternatives(item: DayItem, day: Day, s: PlanSettings): List<String> {
        val current = day.items.map { it.exerciseId }.toSet()
        val bases = day.items.map { it.baseId }.toSet()
        val muscle = lib.exercise(item.baseId).primary.first()
        return lib.exercises.values
            .filter { it.primary.first() == muscle && it.id !in current && it.id !in bases && available(it.id, s) }
            .map { it.id }
    }

    // ---- Names ----

    /** Display name for a day label: preset name, split day name, or a muscle list. [NAME, mname] */
    fun dayName(label: String): String {
        lib.presetNames[label]?.let { return it }
        val base = label.split('#')[0]
        SPLIT_DAY_NAMES[base]?.let { return it }
        val names = parseMuscles(base.removePrefix("M:")).map { it.short }
        if (names.isEmpty()) return "Empty day"
        val text = if (names.size > 1) names.dropLast(1).joinToString(", ") + " and " + names.last() else names[0]
        return text.replaceFirstChar { it.uppercase() }
    }

    /** Rough session length in minutes from the total sets. */
    fun estimateMinutes(day: Day, stateOf: (String) -> ExerciseState): Int {
        val sets = day.items.sumOf { stateOf(it.exerciseId).sets }
        return maxOf(15, jsRound((sets * 2.5 + 8) / 5) * 5)
    }

    companion object {
        val PPL = listOf("M:chest,shoulders,triceps", "M:lats,reardelts,biceps", "M:quads,hams,glutes,abs")
        val BODY_PART = listOf("M:chest,triceps", "M:lats,biceps", "M:quads,hams,glutes", "M:shoulders,reardelts,abs")

        val SPLIT_DAY_NAMES = mapOf(
            "M:chest,shoulders,triceps" to "Push",
            "M:lats,reardelts,biceps" to "Pull",
            "M:quads,hams,glutes,abs" to "Legs",
            "M:chest,triceps" to "Chest and triceps",
            "M:lats,biceps" to "Back and biceps",
            "M:quads,hams,glutes" to "Legs",
            "M:shoulders,reardelts,abs" to "Shoulders and abs",
        )

        val DEFAULT_CUSTOM: List<List<Muscle>> = listOf(
            listOf(Muscle.CHEST, Muscle.TRICEPS),
            listOf(Muscle.LATS, Muscle.REAR_DELTS, Muscle.BICEPS),
            listOf(Muscle.QUADS, Muscle.HAMS, Muscle.GLUTES),
            listOf(Muscle.SHOULDERS, Muscle.ABS),
            listOf(Muscle.CHEST, Muscle.LATS),
            listOf(Muscle.QUADS, Muscle.GLUTES),
            listOf(Muscle.ABS),
        )

        fun muscleLabel(muscles: List<Muscle>): String = "M:" + muscles.joinToString(",") { it.key }

        private fun parseMuscles(csv: String): List<Muscle> = csv.split(',').filter { it.isNotEmpty() }.map(Muscle::fromKey)

        /** Repeated muscle labels get "#n" so later occurrences vary their exercises. [occ] */
        fun withOccurrences(labels: List<String>): List<String> {
            val seen = mutableMapOf<String, Int>()
            return labels.map { l ->
                if (!l.startsWith("M:")) return@map l
                val n = seen[l] ?: 0
                seen[l] = n + 1
                if (n > 0) "$l#$n" else l
            }
        }
    }
}
