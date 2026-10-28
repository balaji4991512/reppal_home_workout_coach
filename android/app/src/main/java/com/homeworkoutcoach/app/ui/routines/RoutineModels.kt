package com.homeworkoutcoach.app.ui.routines

import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.RoutineItem
import com.homeworkoutcoach.engine.Routines
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

object RoutineFormat {
    fun part(p: DayPart): String = when (p) {
        DayPart.MORNING -> "Morning"
        DayPart.AFTERNOON -> "Afternoon"
        DayPart.EVENING -> "Evening"
    }

    fun steps(n: Int): String = if (n == 1) "1 step" else "$n steps"
}

/** One routine in the list. [isNow] marks the ones for the current part of the day. */
data class RoutineRowUi(
    val id: String,
    val name: String,
    val part: String,
    val partKey: DayPart,
    val steps: String,
    val doneToday: Boolean,
    val canRun: Boolean,
    val isNow: Boolean,
)

data class RoutinesUi(
    val routines: List<RoutineRowUi>,
    /** "1 of 2 done today", or what routines are when there are none. */
    val summary: String,
    /** The starting routines not already added. */
    val suggestions: List<Routines.Suggestion>,
)

class RoutinesPresenter {
    fun present(routines: List<Routine>, done: Set<RoutineDone>, today: LocalDate, now: LocalTime): RoutinesUi {
        val part = Routines.partOf(now)
        val rows = Routines.sorted(routines).map { r ->
            RoutineRowUi(
                id = r.id,
                name = r.name,
                part = RoutineFormat.part(r.part),
                partKey = r.part,
                steps = RoutineFormat.steps(r.items.size),
                doneToday = RoutineDone(r.id, today) in done,
                canRun = r.items.isNotEmpty(),
                isNow = r.part == part,
            )
        }
        val doneCount = rows.count { it.doneToday }
        return RoutinesUi(
            routines = rows,
            summary = when {
                rows.isEmpty() -> "A routine is a checklist you run through, like a morning routine."
                doneCount == rows.size -> "All done today."
                else -> "$doneCount of ${rows.size} done today"
            },
            suggestions = Routines.suggestions.filter { s -> routines.none { it.name.equals(s.name, ignoreCase = true) } },
        )
    }
}

/** One step while running a routine. [habitName] is set when ticking it also ticks a habit. */
data class RunStepUi(val id: String, val title: String, val ticked: Boolean, val habitName: String?)

data class RunUi(
    val id: String,
    val name: String,
    val part: String,
    val steps: List<RunStepUi>,
    /** "2 of 4". */
    val progress: String,
    val canFinish: Boolean,
    val doneToday: Boolean,
)

object RunPresenter {
    fun present(routine: Routine, ticked: Set<String>, habits: Map<String, StoredHabit>, doneToday: Boolean): RunUi {
        val p = Routines.progress(routine, ticked)
        return RunUi(
            id = routine.id,
            name = routine.name,
            part = RoutineFormat.part(routine.part),
            steps = routine.items.map { RunStepUi(it.id, it.title, it.id in ticked, it.habitId?.let { h -> habits[h]?.habit?.name }) },
            progress = "${p.ticked} of ${p.total}",
            canFinish = p.complete && !doneToday,
            doneToday = doneToday,
        )
    }
}

/**
 * Running a routine: ticking steps, which also ticks any linked habit for the day, and finishing. The ticks of a run
 * are kept in a [State] by whoever shows it; only finishing is stored.
 */
class RoutineRunner(
    private val habits: HabitRepository,
    private val routines: RoutineRepository,
    private val today: () -> LocalDate,
) {
    /**
     * [ticked] steps, and for each step whose tick changed a habit, what that habit's own entry for the day was before
     * ([null] = none), so unticking the step can put it back.
     */
    data class State(val ticked: Set<String> = emptySet(), val raised: Map<String, Int?> = emptyMap())

    /** Ticks or unticks a step. [habit] is the habit its step is linked to, if it exists. */
    suspend fun toggle(state: State, routine: Routine, itemId: String, habit: Habit?): State {
        val item = routine.items.firstOrNull { it.id == itemId } ?: return state
        val day = today()
        val ticked = Routines.toggle(state.ticked, itemId)
        if (itemId in state.ticked) {
            // Unticking: undo what the tick did, unless the habit has been changed since.
            if (itemId in state.raised && habit != null && habit.id == item.habitId) {
                val now = habits.entries().first()[habit.id]?.get(day)
                if (now == habit.target) {
                    val before = state.raised.getValue(itemId)
                    if (before == null) habits.clearAmount(habit.id, day) else habits.setAmount(habit.id, day, before)
                }
            }
            return State(ticked, state.raised - itemId)
        }
        if (habit == null || habit.id != item.habitId) return State(ticked, state.raised)
        val before = habits.entries().first()[habit.id]?.get(day)
        if ((before ?: 0) >= habit.target) return State(ticked, state.raised)
        habits.setAmount(habit.id, day, habit.target)
        return State(ticked, state.raised + (itemId to before))
    }

    /** Marks the routine finished for today if every step is ticked. Returns whether it was. */
    suspend fun finish(routine: Routine, state: State): Boolean {
        if (!Routines.progress(routine, state.ticked).complete) return false
        routines.setDone(routine.id, today(), true)
        return true
    }

    /** Takes back "finished" for today. */
    suspend fun undoFinish(routine: Routine) = routines.setDone(routine.id, today(), false)
}

/** One step in the Add / Edit routine form. [habitId] links it to a habit. */
data class StepDraft(val id: String, val title: String = "", val habitId: String? = null)

/** What the Add / Edit routine form holds until it is saved. */
data class RoutineDraft(
    /** Null for a new routine. */
    val id: String? = null,
    val name: String = "",
    val part: DayPart = DayPart.MORNING,
    val steps: List<StepDraft> = emptyList(),
    val position: Int? = null,
) {
    /** The first thing to fix, or null if it can be saved. [others] are the names of the other routines. */
    fun problem(others: Collection<String>): String? = when {
        others.any { it.trim().equals(name.trim(), ignoreCase = true) } && name.isNotBlank() -> "You already have a routine with this name."
        else -> Routines.problem(name, steps.map { it.title })
    }

    fun addStep(id: String, title: String = ""): RoutineDraft = copy(steps = steps + StepDraft(id, title))

    fun removeStep(id: String): RoutineDraft = copy(steps = steps.filterNot { it.id == id })

    fun editStep(id: String, transform: (StepDraft) -> StepDraft): RoutineDraft =
        copy(steps = steps.map { if (it.id == id) transform(it) else it })

    /** Drag and drop: moves a step to a new position. */
    fun moveStep(from: Int, to: Int): RoutineDraft = copy(steps = Routines.move(steps, from, to))

    /** Only call when [problem] is null. Blank steps are dropped. A new routine goes to the end of the list. */
    fun toRoutine(newId: String, nextPosition: Int): Routine = Routine(
        id = id ?: newId,
        name = name.trim(),
        part = part,
        items = steps.filter { it.title.isNotBlank() }.map { RoutineItem(it.id, it.title.trim(), it.habitId) },
        position = position ?: nextPosition,
    )

    companion object {
        fun from(r: Routine) = RoutineDraft(
            id = r.id,
            name = r.name,
            part = r.part,
            steps = r.items.map { StepDraft(it.id, it.title, it.habitId) },
            position = r.position,
        )

        /** A new routine from a starting suggestion; [newId] makes the step ids. */
        fun from(s: Routines.Suggestion, newId: () -> String) =
            RoutineDraft(name = s.name, part = s.part, steps = s.steps.map { StepDraft(newId(), it) })
    }
}
