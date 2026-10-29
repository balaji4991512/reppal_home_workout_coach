package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Habit rules from specs/HABITS_PLANNER_SPEC.md. 2026-10-26 is a Monday. */
class HabitsTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private fun day(n: Int) = mon.plusDays(n.toLong()) // 0 = Monday .. 6 = Sunday

    private fun habit(
        schedule: HabitSchedule = HabitSchedule.Daily,
        kind: HabitKind = HabitKind.DONE,
        target: Int = 1,
        start: LocalDate = mon.minusDays(60),
        auto: AutoSource = AutoSource.NONE,
        archived: Boolean = false,
    ) = Habit("h", "Habit", kind, target, schedule, start, auto, archived)

    private fun ticks(vararg days: Int) = HabitLog(days.associate { day(it) to 1 })

    // ---- Dates ----

    @Test
    fun weekStartsOnMonday() {
        assertEquals(mon, Habits.weekStart(day(0)))
        assertEquals(mon, Habits.weekStart(day(6)))
        assertEquals(mon.plusDays(7), Habits.weekStart(day(7)))
    }

    // ---- Amounts ----

    @Test
    fun waterAtFiveOfEightIsNotDoneUntilThreeMoreAreAdded() {
        val water = habit(kind = HabitKind.COUNT, target = 8)
        val five = HabitLog(mapOf(day(0) to 5))
        assertFalse(Habits.isDone(water, five, day(0)))
        assertEquals(DayState.PARTIAL, Habits.dayState(water, five, day(0)))
        assertEquals(5.0 / 8, Habits.fraction(water, five, day(0)), 1e-9)
        val eight = HabitLog(mapOf(day(0) to Habits.adjusted(water, 5, 3)))
        assertTrue(Habits.isDone(water, eight, day(0)))
        assertEquals(DayState.DONE, Habits.dayState(water, eight, day(0)))
    }

    @Test
    fun amountsNeverGoBelowZeroAndTicksStayOnOrOff() {
        val water = habit(kind = HabitKind.COUNT, target = 8)
        assertEquals(0, Habits.adjusted(water, 1, -5))
        val journal = habit()
        assertEquals(1, Habits.adjusted(journal, 0, 1))
        assertEquals(1, Habits.adjusted(journal, 1, 1))
        assertEquals(0, Habits.adjusted(journal, 1, -1))
    }

    @Test
    fun fractionStopsAtOne() {
        val reading = habit(kind = HabitKind.TIME, target = 20)
        assertEquals(1.0, Habits.fraction(reading, HabitLog(mapOf(day(0) to 45)), day(0)), 0.0)
        assertEquals(0.0, Habits.fraction(reading, HabitLog(), day(0)), 0.0)
    }

    // ---- Due ----

    @Test
    fun weekdayHabitIsNotDueOnSaturdayAndDoesNotCountAgainstTheWeek() {
        val gym = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        val log = ticks(0, 2, 4)
        assertTrue(Habits.isDue(gym, log, day(0)))
        assertFalse(Habits.isDue(gym, log, day(1)))
        assertFalse(Habits.isDue(gym, log, day(5))) // Saturday
        assertEquals(DayState.NOT_DUE, Habits.dayState(gym, HabitLog(), day(5)))
        assertEquals(WeekProgress(3, 3), Habits.weekProgress(gym, log, day(5)))
        assertTrue(Habits.weekProgress(gym, log, day(5)).complete)
    }

    @Test
    fun threeTimesAWeekIsDoneAfterThreeTicksAndNotDueThursday() {
        val run = habit(HabitSchedule.TimesPerWeek(3))
        val log = ticks(0, 1, 2) // Mon, Tue, Wed
        assertTrue(Habits.isDue(run, log, day(2))) // Wednesday stays due: it is the day that was ticked
        assertEquals(DayState.DONE, Habits.dayState(run, log, day(2)))
        assertFalse(Habits.isDue(run, log, day(3))) // Thursday
        assertEquals(WeekProgress(3, 3), Habits.weekProgress(run, log, day(3)))
        // next week starts fresh
        assertTrue(Habits.isDue(run, log, day(7)))
    }

    @Test
    fun timesAWeekStaysDueUntilTheCountIsMet() {
        val run = habit(HabitSchedule.TimesPerWeek(3))
        val log = ticks(0, 1)
        assertTrue(Habits.isDue(run, log, day(3)))
        assertTrue(Habits.isDue(run, log, day(6)))
        assertEquals(WeekProgress(2, 3), Habits.weekProgress(run, log, day(6)))
        // ticked anyway on a day when it was not due: the day still shows as done
        val extra = ticks(0, 1, 2, 3)
        assertEquals(DayState.DONE, Habits.dayState(run, extra, day(3)))
    }

    @Test
    fun nothingIsDueBeforeTheStartDateOrAfterArchiving() {
        val h = habit(start = day(2))
        assertFalse(Habits.isDue(h, HabitLog(), day(1)))
        assertTrue(Habits.isDue(h, HabitLog(), day(2)))
        assertFalse(Habits.isDue(habit(archived = true), HabitLog(), day(2)))
    }

    @Test
    fun firstWeekTargetOnlyCountsDaysFromTheStartDate() {
        val h = habit(start = day(4)) // starts Friday: Fri, Sat, Sun
        assertEquals(WeekProgress(0, 3), Habits.weekProgress(h, HabitLog(), day(4)))
        val run = habit(HabitSchedule.TimesPerWeek(5), start = day(4))
        assertEquals(3, Habits.weekProgress(run, HabitLog(), day(4)).target)
    }

    // ---- Stats: never "missed" for what hasn't happened ----

    @Test
    fun todayIsNotCountedAsMissedUntilItIsDone() {
        val h = habit(start = day(0))
        // Monday and Tuesday done, today is Wednesday and not yet ticked: 2 of 2
        assertEquals(HabitStats(2, 2), Habits.stats(h, ticks(0, 1), mon, day(2)))
        // ticking today counts it
        assertEquals(HabitStats(3, 3), Habits.stats(h, ticks(0, 1, 2), mon, day(2)))
        // a past day left undone is counted as due
        assertEquals(HabitStats(1, 2), Habits.stats(h, ticks(1), mon, day(2)))
    }

    @Test
    fun weekdayStatsSkipDaysThatAreNotScheduled() {
        val gym = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), start = day(0))
        assertEquals(HabitStats(1, 2), Habits.stats(gym, ticks(0), mon, day(4)))
        // done on an unscheduled day counts on both sides, so done never passes due
        assertEquals(HabitStats(2, 3), Habits.stats(gym, ticks(0, 1), mon, day(4)))
    }

    @Test
    fun timesAWeekStatsOnlyCountAFinishedWeekInFull() {
        val run = habit(HabitSchedule.TimesPerWeek(3), start = mon)
        val log = HabitLog((listOf(0, 1) + listOf(7, 8, 9)).associate { day(it) to 1 })
        // today is Wednesday of week 2: week 1 finished with 2 of 3, week 2 so far has 3 of 3
        assertEquals(HabitStats(5, 6), Habits.stats(run, log, mon, day(9)))
        // on the first day of a new week with nothing done yet, that week is not behind
        assertEquals(HabitStats(2, 3), Habits.stats(run, ticks(0, 1), mon, day(7)))
        assertEquals(HabitStats(0, 0), Habits.stats(run, log, mon, day(-1)))
    }

    @Test
    fun aPastPeriodCountsItsLastDayLikeAnyOther() {
        val h = habit(start = mon.minusDays(30))
        // the week Mon..Sun before this one, nothing done on its Sunday: 6 of 7, with today well after it
        val log = ticks(-7, -6, -5, -4, -3, -2)
        assertEquals(HabitStats(6, 7), Habits.stats(h, log, day(-7), day(2), until = day(-1)))
        // without [until] the range runs to today, which is not counted as missed
        assertEquals(HabitStats(6, 9), Habits.stats(h, log, day(-7), day(2)))
    }

    @Test
    fun aPeriodEndingMidWeekOnlyOwnsItsOwnDaysOfATimesAWeekHabit() {
        val run = habit(HabitSchedule.TimesPerWeek(3), start = mon)
        // Monday and Tuesday of a finished week, ticked both days: 2 of 2, not 2 of the week's 3
        assertEquals(HabitStats(2, 2), Habits.stats(run, ticks(0, 1), mon, day(9), until = day(1)))
    }

    @Test
    fun doneDaysCountsARange() {
        assertEquals(2, Habits.doneDays(habit(), ticks(0, 3), day(0), day(6)))
        assertEquals(1, Habits.doneDays(habit(), ticks(0, 3), day(1), day(6)))
    }

    // ---- Streaks ----

    @Test
    fun aDailyStreakCountsDaysInARowAndTodayNotYetDoneDoesNotBreakIt() {
        val h = habit(start = mon.minusDays(10))
        // done Sat, Sun, Mon, Tue; today is Wednesday and not done yet
        val s = Habits.streaks(h, ticks(-2, -1, 0, 1), day(2))
        assertEquals(Streaks(4, 4, false), s)
        // ticking today makes it five
        assertEquals(5, Habits.streaks(h, ticks(-2, -1, 0, 1, 2), day(2)).current)
    }

    @Test
    fun aMissedDayResetsTheCurrentStreakButTheBestIsKept() {
        val h = habit(start = mon.minusDays(20))
        // a run of 5 (days -9..-5), a gap, then 2 (days 0, 1) with today (day 2) not done
        val log = ticks(-9, -8, -7, -6, -5, 0, 1)
        assertEquals(Streaks(2, 5, false), Habits.streaks(h, log, day(2)))
        // a missed day just before today ends it: nothing done yesterday
        assertEquals(0, Habits.streaks(h, ticks(-9, -8, 0), day(2)).current)
    }

    @Test
    fun aHabitNotYetStartedOrNeverDoneHasNoStreak() {
        assertEquals(Streaks(0, 0, false), Habits.streaks(habit(start = day(3)), HabitLog(), day(2)))
        assertEquals(Streaks(0, 0, false), Habits.streaks(habit(start = mon.minusDays(5)), HabitLog(), day(2)))
    }

    @Test
    fun aWeekdayHabitSkipsTheDaysItIsNotScheduled() {
        val gym = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), start = mon.minusDays(14))
        // last Mon, Wed, Fri and this Mon, Wed (today, done): the days between do not break it
        val log = ticks(-7, -5, -3, 0, 2)
        assertEquals(Streaks(5, 5, false), Habits.streaks(gym, log, day(2)))
        // missing last Wednesday breaks it
        assertEquals(Streaks(3, 3, false), Habits.streaks(gym, ticks(-7, -3, 0, 2), day(2)))
    }

    @Test
    fun aTimesAWeekHabitCountsWeeksInARow() {
        val run = habit(HabitSchedule.TimesPerWeek(3), start = mon.minusDays(28))
        // three weeks ago: 3 done; two weeks ago: 2 (short); last week: 3; this week so far: 1 (in progress)
        val log = HabitLog(listOf(-21, -20, -19, -14, -13, -7, -6, -5, 0).associate { day(it) to 1 })
        assertEquals(Streaks(1, 1, true), Habits.streaks(run, log, day(2)))
        // this week reaching 3 adds it
        val met = HabitLog((listOf(-21, -20, -19, -14, -13, -7, -6, -5, 0, 1, 2)).associate { day(it) to 1 })
        assertEquals(Streaks(2, 2, true), Habits.streaks(run, met, day(2)))
    }

    @Test
    fun theFirstShortWeekOfATimesAWeekHabitNeedsOnlyTheDaysLeft() {
        val run = habit(HabitSchedule.TimesPerWeek(5), start = day(4)) // starts Friday: 3 days left in the week
        val log = HabitLog(listOf(4, 5, 6).associate { day(it) to 1 })
        assertEquals(1, Habits.streaks(run, log, day(8)).current)
    }

    @Test
    fun anAutoTickedHabitStreaksFromItsAutoDays() {
        val workout = habit(auto = AutoSource.WORKOUT, start = mon.minusDays(5))
        val log = HabitLog(autoDays = setOf(day(-1), day(0), day(1)))
        assertEquals(3, Habits.streaks(workout, log, day(2)).current)
        // unticked by hand on one of them breaks it
        assertEquals(1, Habits.streaks(workout, log.copy(entries = mapOf(day(0) to 0)), day(2)).current)
    }

    // ---- Reminders ----

    private fun remind(schedule: HabitSchedule = HabitSchedule.Daily, at: List<java.time.LocalTime> = listOf(java.time.LocalTime.of(10, 0)), archived: Boolean = false) =
        Habit("h", "Habit", HabitKind.DONE, 1, schedule, mon.minusDays(60), AutoSource.NONE, archived, at)

    @Test
    fun aDueHabitThatIsNotDoneIsRemindedAbout() {
        assertTrue(Habits.shouldRemind(remind(), HabitLog(), day(2)))
    }

    @Test
    fun noReminderOrAnArchivedHabitStaysQuiet() {
        assertFalse(Habits.shouldRemind(remind(at = emptyList()), HabitLog(), day(2)))
        assertFalse(Habits.shouldRemind(remind(archived = true), HabitLog(), day(2)))
    }

    @Test
    fun aHabitAlreadyDoneTodayIsNotRemindedAbout() {
        assertFalse(Habits.shouldRemind(remind(), ticks(2), day(2)))
        // done yesterday does not matter
        assertTrue(Habits.shouldRemind(remind(), ticks(1), day(2)))
    }

    @Test
    fun aHabitNotDueTodayStaysQuiet() {
        val gym = remind(HabitSchedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        assertTrue(Habits.shouldRemind(gym, HabitLog(), day(0))) // Monday
        assertFalse(Habits.shouldRemind(gym, HabitLog(), day(2))) // Wednesday
    }

    @Test
    fun aTimesAWeekHabitReminsUntilTheWeekIsMet() {
        val run = remind(HabitSchedule.TimesPerWeek(2))
        assertTrue(Habits.shouldRemind(run, ticks(0), day(2))) // one of two so far
        assertFalse(Habits.shouldRemind(run, ticks(0, 1), day(2))) // met: no more this week
        assertTrue(Habits.shouldRemind(run, ticks(0, 1), day(7))) // a new week
    }

    @Test
    fun aSelfTickingHabitDoneByItsSourceIsNotRemindedAbout() {
        val workout = remind().copy(auto = AutoSource.WORKOUT)
        assertFalse(Habits.shouldRemind(workout, HabitLog(autoDays = setOf(day(2))), day(2)))
        assertTrue(Habits.shouldRemind(workout, HabitLog(), day(2)))
    }

    @Test
    fun theNextReminderIsTodayIfItIsStillAheadOtherwiseTomorrow() {
        val ten = java.time.LocalTime.of(10, 0)
        fun at(h: Int, m: Int = 0) = day(2).atTime(h, m)
        assertEquals(at(10), Habits.nextReminder(ten, at(8, 30)))
        assertEquals(at(10).plusDays(1), Habits.nextReminder(ten, at(10))) // it has just rung
        assertEquals(at(10).plusDays(1), Habits.nextReminder(ten, at(10, 1)))
        assertEquals(at(10).plusDays(1), Habits.nextReminder(ten, at(23, 59)))
        assertEquals(day(3).atTime(0, 5), Habits.nextReminder(java.time.LocalTime.of(0, 5), at(23, 59))) // just after midnight
    }

    // ---- Ticking itself ----

    @Test
    fun anAutoTickCountsAsDoneAndAHandTickOverridesIt() {
        val workout = habit(auto = AutoSource.WORKOUT)
        val log = HabitLog(autoDays = setOf(day(0), day(1)))
        assertTrue(Habits.isDone(workout, log, day(0)))
        assertFalse(Habits.isDone(workout, log, day(2)))
        // unticked by hand: an entry of 0 wins over the auto tick
        val unticked = log.copy(entries = mapOf(day(1) to 0))
        assertFalse(Habits.isDone(workout, unticked, day(1)))
        assertTrue(Habits.isDone(workout, unticked, day(0)))
        // ticked by hand on a day that did not tick itself
        val manual = log.copy(entries = mapOf(day(2) to 1))
        assertTrue(Habits.isDone(workout, manual, day(2)))
    }

    @Test
    fun anAutoTickCountsForNumberHabitsAsTheFullTarget() {
        val minutes = habit(kind = HabitKind.TIME, target = 30, auto = AutoSource.WORKOUT)
        assertEquals(30, Habits.amount(minutes, HabitLog(autoDays = setOf(day(0))), day(0)))
    }

    // ---- Checks and suggestions ----

    @Test
    fun badHabitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { habit(kind = HabitKind.COUNT, target = 0) }
        assertThrows(IllegalArgumentException::class.java) { habit(kind = HabitKind.DONE, target = 2) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.Weekdays(emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.TimesPerWeek(0)) }
        assertThrows(IllegalArgumentException::class.java) { habit(HabitSchedule.TimesPerWeek(8)) }
    }

    @Test
    fun startingSuggestionsMatchTheSpec() {
        val s = Habits.suggestions
        assertEquals(
            listOf("Workout", "Morning routine", "Night routine", "Journal", "Water", "Sleep", "Reading", "Meditation", "Yoga", "Walking"),
            s.take(10).map { it.name },
        )
        assertEquals(
            listOf(AutoSource.WORKOUT, AutoSource.MORNING_ROUTINE, AutoSource.NIGHT_ROUTINE, AutoSource.JOURNAL),
            s.map { it.auto }.filter { it != AutoSource.NONE },
        )
        assertEquals(Habits.Suggestion("Water", HabitKind.COUNT, 8, icon = "drop"), s.first { it.name == "Water" })
        assertEquals(420, s.first { it.name == "Sleep" }.target) // 7 hours, in minutes
        // every suggestion makes a valid habit
        s.forEach { habit(kind = it.kind, target = it.target, auto = it.auto) }
    }

    @Test
    fun thereAreAHundredOrMoreSuggestionsWithDistinctNames() {
        val s = Habits.suggestions
        assertTrue(s.size >= 100)
        assertEquals(s.size, s.map { it.name.lowercase() }.toSet().size)
        assertTrue(s.all { it.icon.isNotBlank() && it.name.length <= 40 })
    }

    @Test
    fun suggestingWithNothingTypedGivesTheMostCommon() {
        assertEquals(listOf("Workout", "Morning routine", "Night routine", "Journal", "Water", "Sleep"), Habits.suggest("").map { it.name })
        assertEquals(listOf("Water", "Sleep"), Habits.suggest("", skip = listOf("workout", "Morning routine", "Night routine", "JOURNAL"), limit = 2).map { it.name })
    }

    @Test
    fun suggestingMatchesWhatIsTypedStartsFirstThenContains() {
        val names = Habits.suggest("wa", limit = 20).map { it.name }
        assertTrue(names.first().startsWith("Wa", ignoreCase = true))
        assertTrue("Water" in names && "Walking" in names)
        assertEquals(listOf("Water"), Habits.suggest(" WATER ").map { it.name }.filter { it == "Water" })
        // inside a name, at the start of a later word too
        assertTrue("Walk the dog" in Habits.suggest("dog", limit = 20).map { it.name })
        assertTrue(Habits.suggest("zzzz").isEmpty())
    }

    @Test
    fun suggestingLeavesOutHabitsTheyAlreadyHave() {
        assertTrue("Water" !in Habits.suggest("water", skip = listOf("water")).map { it.name })
    }

    // ---- Several reminders ----

    private fun t(h: Int, m: Int = 0) = java.time.LocalTime.of(h, m)

    @Test
    fun aHabitCanHaveManyRemindersButNotTooMany() {
        val many = (6..17).map { t(it) }
        assertEquals(12, Habits.MAX_REMINDERS)
        remind(at = many)
        assertThrows(IllegalArgumentException::class.java) { remind(at = many + t(18)) }
        assertThrows(IllegalArgumentException::class.java) { remind(at = listOf(t(9), t(9))) }
    }

    @Test
    fun anyOfTheRemindersCanRingWhileTheHabitIsNotDone() {
        val water = remind(at = listOf(t(9), t(13), t(17)))
        assertTrue(Habits.shouldRemind(water, HabitLog(), day(2)))
        assertFalse(Habits.shouldRemind(water, ticks(2), day(2)))
    }

    @Test
    fun aCountHabitStopsRingingOnceTheTargetIsMet() {
        val glasses = Habit("w", "Water", HabitKind.COUNT, 8, HabitSchedule.Daily, mon.minusDays(60), reminders = listOf(t(9), t(13)))
        assertTrue(Habits.shouldRemind(glasses, HabitLog(entries = mapOf(day(2) to 5)), day(2)))
        assertFalse(Habits.shouldRemind(glasses, HabitLog(entries = mapOf(day(2) to 8)), day(2)))
    }

    @Test
    fun tidyRemindersSortsDropsRepeatsAndCaps() {
        assertEquals(listOf(t(8), t(9, 30), t(20)), Habits.tidyReminders(listOf(t(20), t(8), t(9, 30), t(8))))
        assertEquals(12, Habits.tidyReminders((0..20).map { t(it) }).size)
    }

    @Test
    fun evenlySpacesReminders() {
        assertEquals((8..20 step 2).map { t(it) }, Habits.evenly(t(8), t(20), 120))
        assertEquals(listOf(t(8), t(11), t(14), t(17), t(20)), Habits.evenly(t(8), t(20), 180))
        assertEquals(listOf(t(9)), Habits.evenly(t(9), t(9), 60))
        assertEquals(12, Habits.evenly(t(6), t(23), 30).size) // capped
        assertTrue(Habits.evenly(t(20), t(8), 60).isEmpty())
        assertEquals(t(23, 30), Habits.evenly(t(22), t(23, 59), 90).last()) // never wraps past midnight
        assertThrows(IllegalArgumentException::class.java) { Habits.evenly(t(8), t(9), 0) }
    }
}
