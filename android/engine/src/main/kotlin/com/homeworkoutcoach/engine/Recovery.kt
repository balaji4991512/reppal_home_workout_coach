package com.homeworkoutcoach.engine

/**
 * Rest between sessions. A muscle worked directly (as a primary muscle) on two days in a row gets under 48 hours
 * to recover. Abs are left out: they recover fast and most plans train core often.
 */
object Recovery {
    /** [muscle] is trained on [firstDay] and again on [secondDay], the next day (0 = Monday; Sunday wraps to Monday). */
    data class Clash(val muscle: Muscle, val firstDay: Int, val secondDay: Int)

    private val EXEMPT = setOf(Muscle.ABS)

    fun clashes(lib: Library, planner: Planner, s: PlanSettings): List<Clash> {
        val seq = planner.weekSequence(s)
        val days = planner.weekdays(s)
        if (seq.size < 2) return emptyList()
        val muscles = seq.map { label ->
            planner.buildDay(label, s).items.flatMap { lib.exercise(it.exerciseId).primary }.toSet() - EXEMPT
        }
        val out = mutableListOf<Clash>()
        for (i in seq.indices) {
            val j = (i + 1) % seq.size
            val gap = (days[j] - days[i] + 7) % 7
            if (gap != 1) continue
            // Muscle order follows the enum so messages read the same every time.
            Muscle.entries.filter { it in muscles[i] && it in muscles[j] }.forEach { out += Clash(it, days[i], days[j]) }
        }
        return out
    }

    /** Clashes in [s] that the plan's default arrangement doesn't already have; these are the ones to prevent. */
    fun newClashes(lib: Library, planner: Planner, s: PlanSettings): List<Clash> {
        // Compared per muscle (how many back-to-back pairs), since the arrangement moves the weekdays around.
        val allowed = clashes(lib, planner, s.copy(sessionWeekdays = null)).groupingBy { it.muscle }.eachCount()
        val seen = mutableMapOf<Muscle, Int>()
        return clashes(lib, planner, s).filter { c -> seen.merge(c.muscle, 1, Int::plus)!! > (allowed[c.muscle] ?: 0) }
    }
}
