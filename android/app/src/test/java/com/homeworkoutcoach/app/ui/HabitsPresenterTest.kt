package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.storedHabit
import com.homeworkoutcoach.app.ui.habits.DayCellUi
import com.homeworkoutcoach.app.ui.habits.HabitDetailPresenter
import com.homeworkoutcoach.app.ui.habits.HabitDraft
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.app.ui.habits.HabitStyles
import com.homeworkoutcoach.app.ui.habits.HabitWrites
import com.homeworkoutcoach.app.ui.habits.HabitsPresenter
import com.homeworkoutcoach.app.ui.habits.ScheduleType
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.DayState
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Habits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val WED = LocalDate.of(2026, 10, 28) // a Wednesday

class HabitsPresenterTest {
    private val presenter = HabitsPresenter()

    private fun entries(vararg e: Pair<String, Map<LocalDate, Int>>) = mapOf(*e)

    @Test
    fun dueHabitsComeFirstAndTheRestAreGrouped() {
        val water = storedHabit("water", name = "Water", kind = HabitKind.COUNT, target = 8, position = 0)
        val gym = storedHabit("gym", name = "Gym", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)), position = 1)
        val old = storedHabit("old", archived = true, position = 2)
        val ui = presenter.present(listOf(water, gym, old), entries("water" to mapOf(WED to 5)), emptySet(), WED)

        assertEquals(listOf("water"), ui.today.map { it.id })
        assertEquals(listOf("gym"), ui.other.map { it.id })
        assertEquals(listOf("old"), ui.archived.map { it.id })
        assertEquals("0 of 1 done today", ui.summary)
        val row = ui.today.single()
        assertEquals("5 of 8", row.progress)
        assertFalse(row.done)
        assertEquals(5f / 8, row.fraction, 1e-6f)
    }

    @Test
    fun anotherDayShowsWhatWasDueAndDoneThenAndTheWeeksNumbers() {
        val journal = storedHabit("j", name = "Journal", position = 0)
        val water = storedHabit("w", name = "Water", kind = HabitKind.COUNT, target = 8, position = 1)
        val mon = WED.minusDays(2)
        val tue = WED.minusDays(1)
        val e = entries("j" to mapOf(mon to 1, tue to 1), "w" to mapOf(mon to 8, tue to 3))

        val monday = presenter.present(listOf(journal, water), e, emptySet(), WED, shown = mon)
        assertEquals("Mon 26 Oct", monday.title)
        assertFalse(monday.isToday)
        assertEquals("All done.", monday.summary)
        assertEquals(listOf(true, true), monday.today.map { it.done })

        val tuesday = presenter.present(listOf(journal, water), e, emptySet(), WED, shown = tue)
        assertEquals("Yesterday", tuesday.title)
        assertEquals("1 of 2 done", tuesday.summary)
        assertEquals(listOf(true, false), tuesday.today.map { it.done })

        // the week's numbers: past days and today, not the days ahead
        assertEquals(mapOf(mon to "2/2", tue to "1/2", WED to "0/2"), tuesday.badges.mapValues { it.value.text })
        assertTrue(tuesday.badges.getValue(mon).complete)
        assertFalse(tuesday.badges.getValue(tue).complete)
    }

    @Test
    fun aDayThatHasNotHappenedSaysWhatIsDueAndIsMarkedFuture() {
        val journal = storedHabit("j", name = "Journal")
        val ui = presenter.present(listOf(journal), emptyMap(), emptySet(), WED, shown = WED.plusDays(1))
        assertTrue(ui.isFuture)
        assertEquals("1 due", ui.summary)
        assertEquals("Tomorrow", ui.title)
        assertTrue(ui.badges.keys.none { it.isAfter(WED) })
    }

    @Test
    fun aHabitIsNotDueBeforeItStarted() {
        val habit = storedHabit("h") // starts on Saturday 24 October
        val ui = presenter.present(listOf(habit), emptyMap(), emptySet(), WED, shown = WED.minusDays(5)) // the Friday before
        assertTrue(ui.today.isEmpty())
        assertEquals("Nothing was due.", ui.summary)
        assertTrue(ui.badges.keys.all { it >= WED.minusDays(4) }) // no numbers for days before it started
    }

    @Test
    fun everyIconKeyHasItsOwnEmojiAndOldKeysAreKept() {
        val emojis = HabitStyles.icons.map { com.homeworkoutcoach.app.ui.habits.HabitLook.emoji(it) }
        assertEquals(HabitStyles.icons.size, emojis.toSet().size) // no two icons look the same
        // the keys saved habits already use
        listOf("check", "drop", "moon", "sun", "book", "pen", "dumbbell", "walk", "lotus", "heart", "star", "list", "bolt")
            .forEach { assertTrue("$it is gone", it in HabitStyles.icons) }
        // a key from a future version still shows something
        assertEquals("✅", com.homeworkoutcoach.app.ui.habits.HabitLook.emoji("nope"))
        // the named starting habits point at keys that exist
        listOf("Workout", "Water", "Reading", "Walking", "Journal").forEach { assertTrue(HabitStyles.forName(it).first in HabitStyles.icons) }
    }

    @Test
    fun summaryReadsGently() {
        val journal = storedHabit("journal", name = "Journal")
        val water = storedHabit("water", kind = HabitKind.COUNT, target = 8)
        fun summary(vararg done: String) = presenter.present(
            listOf(journal, water),
            done.associateWith { mapOf(WED to if (it == "water") 8 else 1) },
            emptySet(), WED,
        ).summary
        assertEquals("0 of 2 done today", summary())
        assertEquals("1 of 2 done today", summary("journal"))
        assertEquals("All done for today.", summary("journal", "water"))
        assertEquals("Nothing is due today.", presenter.present(listOf(storedHabit("x", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.SUNDAY)))), emptyMap(), emptySet(), WED).summary)
        assertEquals("Track anything you want to do every day or every week.", presenter.present(emptyList(), emptyMap(), emptySet(), WED).summary)
    }

    @Test
    fun theWorkoutHabitTicksItselfFromWorkouts() {
        val workout = storedHabit("w", name = "Workout", auto = AutoSource.WORKOUT)
        val trained = presenter.present(listOf(workout), emptyMap(), setOf(WED), WED).today.single()
        assertTrue(trained.done)
        assertEquals("Done", trained.progress)
        assertTrue(trained.auto)
        assertFalse(presenter.present(listOf(workout), emptyMap(), emptySet(), WED).today.single().done)
        // unticked by hand wins
        assertFalse(presenter.present(listOf(workout), entries("w" to mapOf(WED to 0)), setOf(WED), WED).today.single().done)
    }

    @Test
    fun theMorningRoutineHabitTicksItselfFromAFinishedRoutine() {
        val habit = storedHabit("mr", name = "Morning routine", auto = AutoSource.MORNING_ROUTINE)
        val sourceDays = mapOf(AutoSource.MORNING_ROUTINE to setOf(WED))
        val ui = presenter.present(listOf(habit), emptyMap(), emptySet(), WED, sourceDays)
        assertTrue(ui.today.single().done)
        assertTrue(ui.today.single().auto)
        assertFalse(presenter.present(listOf(habit), emptyMap(), emptySet(), WED).today.single().done)
        // the habit page's calendar sees it too
        val detail = HabitDetailPresenter().present(habit, emptyMap(), emptySet(), WED, YearMonth.of(2026, 10), sourceDays)
        assertEquals(1, detail.monthDays)
    }

    @Test
    fun aTimesAWeekHabitLeavesTodayOnceTheWeekIsMet() {
        val run = storedHabit("run", schedule = HabitSchedule.TimesPerWeek(2))
        val log = entries("run" to mapOf(WED.minusDays(2) to 1, WED.minusDays(1) to 1))
        val ui = presenter.present(listOf(run), log, emptySet(), WED)
        assertTrue(ui.today.isEmpty())
        assertEquals("2 of 2 this week", ui.other.single().week)
    }

    @Test
    fun formatting() {
        assertEquals("20 min", HabitFormat.minutes(20))
        assertEquals("7 h", HabitFormat.minutes(420))
        assertEquals("1 h 30 min", HabitFormat.minutes(90))
        assertEquals("20 of 30 min", HabitFormat.progress(HabitKind.TIME, 20, 30))
        assertEquals("1 h 30 min of 7 h", HabitFormat.progress(HabitKind.TIME, 90, 420))
        assertEquals("0 min of 1 h", HabitFormat.progress(HabitKind.TIME, 0, 60))
        assertEquals("Not done", HabitFormat.progress(HabitKind.DONE, 0, 1))
        assertEquals("Every day", HabitFormat.schedule(HabitSchedule.Daily))
        assertEquals("Mon, Wed, Fri", HabitFormat.schedule(HabitSchedule.Weekdays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))))
        assertEquals("Every day", HabitFormat.schedule(HabitSchedule.Weekdays(DayOfWeek.entries.toSet())))
        assertEquals("3 times a week", HabitFormat.schedule(HabitSchedule.TimesPerWeek(3)))
        assertEquals("Once a week", HabitFormat.schedule(HabitSchedule.TimesPerWeek(1)))
    }

    @Test
    fun writesKeepAZeroOnlyForHabitsThatTickThemselves() = runTest {
        val repo = InMemoryHabitRepository()
        repo.save(storedHabit("w", kind = HabitKind.COUNT, target = 8))
        HabitWrites.record(repo, "w", WED, 3, auto = false)
        assertEquals(mapOf(WED to 3), repo.entries().first().getValue("w"))
        HabitWrites.record(repo, "w", WED, 0, auto = false)
        assertTrue(repo.entries().first().isEmpty())
        HabitWrites.record(repo, "w", WED, 0, auto = true)
        assertEquals(mapOf(WED to 0), repo.entries().first().getValue("w"))
    }
}

class HabitDetailPresenterTest {
    private val presenter = HabitDetailPresenter()
    private val oct = YearMonth.of(2026, 10)

    private fun cell(ui: com.homeworkoutcoach.app.ui.habits.HabitDetailUi, day: Int): DayCellUi =
        ui.weeks.flatten().filterNotNull().first { it.date == LocalDate.of(2026, 10, day) }

    @Test
    fun monthGridStartsOnMondayAndPadsTheEnds() {
        val h = storedHabit("a") // started 2026-10-24
        val ui = presenter.present(h, emptyMap(), emptySet(), WED, oct)
        assertEquals("October 2026", ui.monthTitle)
        assertTrue(ui.weeks.all { it.size == 7 })
        // 1 October 2026 is a Thursday: three blanks first
        assertEquals(listOf(null, null, null), ui.weeks.first().take(3))
        assertEquals(LocalDate.of(2026, 10, 1), ui.weeks.first()[3]?.date)
        assertEquals(31, ui.weeks.flatten().filterNotNull().size)
        assertEquals(LocalDate.of(2026, 10, 31), ui.weeks.last().filterNotNull().last().date) // Saturday
    }

    @Test
    fun daysAreDoneOpenOrNotDueAndOnlyPastDaysFromTheStartCanBeEdited() {
        val h = storedHabit("a", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))) // starts Oct 24
        val log = mapOf("a" to mapOf(LocalDate.of(2026, 10, 26) to 1))
        val ui = presenter.present(h, log, emptySet(), WED, oct)
        assertEquals(DayState.DONE, cell(ui, 26).state) // Monday, done
        assertEquals(DayState.NOT_DUE, cell(ui, 27).state) // Tuesday
        assertEquals(DayState.OPEN, cell(ui, 28).state) // today
        assertTrue(cell(ui, 28).isToday)
        assertTrue(cell(ui, 26).editable)
        assertFalse(cell(ui, 23).editable) // before the start date
        assertTrue(cell(ui, 24).editable) // the start date itself
        assertFalse(cell(ui, 29).editable) // tomorrow
        assertTrue(cell(ui, 29).future)
    }

    @Test
    fun countsForTheWeekMonthAndAllTime() {
        val h = storedHabit("a") // daily, started 2026-10-24
        val days = listOf(25, 26, 28).associate { LocalDate.of(2026, 10, it) to 1 }
        val ui = presenter.present(h, mapOf("a" to days), emptySet(), WED, oct)
        assertEquals(2, ui.weekDone) // Mon 26 and Wed 28; Sunday 25 belongs to the week before
        assertEquals(7, ui.weekTarget)
        assertFalse(ui.weekComplete)
        assertEquals(3, ui.monthDays)
        assertEquals(3, ui.allTimeDays)
        assertEquals("24 Oct 2026", ui.since)
    }

    @Test
    fun monthNavigationStopsAtTheStartAndAtToday() {
        val h = storedHabit("a")
        assertFalse(presenter.present(h, emptyMap(), emptySet(), WED, oct).canPrev)
        assertFalse(presenter.present(h, emptyMap(), emptySet(), WED, oct).canNext)
        val older = storedHabit("b").let { it.copy(habit = it.habit.copy(startDate = LocalDate.of(2026, 8, 3))) }
        val sep = presenter.present(older, emptyMap(), emptySet(), WED, YearMonth.of(2026, 9))
        assertTrue(sep.canPrev)
        assertTrue(sep.canNext)
        assertEquals(0, sep.monthDays)
        // a month before the start has no done days
        assertEquals(0, presenter.present(h, emptyMap(), emptySet(), WED, YearMonth.of(2026, 9)).monthDays)
    }
}

class HabitDraftTest {
    private val none = emptyList<String>()

    private fun draft(name: String = "Water") = HabitDraft(name = name)

    @Test
    fun aNewDraftIsValidOnceItHasAName() {
        assertEquals("Give the habit a name.", HabitDraft().problem(none))
        assertEquals("Give the habit a name.", draft("   ").problem(none))
        assertNull(draft().problem(none))
        assertEquals("Keep the name to 40 characters.", draft("x".repeat(41)).problem(none))
    }

    @Test
    fun namesCannotRepeat() {
        assertEquals("You already have a habit with this name.", draft(" water ").problem(listOf("Water")))
        assertNull(draft("Walking").problem(listOf("Water")))
    }

    @Test
    fun targetsMustFitTheKind() {
        val count = draft().withKind(HabitKind.COUNT)
        assertEquals("8", count.targetText)
        assertEquals(8, count.target)
        assertEquals("Enter a target from 1 to 999.", count.copy(targetText = "0").problem(none))
        assertEquals("Enter a target from 1 to 999.", count.copy(targetText = "").problem(none))
        assertEquals("Enter a target from 1 to 999.", count.copy(targetText = "1000").problem(none))
        val time = count.withKind(HabitKind.TIME)
        assertEquals("20", time.targetText)
        assertEquals("Enter minutes from 1 to 1440.", time.copy(targetText = "1441").problem(none))
        assertNull(time.copy(targetText = "1440").problem(none))
        // a done / not done habit ignores whatever was typed
        assertEquals(1, time.withKind(HabitKind.DONE).target)
        // switching to the same kind keeps what was typed
        assertEquals("12", count.copy(targetText = "12").withKind(HabitKind.COUNT).targetText)
    }

    @Test
    fun certainDaysNeedAtLeastOneDay() {
        val d = draft().copy(scheduleType = ScheduleType.WEEKDAYS, weekdays = emptySet())
        assertEquals("Choose at least one day.", d.problem(none))
        assertNull(d.copy(weekdays = setOf(DayOfWeek.SATURDAY)).problem(none))
    }

    @Test
    fun aNewHabitStartsTodayAtTheEndOfTheList() {
        val s = draft(" Water ").copy(kind = HabitKind.COUNT, targetText = "8", icon = "drop", color = "blue",
            scheduleType = ScheduleType.TIMES, times = 5).toStored("new-id", WED, 4)
        assertEquals("new-id", s.habit.id)
        assertEquals("Water", s.habit.name)
        assertEquals(WED, s.habit.startDate)
        assertEquals(4, s.position)
        assertEquals(HabitSchedule.TimesPerWeek(5), s.habit.schedule)
        assertEquals("drop", s.icon)
    }

    @Test
    fun editingKeepsTheIdStartDateAndPosition() {
        val stored = storedHabit("gym", name = "Gym", schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY)), auto = AutoSource.WORKOUT, position = 2)
        val edited = HabitDraft.from(stored).copy(name = "Lift").toStored("ignored", WED, 9)
        assertEquals("gym", edited.habit.id)
        assertEquals(stored.habit.startDate, edited.habit.startDate)
        assertEquals(2, edited.position)
        assertEquals(AutoSource.WORKOUT, edited.habit.auto)
        assertEquals(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY)), edited.habit.schedule)
        assertEquals(stored.copy(habit = stored.habit.copy(name = "Lift")), edited)
    }

    @Test
    fun everySuggestionMakesAValidDraftWithAKnownLookAndRoundTrips() {
        Habits.suggestions.forEach { s ->
            val d = HabitDraft.from(s)
            assertNull(s.name, d.problem(none))
            assertTrue(d.icon in HabitStyles.icons)
            assertTrue(d.color in HabitStyles.colors)
            val stored = d.toStored("id", WED, 0)
            assertEquals(s.kind, stored.habit.kind)
            assertEquals(s.target, stored.habit.target)
            assertEquals(s.auto, stored.habit.auto)
            assertEquals(d.copy(id = "id", startDate = WED, position = 0), HabitDraft.from(stored))
        }
    }

    @Test
    fun theDefaultLookIsInThePalette() {
        assertTrue(HabitStyles.DEFAULT_ICON in HabitStyles.icons)
        assertTrue(HabitStyles.DEFAULT_COLOR in HabitStyles.colors)
        assertEquals(HabitStyles.DEFAULT_ICON to HabitStyles.DEFAULT_COLOR, HabitStyles.forName("Something new"))
    }
}
