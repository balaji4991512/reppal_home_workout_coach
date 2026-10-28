package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.LocalTime

/** The part of the day a routine belongs to (specs/HABITS_PLANNER_SPEC.md, section 3). */
enum class DayPart { MORNING, AFTERNOON, EVENING }

/** One step of a routine. [habitId] links it to a habit: ticking the step ticks that habit for the day. */
data class RoutineItem(val id: String, val title: String, val habitId: String? = null) {
    init {
        require(title.isNotBlank()) { "a step needs a title" }
    }
}

/** A named, ordered checklist such as Morning or Evening. */
data class Routine(
    val id: String,
    val name: String,
    val part: DayPart,
    val items: List<RoutineItem> = emptyList(),
    /** Position in the routines list, lowest first. */
    val position: Int = 0,
) {
    init {
        require(name.isNotBlank()) { "a routine needs a name" }
        require(items.map { it.id }.toSet().size == items.size) { "steps need different ids" }
    }
}

/** A routine finished on a calendar day. */
data class RoutineDone(val routineId: String, val date: LocalDate)

/** How far a run of a routine has got: [ticked] of [total] steps. */
data class RunProgress(val ticked: Int, val total: Int) {
    /** A routine can only be finished when every step is ticked (and it has any). */
    val complete: Boolean get() = total > 0 && ticked == total
}

object Routines {
    const val MAX_NAME = 40
    const val MAX_STEP = 80
    const val MAX_STEPS = 30

    /** 4 am to noon is morning, noon to 5 pm afternoon, and the rest (5 pm to 4 am) evening. */
    fun partOf(time: LocalTime): DayPart = when (time.hour) {
        in 4..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        else -> DayPart.EVENING
    }

    /** The part of the day a self-ticking habit follows: the Morning routine habit follows Morning, Night follows Evening. */
    fun partOf(auto: AutoSource): DayPart? = when (auto) {
        AutoSource.MORNING_ROUTINE -> DayPart.MORNING
        AutoSource.NIGHT_ROUTINE -> DayPart.EVENING
        AutoSource.WORKOUT, AutoSource.JOURNAL, AutoSource.NONE -> null
    }

    val order: Comparator<Routine> = compareBy<Routine>({ it.position }, { it.name.lowercase() }, { it.id })

    fun sorted(all: List<Routine>): List<Routine> = all.sortedWith(order)

    /** The routines for a part of the day, in list order. */
    fun forPart(all: List<Routine>, part: DayPart): List<Routine> = all.filter { it.part == part }.sortedWith(order)

    /** Steps of [routine] that are in [ticked]; ids that belong to other routines do not count. */
    fun progress(routine: Routine, ticked: Set<String>): RunProgress =
        RunProgress(routine.items.count { it.id in ticked }, routine.items.size)

    /** Ticks a step, or unticks it if it was ticked. Steps can be done in any order. */
    fun toggle(ticked: Set<String>, itemId: String): Set<String> = if (itemId in ticked) ticked - itemId else ticked + itemId

    /** Moves the step at [from] to position [to] (drag and drop). Out-of-range positions leave the list as it was. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /**
     * The days the self-ticking habit for [part] counts as done: days on which every routine of that part was
     * finished. Routines with no steps can't be finished and are left out; with no such routines there are no days.
     */
    fun autoDays(routines: List<Routine>, done: Collection<RoutineDone>, part: DayPart): Set<LocalDate> {
        val ofPart = routines.filter { it.part == part && it.items.isNotEmpty() }
        if (ofPart.isEmpty()) return emptySet()
        val byRoutine = done.groupBy({ it.routineId }, { it.date }).mapValues { it.value.toSet() }
        return ofPart.map { byRoutine[it.id].orEmpty() }.reduce { a, b -> a intersect b }
    }

    /** The first thing to fix in what was typed, or null if the routine can be saved. */
    fun problem(name: String, steps: List<String>): String? {
        val kept = steps.map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            name.isBlank() -> "Give the routine a name."
            name.trim().length > MAX_NAME -> "Keep the name to $MAX_NAME characters."
            kept.isEmpty() -> "Add at least one step."
            kept.size > MAX_STEPS -> "A routine can have up to $MAX_STEPS steps."
            kept.any { it.length > MAX_STEP } -> "Keep each step to $MAX_STEP characters."
            else -> null
        }
    }

    data class Suggestion(val name: String, val part: DayPart, val steps: List<String>)

    /** The starting routines; the user edits them. */
    val suggestions: List<Suggestion> = listOf(
        Suggestion("Morning", DayPart.MORNING, listOf("Drink a glass of water", "Stretch", "Wash up", "Plan the day")),
        Suggestion("Evening", DayPart.EVENING, listOf("Tidy up", "Plan tomorrow", "Wind down", "Lights out")),
    )
}
