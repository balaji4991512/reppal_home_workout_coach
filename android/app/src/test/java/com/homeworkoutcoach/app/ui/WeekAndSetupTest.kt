package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.SetupDraft
import com.homeworkoutcoach.app.ui.week.DayStatus
import com.homeworkoutcoach.app.ui.week.HeroKind
import com.homeworkoutcoach.app.ui.week.WeekPresenter
import com.homeworkoutcoach.app.ui.week.WeekdayUi
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.ExerciseOutcome
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Level
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.SetCountStatus
import com.homeworkoutcoach.engine.Split
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPresenterTest {
    private val lib = Library.default
    private val mon = java.time.LocalDate.of(2026, 10, 26) // a Monday: a 3-day plan trains Mon, Wed, Fri
    private val started = UserSettings(
        started = true, bench = Bench.FLAT, dumbbellsKg = listOf(5.0, 8.0), daysPerWeek = 3,
        planStartEpochDay = mon.minusDays(30).toEpochDay(),
    )

    /** The week as it looks on [today] (a Monday unless said otherwise). */
    private val presenter = object {
        fun present(
            s: UserSettings, states: Map<String, ExerciseState>, swaps: Map<String, String>, workouts: List<Workout>,
            today: java.time.LocalDate = mon,
        ) = WeekPresenter(lib).present(s, states, swaps, workouts, today, java.time.ZoneOffset.UTC)
    }

    private fun on(date: java.time.LocalDate) = Workout(
        dateMillis = date.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(), label = "A", plan = emptyList(), sets = emptyMap(),
    )

    private fun training(ui: com.homeworkoutcoach.app.ui.week.WeekUi) = ui.days.filterIsInstance<WeekdayUi.Training>()

    @Test
    fun heroAndWeekLayoutForThreeDays() {
        val ui = presenter.present(started, emptyMap(), emptyMap(), emptyList())
        assertEquals("Monday 26 October", ui.hero.when_)
        assertEquals(HeroKind.TODAY, ui.hero.kind)
        assertEquals("Full body A", ui.hero.title)
        // A with flat bench + 5/8 kg: bench, row, goblet, rdl, dead bug, shoulder press, curl -> 21 sets -> 60 min
        assertEquals("7 exercises, about 60 min", ui.hero.summary)
        assertEquals(3, ui.hero.total)
        assertEquals(0, ui.hero.done)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), ui.days.map { it.abbr })
        assertEquals(listOf("Mon", "Wed", "Fri"), training(ui).map { it.abbr })
        assertEquals(listOf(DayStatus.TODAY, DayStatus.UPCOMING, DayStatus.UPCOMING), training(ui).map { it.status })
        assertEquals(listOf("Full body A", "Full body B", "Full body A"), training(ui).map { it.name })
        assertEquals("3 × 12 at 8 kg", training(ui)[0].exercises.first { it.id == "bench" }.prescription)
        assertEquals("3 × 12", training(ui)[0].exercises.first { it.id == "dead" }.prescription)
    }

    @Test
    fun splitDayShowsFourSetsOnBigLiftsWithoutStoringThem() {
        val ui = presenter.present(started.copy(daysPerWeek = 4, split = Split.BODY_PART), emptyMap(), emptyMap(), emptyList())
        val chest = training(ui)[0]
        assertEquals("4 × 12 at 8 kg", chest.exercises.first { it.id == "bench" }.prescription)
        assertEquals(3, ui.statesToStore.getValue("bench").sets)
    }

    @Test
    fun newStatesAreStoredOnceThenStable() {
        val ui = presenter.present(started, emptyMap(), emptyMap(), emptyList())
        assertEquals(ExerciseState(8.0), ui.statesToStore["bench"])
        assertEquals(ExerciseState(0.0), ui.statesToStore["dead"])
        val again = presenter.present(started, ui.statesToStore, emptyMap(), emptyList())
        assertTrue(again.statesToStore.isEmpty())
        // a stored weight no longer owned is snapped down and stored again
        val snapped = presenter.present(started.copy(dumbbellsKg = listOf(5.0)), ui.statesToStore, emptyMap(), emptyList())
        assertEquals(ExerciseState(5.0), snapped.statesToStore["bench"])
    }

    @Test
    fun aDoneDayShowsItsSavedSessionOnItsOwnDay() {
        val w = Workout(
            dateMillis = mon.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(), label = "A",
            plan = listOf(PlannedExercise("bench", 3, 12, 8.0), PlannedExercise("dead", 3, 12, 0.0)),
            sets = mapOf(
                "bench" to List(3) { LoggedSet(12, 8.0, 12) },
                "dead" to listOf(LoggedSet(12, 0.0, 12), LoggedSet(10, 0.0, 12)),
            ),
        )
        // an older workout on another day, then Monday's, and today is Wednesday
        val ui = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon.minusDays(9)), w), today = mon.plusDays(2))
        assertEquals("Wednesday 28 October", ui.hero.when_)
        val mondaySession = training(ui)[0]
        assertEquals(DayStatus.DONE, mondaySession.status)
        val card = mondaySession.session!!
        assertEquals(listOf(ExerciseOutcome.HIT, ExerciseOutcome.SHORT), card.rows.map { it.outcome })
        assertEquals("12, 12, 12 reps at 8 kg", card.rows[0].detail)
        assertEquals("12, 10 reps · planned 3 × 12", card.rows[1].detail)
        assertEquals("288 kg lifted, 288 kg planned · 22 reps done, 36 planned", card.totals)
        assertEquals(100, card.percent)
        assertFalse(card.reached)
        assertEquals(DayStatus.TODAY, training(ui)[1].status)
        assertNull(training(ui)[1].session)
        assertEquals(1, ui.hero.done)
    }

    @Test
    fun aDayThatPassedWithoutAWorkoutIsMarkedSkipped() {
        // Thursday: Monday done, Wednesday not
        val ui = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon)), today = mon.plusDays(3))
        assertEquals(listOf(DayStatus.DONE, DayStatus.SKIPPED, DayStatus.UPCOMING), training(ui).map { it.status })
        assertNull(training(ui)[1].session) // nothing was saved for it
        assertEquals(1, ui.hero.done)
        assertEquals(3, ui.hero.total)
        // it stays skipped for the rest of the week, and nothing moved
        val friday = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon)), today = mon.plusDays(4))
        assertEquals(listOf(DayStatus.DONE, DayStatus.SKIPPED, DayStatus.TODAY), training(friday).map { it.status })
    }

    @Test
    fun aRestDayOffersNothingToStartAndSaysWhatIsNext() {
        val tuesday = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon)), today = mon.plusDays(1))
        assertEquals(HeroKind.REST, tuesday.hero.kind)
        assertEquals("Rest day", tuesday.hero.title)
        assertEquals("Next: Wed, Full body B", tuesday.hero.summary)
        assertFalse(tuesday.hero.canStart)
        // Saturday: nothing left this week
        val saturday = presenter.present(started, emptyMap(), emptyMap(), emptyList(), today = mon.plusDays(5))
        assertEquals("No more workouts this week", saturday.hero.summary)
        assertFalse(saturday.hero.canStart)
    }

    @Test
    fun todaysWorkoutDoneIsSaidSoAndNotStartableAgain() {
        val ui = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon)), today = mon)
        assertEquals(HeroKind.DONE_TODAY, ui.hero.kind)
        assertEquals("Workout done", ui.hero.title)
        assertEquals("Full body A", ui.hero.summary)
        assertFalse(ui.hero.canStart)
        assertEquals("Wed", ui.hero.nextDay)
        assertEquals(DayStatus.DONE, training(ui)[0].status)
    }

    @Test
    fun daysBeforeThePlanWasMadeAreNotShownAsSkipped() {
        // the plan was made on Wednesday: Monday was never due, so it shows as a rest day
        val s = started.copy(planStartEpochDay = mon.plusDays(2).toEpochDay())
        val ui = presenter.present(s, emptyMap(), emptyMap(), emptyList(), today = mon.plusDays(3))
        assertEquals(listOf("Wed", "Fri"), training(ui).map { it.abbr })
        assertEquals(listOf(DayStatus.SKIPPED, DayStatus.UPCOMING), training(ui).map { it.status })
        assertTrue(ui.days[0] is WeekdayUi.Rest)
        assertEquals(2, ui.hero.total) // only the sessions that were due
    }

    @Test
    fun aNewWeekStartsFresh() {
        val ui = presenter.present(started, emptyMap(), emptyMap(), listOf(on(mon)), today = mon.plusDays(7))
        assertEquals(listOf(DayStatus.TODAY, DayStatus.UPCOMING, DayStatus.UPCOMING), training(ui).map { it.status })
        assertEquals(0, ui.hero.done)
    }

    @Test
    fun aRearrangedWeekFollowsItsOwnWeekdays() {
        // Tuesday, Thursday, Saturday
        val s = started.copy(sessionWeekdays = listOf(1, 3, 5))
        val ui = presenter.present(s, emptyMap(), emptyMap(), emptyList(), today = mon.plusDays(3))
        assertEquals(listOf("Tue", "Thu", "Sat"), training(ui).map { it.abbr })
        assertEquals(listOf(DayStatus.SKIPPED, DayStatus.TODAY, DayStatus.UPCOMING), training(ui).map { it.status })
    }

    @Test
    fun swapsShowAndOfferAlternatives() {
        val ui = presenter.present(started, emptyMap(), mapOf("bench" to "floor"), emptyList())
        val row = training(ui)[0].exercises[0]
        assertEquals("floor", row.id)
        assertEquals("bench", row.baseId)
        assertTrue(row.swapped)
        assertEquals("Dumbbell bench press", row.baseName)
        assertTrue(row.alternatives.map { it.first }.containsAll(listOf("pushup", "fly")))
        assertFalse(row.alternatives.any { it.first == "floor" || it.first == "bench" })
    }

    @Test
    fun leftOutAndVolume() {
        val s = started.copy(excluded = listOf("rdl", "brow"))
        val ui = presenter.present(s, emptyMap(), emptyMap(), emptyList())
        assertEquals(listOf("Dumbbell Romanian deadlift", "Bent-over dumbbell row"), ui.leftOut)
        val chest = ui.volume.rows.first { it.muscle == "Chest" }
        // bench 3x12x8 twice (A, A) and incline unavailable on flat bench: B uses floor press 3x12x8
        assertEquals("864 kg", chest.load)
        assertEquals(10, ui.volume.rows.size)
        assertTrue(ui.volume.headline.startsWith("Lighter this week:"))
        assertTrue(ui.volume.setTarget.startsWith("Weekly set targets for beginners."))
        assertEquals("9 of 11 sets, On target", chest.sets)
        assertEquals(SetCountStatus.ON_TARGET, chest.status)
    }

    @Test
    fun emptyDayCannotStart() {
        val s = started.copy(split = Split.CUSTOM, daysPerWeek = 1, customDays = listOf(emptyList()))
        val ui = presenter.present(s, emptyMap(), emptyMap(), emptyList())
        assertFalse(ui.hero.canStart)
        assertEquals("Empty day", ui.hero.title)
    }
}

class PlanActionsTest {
    private val settings = InMemorySettingsRepository()
    private val training = InMemoryTrainingRepository()
    private val actions = PlanActions(settings, training, KeywordConcernMapper(Library.default))

    private fun draft() = SetupDraft.from(UserSettings(), Planner.DEFAULT_CUSTOM)

    @Test
    fun firstBuildStartsFreshAndMapsConcerns() = runTest {
        training.putExerciseStates(mapOf("bench" to ExerciseState(20.0)))
        actions.buildPlan(draft().copy(name = "  Asha ", concern = "lower back pain", level = Level.INTERMEDIATE, experienceOption = 2))
        val s = settings.settings.first()
        assertTrue(s.started)
        assertEquals("Asha", s.name)
        assertEquals(Level.INTERMEDIATE, s.level)
        assertTrue("rdl" in s.excluded && "brow" in s.excluded)
        assertEquals(7, s.customDays.size)
        assertTrue(training.exerciseStates().first().isEmpty())
    }

    @Test
    fun rebuildKeepsProgressAndPainExclusionsAndStartsANewWeek() = runTest {
        actions.buildPlan(draft())
        training.saveWorkout(Workout(dateMillis = 1, label = "A", plan = emptyList(), sets = emptyMap()), mapOf("bench" to ExerciseState(6.0, 11, 3)))
        settings.update { it.copy(sessionIndex = 1, painExcluded = listOf("ohp"), excluded = listOf("ohp")) }

        actions.buildPlan(draft().copy(daysPerWeek = 4, concern = "knee"))
        val s = settings.settings.first()
        assertEquals(0, s.sessionIndex)
        assertEquals(1, s.weekBase)
        assertEquals(4, s.daysPerWeek)
        assertTrue("ohp" in s.excluded && "goblet" in s.excluded)
        assertEquals(ExerciseState(6.0, 11, 3), training.exerciseStates().first()["bench"])
    }

    @Test
    fun thePlanRemembersTheDayItStartedAndOnlyRearrangingMovesIt() = runTest {
        var day = java.time.LocalDate.of(2026, 10, 26)
        val dated = PlanActions(settings, training, KeywordConcernMapper(Library.default), today = { day })
        dated.buildPlan(draft())
        assertEquals(day.toEpochDay(), settings.settings.first().planStartEpochDay)
        // editing the plan later keeps the day it first started
        day = day.plusDays(3)
        dated.buildPlan(draft().copy(daysPerWeek = 4))
        assertEquals(java.time.LocalDate.of(2026, 10, 26).toEpochDay(), settings.settings.first().planStartEpochDay)
        // putting the sessions on new days starts counting from that day
        dated.arrangeWeek(listOf(0, 1, 3, 4))
        assertEquals(day.toEpochDay(), settings.settings.first().planStartEpochDay)
    }

    @Test
    fun anOlderInstallGetsAStartDayAndNothingEarlierIsSkipped() = runTest {
        val day = java.time.LocalDate.of(2026, 10, 28)
        settings.update { it.copy(started = true) }
        val dated = PlanActions(settings, training, KeywordConcernMapper(Library.default), today = { day })
        dated.ensurePlanStart()
        assertEquals(day.toEpochDay(), settings.settings.first().planStartEpochDay)
        // a second call changes nothing
        PlanActions(settings, training, KeywordConcernMapper(Library.default), today = { day.plusDays(5) }).ensurePlanStart()
        assertEquals(day.toEpochDay(), settings.settings.first().planStartEpochDay)
        // and a phone with no plan gets none
        val fresh = InMemorySettingsRepository()
        PlanActions(fresh, training, KeywordConcernMapper(Library.default), today = { day }).ensurePlanStart()
        assertNull(fresh.settings.first().planStartEpochDay)
    }

    @Test
    fun guestChoiceIsRememberedAndSurvivesPlanBuilds() = runTest {
        assertEquals(false, settings.settings.first().guest)
        actions.continueAsGuest()
        actions.buildPlan(draft())
        assertTrue(settings.settings.first().guest)
    }

    @Test
    fun swappingBackToBaseRemovesTheSwap() = runTest {
        actions.swap("bench", "floor")
        assertEquals(mapOf("bench" to "floor"), training.swaps().first())
        actions.swap("bench", "bench")
        assertTrue(training.swaps().first().isEmpty())
    }
}
