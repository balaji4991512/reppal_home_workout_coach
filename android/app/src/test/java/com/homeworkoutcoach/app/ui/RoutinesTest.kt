package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.routine
import com.homeworkoutcoach.app.data.storedHabit
import com.homeworkoutcoach.app.ui.routines.RoutineDraft
import com.homeworkoutcoach.app.ui.routines.RoutineRunner
import com.homeworkoutcoach.app.ui.routines.RoutinesPresenter
import com.homeworkoutcoach.app.ui.routines.RunPresenter
import com.homeworkoutcoach.app.ui.routines.StepDraft
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.RoutineItem
import com.homeworkoutcoach.engine.Routines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private val TODAY = LocalDate.of(2026, 10, 28)

class RoutinesPresenterTest {
    private val presenter = RoutinesPresenter()
    private val morningTime = LocalTime.of(7, 30)

    @Test
    fun theListMarksTheRoutinesForNowAndWhatIsDoneToday() {
        val morning = routine("m", "Morning", DayPart.MORNING, steps = 4, position = 0)
        val evening = routine("e", "Evening", DayPart.EVENING, steps = 1, position = 1)
        val done = setOf(RoutineDone("m", TODAY), RoutineDone("e", TODAY.minusDays(1)))
        val ui = presenter.present(listOf(evening, morning), done, TODAY, morningTime)
        assertEquals(listOf("m", "e"), ui.routines.map { it.id })
        assertEquals(listOf("Morning", "Evening"), ui.routines.map { it.part })
        assertEquals(listOf("4 steps", "1 step"), ui.routines.map { it.steps })
        assertEquals(listOf(true, false), ui.routines.map { it.doneToday })
        assertEquals(listOf(true, false), ui.routines.map { it.isNow })
        assertEquals("1 of 2 done today", ui.summary)
        assertTrue(ui.suggestions.isEmpty()) // Morning and Evening are both there
    }

    @Test
    fun theSummaryAndSuggestions() {
        val none = presenter.present(emptyList(), emptySet(), TODAY, morningTime)
        assertEquals("A routine is a checklist you run through, like a morning routine.", none.summary)
        assertEquals(listOf("Morning", "Evening"), none.suggestions.map { it.name })
        val all = presenter.present(listOf(routine("m", "morning")), setOf(RoutineDone("m", TODAY)), TODAY, morningTime)
        assertEquals("All done today.", all.summary)
        assertEquals(listOf("Evening"), all.suggestions.map { it.name }) // matched without regard to case
    }

    @Test
    fun anotherDayShowsWhatWasDoneThenAndTheWeeksNumbers() {
        val morning = routine("m", "Morning", DayPart.MORNING, steps = 3, position = 0)
        val evening = routine("e", "Evening", DayPart.EVENING, steps = 2, position = 1)
        val mon = TODAY.minusDays(2)
        val tue = TODAY.minusDays(1)
        val done = setOf(RoutineDone("m", mon), RoutineDone("e", mon), RoutineDone("m", tue), RoutineDone("m", TODAY))

        val ui = presenter.present(listOf(morning, evening), done, TODAY, morningTime, shown = tue)
        assertEquals("Yesterday", ui.title)
        assertFalse(ui.isToday)
        assertEquals(listOf(true, false), ui.routines.map { it.doneToday })
        assertEquals("1 of 2 done", ui.summary)
        assertEquals(listOf(false, false), ui.routines.map { it.isNow }) // "now" is only for today
        assertEquals(mapOf(mon to "2/2", tue to "1/2", TODAY to "1/2"), ui.badges.mapValues { it.value.text })
        assertTrue(ui.badges.getValue(mon).complete)
        assertFalse(ui.badges.getValue(tue).complete)
    }

    @Test
    fun aDayAheadIsMarkedFutureAndHasNoNumbers() {
        val ui = presenter.present(listOf(routine("m", "Morning")), emptySet(), TODAY, morningTime, shown = TODAY.plusDays(2))
        assertTrue(ui.isFuture)
        assertEquals("1 routine planned", ui.summary)
        assertTrue(ui.badges.keys.none { it.isAfter(TODAY) })
    }

    @Test
    fun noRoutinesMeansNoNumbers() {
        assertTrue(presenter.present(emptyList(), emptySet(), TODAY, morningTime, shown = TODAY.minusDays(1)).badges.isEmpty())
    }

    @Test
    fun aRoutineWithNoStepsCannotBeRun() {
        assertFalse(presenter.present(listOf(routine("m", steps = 0)), emptySet(), TODAY, morningTime).routines.single().canRun)
    }

    @Test
    fun theRunViewShowsProgressAndLinkedHabits() {
        val r = routine("m", "Morning", steps = 3, linkedTo = "water")
        val habits = mapOf("water" to storedHabit("water", name = "Water"))
        val ui = RunPresenter.present(r, setOf("m-0"), habits, doneToday = false)
        assertEquals("1 of 3", ui.progress)
        assertFalse(ui.canFinish)
        assertEquals(listOf(true, false, false), ui.steps.map { it.ticked })
        assertEquals(listOf("Water", null, null), ui.steps.map { it.habitName })
        assertTrue(RunPresenter.present(r, setOf("m-0", "m-1", "m-2"), habits, false).canFinish)
        // already finished today: nothing more to finish
        assertFalse(RunPresenter.present(r, setOf("m-0", "m-1", "m-2"), habits, true).canFinish)
        // a link to a habit that is gone shows nothing
        assertNull(RunPresenter.present(r, emptySet(), emptyMap(), false).steps.first().habitName)
    }
}

class RoutineRunnerTest {
    private val habits = InMemoryHabitRepository()
    private val routines = InMemoryRoutineRepository()
    private val runner = RoutineRunner(habits, routines, today = { TODAY })

    private val water = storedHabit("water", name = "Water", kind = HabitKind.COUNT, target = 8)
    private val journal = storedHabit("journal", name = "Journal")
    private val r: Routine = Routine(
        "m", "Morning", DayPart.MORNING,
        listOf(RoutineItem("a", "Drink water", "water"), RoutineItem("b", "Stretch"), RoutineItem("c", "Journal", "journal")),
    )

    private suspend fun entry(id: String) = habits.entries().first()[id]?.get(TODAY)

    private suspend fun setup() {
        habits.save(water)
        habits.save(journal)
        routines.save(r)
    }

    @Test
    fun tickingAStepTicksTheLinkedHabitForToday() = runTest {
        setup()
        var s = RoutineRunner.State()
        s = runner.toggle(s, r, "a", water.habit)
        assertEquals(setOf("a"), s.ticked)
        assertEquals(8, entry("water")) // the full target
        s = runner.toggle(s, r, "b", null) // a step with no habit changes nothing else
        assertEquals(setOf("a", "b"), s.ticked)
        assertNull(entry("journal"))
    }

    @Test
    fun unTickingPutsTheHabitBackToWhatItWas() = runTest {
        setup()
        habits.setAmount("water", TODAY, 3)
        var s = runner.toggle(RoutineRunner.State(), r, "a", water.habit)
        assertEquals(8, entry("water"))
        s = runner.toggle(s, r, "a", water.habit)
        assertEquals(3, entry("water"))
        assertTrue(s.ticked.isEmpty())
        // a habit with no entry before goes back to none
        s = runner.toggle(RoutineRunner.State(), r, "c", journal.habit)
        assertEquals(1, entry("journal"))
        runner.toggle(s, r, "c", journal.habit)
        assertNull(entry("journal"))
    }

    @Test
    fun aHabitAlreadyAtItsTargetIsLeftAlone() = runTest {
        setup()
        habits.setAmount("water", TODAY, 10)
        var s = runner.toggle(RoutineRunner.State(), r, "a", water.habit)
        assertEquals(10, entry("water"))
        s = runner.toggle(s, r, "a", water.habit)
        assertEquals(10, entry("water")) // and unticking leaves it alone too
    }

    @Test
    fun unTickingKeepsAChangeMadeInTheMeantime() = runTest {
        setup()
        var s = runner.toggle(RoutineRunner.State(), r, "a", water.habit)
        habits.setAmount("water", TODAY, 12) // changed on the Habits screen
        s = runner.toggle(s, r, "a", water.habit)
        assertEquals(12, entry("water"))
    }

    @Test
    fun aStepLinkedToAHabitThatIsGoneJustTicks() = runTest {
        setup()
        val s = runner.toggle(RoutineRunner.State(), r, "a", null)
        assertEquals(setOf("a"), s.ticked)
        assertNull(entry("water"))
        // an unknown step is ignored
        assertEquals(s, runner.toggle(s, r, "nope", null))
    }

    @Test
    fun aRoutineCanOnlyBeFinishedWhenEveryStepIsTicked() = runTest {
        setup()
        var s = RoutineRunner.State()
        s = runner.toggle(s, r, "a", water.habit)
        s = runner.toggle(s, r, "b", null)
        assertFalse(runner.finish(r, s))
        assertTrue(routines.done().first().isEmpty())
        s = runner.toggle(s, r, "c", journal.habit)
        assertTrue(runner.finish(r, s))
        assertEquals(setOf(RoutineDone("m", TODAY)), routines.done().first())
        runner.undoFinish(r)
        assertTrue(routines.done().first().isEmpty())
    }
}

class RoutineDraftTest {
    private var n = 0
    private fun id() = "s${n++}"
    private val none = emptyList<String>()

    @Test
    fun aNewDraftNeedsANameAndAStep() {
        assertEquals("Give the routine a name.", RoutineDraft().problem(none))
        assertEquals("Add at least one step.", RoutineDraft(name = "Morning").problem(none))
        assertEquals("Add at least one step.", RoutineDraft(name = "Morning").addStep("a", "  ").problem(none))
        assertNull(RoutineDraft(name = "Morning").addStep("a", "Stretch").problem(none))
    }

    @Test
    fun namesCannotRepeat() {
        val d = RoutineDraft(name = " morning ").addStep("a", "Stretch")
        assertEquals("You already have a routine with this name.", d.problem(listOf("Morning")))
        assertNull(d.problem(listOf("Evening")))
    }

    @Test
    fun stepsCanBeAddedEditedLinkedRemovedAndMoved() {
        var d = RoutineDraft(name = "M").addStep("a", "One").addStep("b", "Two").addStep("c", "Three")
        d = d.editStep("b") { it.copy(title = "Second", habitId = "water") }
        assertEquals(StepDraft("b", "Second", "water"), d.steps[1])
        d = d.moveStep(2, 0)
        assertEquals(listOf("c", "a", "b"), d.steps.map { it.id })
        d = d.removeStep("a")
        assertEquals(listOf("c", "b"), d.steps.map { it.id })
        assertEquals(d, d.moveStep(0, 5)) // out of range: unchanged
    }

    @Test
    fun savingDropsBlankStepsKeepsOrderAndIdsAndLinks() {
        val d = RoutineDraft(name = " Morning ", part = DayPart.AFTERNOON)
            .addStep("a", " Stretch ").addStep("blank", " ").addStep("b", "Water").editStep("b") { it.copy(habitId = "water") }
        val r = d.toRoutine("new-id", nextPosition = 3)
        assertEquals(Routine("new-id", "Morning", DayPart.AFTERNOON, listOf(RoutineItem("a", "Stretch"), RoutineItem("b", "Water", "water")), 3), r)
    }

    @Test
    fun editingKeepsTheIdAndPositionAndRoundTrips() {
        val original = routine("m", "Morning", DayPart.EVENING, steps = 3, position = 2, linkedTo = "water")
        val d = RoutineDraft.from(original)
        assertEquals(original, d.toRoutine("ignored", nextPosition = 9))
        assertEquals("m", d.copy(name = "Renamed").toRoutine("ignored", 9).id)
    }

    @Test
    fun everySuggestionMakesAValidDraft() {
        Routines.suggestions.forEach { s ->
            val d = RoutineDraft.from(s, ::id)
            assertNull(s.name, d.problem(none))
            assertEquals(s.steps, d.toRoutine("x", 0).items.map { it.title })
            assertEquals(s.part, d.part)
        }
        // each step gets its own id
        val ids = RoutineDraft.from(Routines.suggestions.first(), ::id).steps.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
