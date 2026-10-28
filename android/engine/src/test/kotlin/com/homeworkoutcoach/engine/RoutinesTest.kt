package com.homeworkoutcoach.engine

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Routine rules from specs/HABITS_PLANNER_SPEC.md, section 3. */
class RoutinesTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong())

    private fun routine(
        id: String,
        part: DayPart = DayPart.MORNING,
        steps: Int = 2,
        position: Int = 0,
        name: String = id,
    ) = Routine(id, name, part, List(steps) { RoutineItem("$id-$it", "Step $it") }, position)

    // ---- Parts of the day ----

    @Test
    fun partsOfTheDay() {
        fun p(h: Int, m: Int = 0) = Routines.partOf(LocalTime.of(h, m))
        assertEquals(DayPart.EVENING, p(0, 0))
        assertEquals(DayPart.EVENING, p(3, 59))
        assertEquals(DayPart.MORNING, p(4, 0))
        assertEquals(DayPart.MORNING, p(11, 59))
        assertEquals(DayPart.AFTERNOON, p(12, 0))
        assertEquals(DayPart.AFTERNOON, p(16, 59))
        assertEquals(DayPart.EVENING, p(17, 0))
        assertEquals(DayPart.EVENING, p(23, 59))
    }

    @Test
    fun theSelfTickingHabitsFollowMorningAndEvening() {
        assertEquals(DayPart.MORNING, Routines.partOf(AutoSource.MORNING_ROUTINE))
        assertEquals(DayPart.EVENING, Routines.partOf(AutoSource.NIGHT_ROUTINE))
        assertNull(Routines.partOf(AutoSource.WORKOUT))
        assertNull(Routines.partOf(AutoSource.JOURNAL))
        assertNull(Routines.partOf(AutoSource.NONE))
    }

    // ---- The list ----

    @Test
    fun routinesAreListedByPositionThenNameAndFilteredByPart() {
        val all = listOf(
            routine("c", DayPart.MORNING, position = 1, name = "charlie"),
            routine("b", DayPart.EVENING, position = 0, name = "Bravo"),
            routine("a", DayPart.MORNING, position = 1, name = "Alpha"),
            routine("z", DayPart.MORNING, position = 0, name = "Zulu"),
        )
        assertEquals(listOf("b", "z", "a", "c"), Routines.sorted(all).map { it.id })
        assertEquals(listOf("z", "a", "c"), Routines.forPart(all, DayPart.MORNING).map { it.id })
        assertTrue(Routines.forPart(all, DayPart.AFTERNOON).isEmpty())
    }

    // ---- Running one ----

    @Test
    fun stepsCanBeTickedInAnyOrderAndTheRoutineFinishesWhenAllAre() {
        val r = routine("m", steps = 3)
        var ticked = emptySet<String>()
        assertEquals(RunProgress(0, 3), Routines.progress(r, ticked))
        ticked = Routines.toggle(ticked, "m-2")
        ticked = Routines.toggle(ticked, "m-0")
        assertEquals(RunProgress(2, 3), Routines.progress(r, ticked))
        assertFalse(Routines.progress(r, ticked).complete)
        ticked = Routines.toggle(ticked, "m-1")
        assertTrue(Routines.progress(r, ticked).complete)
        // unticking takes it back
        ticked = Routines.toggle(ticked, "m-1")
        assertFalse(Routines.progress(r, ticked).complete)
    }

    @Test
    fun aRoutineWithoutStepsCanNeverBeFinished() {
        assertFalse(Routines.progress(routine("empty", steps = 0), emptySet()).complete)
    }

    @Test
    fun tickedStepsOfOtherRoutinesDoNotCount() {
        val r = routine("m", steps = 2)
        assertEquals(RunProgress(0, 2), Routines.progress(r, setOf("other-0", "other-1")))
    }

    // ---- Reordering ----

    @Test
    fun movingAStepDownUpAndNowhere() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), Routines.move(list, 0, 2))
        assertEquals(listOf("a", "d", "b", "c"), Routines.move(list, 3, 1))
        assertEquals(list, Routines.move(list, 1, 1))
        assertEquals(list, Routines.move(list, -1, 2))
        assertEquals(list, Routines.move(list, 1, 4))
        assertEquals(listOf("d", "a", "b", "c"), Routines.move(list, 3, 0))
    }

    // ---- Self-ticking habits ----

    @Test
    fun theHabitTicksOnDaysTheMorningRoutineWasFinished() {
        val morning = routine("m", DayPart.MORNING)
        val done = listOf(RoutineDone("m", day(0)), RoutineDone("m", day(2)), RoutineDone("m", day(2)))
        assertEquals(setOf(day(0), day(2)), Routines.autoDays(listOf(morning), done, DayPart.MORNING))
        // a finished evening routine does not tick the morning habit
        assertTrue(Routines.autoDays(listOf(morning, routine("e", DayPart.EVENING)), listOf(RoutineDone("e", day(1))), DayPart.MORNING).isEmpty())
    }

    @Test
    fun withTwoRoutinesInAPartBothMustBeFinished() {
        val a = routine("a")
        val b = routine("b")
        val done = listOf(RoutineDone("a", day(0)), RoutineDone("b", day(0)), RoutineDone("a", day(1)))
        assertEquals(setOf(day(0)), Routines.autoDays(listOf(a, b), done, DayPart.MORNING))
    }

    @Test
    fun routinesWithoutStepsAreLeftOutAndNoRoutinesMeansNoDays() {
        val a = routine("a")
        val empty = routine("empty", steps = 0)
        assertEquals(setOf(day(0)), Routines.autoDays(listOf(a, empty), listOf(RoutineDone("a", day(0))), DayPart.MORNING))
        assertTrue(Routines.autoDays(listOf(empty), emptyList(), DayPart.MORNING).isEmpty())
        assertTrue(Routines.autoDays(emptyList(), listOf(RoutineDone("a", day(0))), DayPart.MORNING).isEmpty())
    }

    // ---- Typing a routine ----

    @Test
    fun aRoutineNeedsANameAndAtLeastOneStep() {
        assertEquals("Give the routine a name.", Routines.problem("", listOf("x")))
        assertEquals("Give the routine a name.", Routines.problem("  ", listOf("x")))
        assertEquals("Add at least one step.", Routines.problem("Morning", emptyList()))
        assertEquals("Add at least one step.", Routines.problem("Morning", listOf("", "  ")))
        assertNull(Routines.problem("Morning", listOf("Stretch", "")))
    }

    @Test
    fun limitsOnNameStepsAndTheirLength() {
        assertNull(Routines.problem("n".repeat(40), listOf("x")))
        assertEquals("Keep the name to 40 characters.", Routines.problem("n".repeat(41), listOf("x")))
        assertNull(Routines.problem("m", listOf("s".repeat(80))))
        assertEquals("Keep each step to 80 characters.", Routines.problem("m", listOf("s".repeat(81))))
        assertNull(Routines.problem("m", List(30) { "step $it" }))
        assertEquals("A routine can have up to 30 steps.", Routines.problem("m", List(31) { "step $it" }))
        // blank steps are dropped, so they do not count towards the limit
        assertNull(Routines.problem("m", List(30) { "step $it" } + List(5) { " " }))
    }

    @Test
    fun routinesAndStepsMustBeValid() {
        assertThrows(IllegalArgumentException::class.java) { Routine("a", " ", DayPart.MORNING) }
        assertThrows(IllegalArgumentException::class.java) { RoutineItem("a", " ") }
        assertThrows(IllegalArgumentException::class.java) {
            Routine("a", "A", DayPart.MORNING, listOf(RoutineItem("s", "One"), RoutineItem("s", "Two")))
        }
    }

    @Test
    fun theStartingSuggestionsAreValid() {
        assertEquals(listOf("Morning", "Evening"), Routines.suggestions.map { it.name })
        assertEquals(listOf(DayPart.MORNING, DayPart.EVENING), Routines.suggestions.map { it.part })
        Routines.suggestions.forEach { assertNull(it.name, Routines.problem(it.name, it.steps)) }
    }
}
