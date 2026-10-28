package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.plan.WorkoutSession
import com.homeworkoutcoach.app.ui.workout.DonePresenter
import com.homeworkoutcoach.app.ui.workout.MakeUpOption
import com.homeworkoutcoach.app.ui.workout.WorkoutState
import com.homeworkoutcoach.app.ui.workout.WorkoutViewModel
import com.homeworkoutcoach.app.ui.workout.recordJson
import com.homeworkoutcoach.app.voice.VoiceError
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Day
import com.homeworkoutcoach.engine.DayItem
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.Equipment
import com.homeworkoutcoach.engine.ExerciseOutcome
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.KeywordSetParser
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LiveAdjustment
import com.homeworkoutcoach.engine.LiveTarget
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.SetFlag
import com.homeworkoutcoach.engine.SetRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutSessionTest {
    private val lib = Library.default
    private val kit = Equipment(Bench.FLAT, setOf(5.0, 6.0))
    private val day = Day("A", listOf(DayItem("Push", "bench"), DayItem("Core", "dead")), emptyList())
    private fun start() = WorkoutSession.start(lib, kit, day) { id ->
        if (id == "bench") ExerciseState(5.0) else ExerciseState(0.0)
    }

    private fun set(reps: Int, effort: Effort = Effort.JUST_RIGHT, pain: Boolean = false, kg: Double? = null) =
        SetRecord(reps, kg, effort, if (pain) setOf(SetFlag.PAIN) else emptySet())

    @Test
    fun easyAtTheTopMovesUpForTheNextSet() {
        val s = start().save(set(12, Effort.EASY))
        assertEquals(0, s.index)
        assertEquals(1, s.setIndex)
        assertEquals(LiveTarget(6.0, 11), s.target)
        // the saved set keeps the weight and target that applied to it
        assertEquals(LoggedSet(12, 5.0, 12, Effort.EASY), s.sets.getValue("bench").single())
        assertEquals(
            "Easy at the top of the range, so the next set moves up to 6 kg for 11 reps.",
            Copy.liveNote(lib.exercise("bench"), LiveTarget(5.0, 12), s.lastAdjustment!!),
        )
    }

    @Test
    fun liveNotesMatchThePrototype() {
        val bench = lib.exercise("bench")
        val dead = lib.exercise("dead")
        val cp = LiveTarget(5.0, 10)
        assertEquals("Short of 10, so the next set stays at 5 kg, 10 reps.", Copy.liveNote(bench, cp, LiveAdjustment.ShortOfTarget(cp)))
        assertEquals("Next set stays at 5 kg, 10 reps.", Copy.liveNote(bench, cp, LiveAdjustment.NotEasy(cp)))
        assertEquals("Easy, so the next set goes up to 11 reps at 5 kg.", Copy.liveNote(bench, cp, LiveAdjustment.AddRep(LiveTarget(5.0, 11))))
        assertEquals("Easy, so the next set goes up to 13 reps.", Copy.liveNote(dead, LiveTarget(0.0, 12), LiveAdjustment.AddRep(LiveTarget(0.0, 13))))
        assertEquals(
            "Easy, but there is nothing heavier to move up to, so the next set stays at 6 kg, 12 reps.",
            Copy.liveNote(bench, LiveTarget(6.0, 12), LiveAdjustment.NothingHeavier(LiveTarget(6.0, 12))),
        )
        assertNull(Copy.liveNote(bench, cp, LiveAdjustment.Pain(cp)))
    }

    @Test
    fun painStopsTheExerciseAndSkipsToTheNext() {
        val s = start().save(set(8, pain = true))
        assertEquals(1, s.index)
        assertEquals(0, s.setIndex)
        assertEquals(listOf("bench"), s.pain)
        assertEquals("dead", s.exercise.id)
    }

    @Test
    fun finishGivesNextSessionAndKeepsPainedStatesAlone() {
        var s = start()
        repeat(3) { s = s.save(set(12)) }
        s = s.save(set(12, pain = true))
        assertTrue(s.finished)
        val r = s.finish(dateMillis = 42)
        assertEquals(mapOf("bench" to ExerciseState(6.0, 11, 3)), r.nextStates)
        assertEquals(listOf("dead"), r.painIds)
        assertEquals(setOf("dead"), r.workout.painIds)
        assertEquals(42L, r.workout.dateMillis)

        val done = DonePresenter(lib).present(r, emptyList())
        assertEquals(180L, done.totalKg)
        assertEquals("First time doing this session, so there is nothing to compare yet.", done.kgComparison)
        assertEquals("12 bodyweight reps", done.repsLine)
        val bench = done.changes[0]
        assertEquals("Reps: 12, 12, 12. Load 180 kg. Was 3 × 12 at 5 kg.", bench.summary)
        assertEquals("Volume was 180 kg, so next time aims for about 10% more than that: 198 kg (+10%).", bench.message)
        assertEquals("Next: 3 × 11 at 6 kg", bench.next)
        assertTrue(bench.up)
        val dead = done.changes[1]
        assertEquals("Next: Swapped out next time", dead.next)
        assertEquals(Copy.PAIN_NEXT, dead.message)
        assertEquals(listOf(ExerciseOutcome.HIT, ExerciseOutcome.STOPPED), done.card.rows.map { it.outcome })
    }

    @Test
    fun comparesWithTheLastSessionOfTheSameDay() {
        var s = start()
        repeat(3) { s = s.save(set(12)) }
        repeat(3) { s = s.save(set(12)) }
        val first = s.finish(1).workout
        var t = start()
        repeat(3) { t = t.save(set(10)) }
        repeat(3) { t = t.save(set(12)) }
        val done = DonePresenter(lib).present(t.finish(2), listOf(first))
        assertEquals("down 17% from last time", done.kgComparison) // 150 vs 180
        assertEquals("Volume was 150 of 180 kg, short of the target, so the same plan again.", done.changes[0].message)
        assertEquals("Next: 3 × 12 at 5 kg", done.changes[0].next)
    }

    @Test
    fun savedDataJson() {
        assertEquals(
            "{\n    \"reps\": 12,\n    \"weight_kg\": 5,\n    \"effort\": \"just right\",\n    \"flags\": [],\n    \"comment\": \"\"\n}",
            recordJson(SetRecord(12, 5.0, Effort.JUST_RIGHT)),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val lib = Library.default
    private val settings = InMemorySettingsRepository(
        UserSettings(started = true, bench = Bench.FLAT, dumbbellsKg = listOf(5.0, 6.0), daysPerWeek = 1),
    )
    private val training = InMemoryTrainingRepository()
    private val actions = PlanActions(settings, training, KeywordConcernMapper(lib))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Monday 26 Oct 2026, 09:00 UTC: a 1-day plan trains on Mondays. */
    private val monday = java.time.LocalDate.of(2026, 10, 26).atTime(9, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    private fun vm(at: Long = monday, makeUp: Long? = null) =
        WorkoutViewModel(lib, Planner(lib), settings, training, actions, KeywordSetParser, java.time.ZoneOffset.UTC, { at }, makeUp)
    private fun WorkoutViewModel.logging() = state.value as WorkoutState.Logging

    @Test
    fun onADayWithNoWorkoutNothingCanBeStarted() {
        // Tuesday: the 1-day plan only trains on Monday
        val tuesday = monday + 24 * 3_600_000L
        val s = vm(tuesday).state.value as WorkoutState.NotToday
        assertEquals("Rest day", s.title)
        assertEquals("There is no workout today. No more workouts this week.", s.note)
    }

    @Test
    fun aWorkoutAlreadyDoneTodayIsNotOfferedAgain() = kotlinx.coroutines.test.runTest {
        training.saveWorkout(Workout(dateMillis = monday, label = "F", plan = emptyList(), sets = emptyMap()), emptyMap())
        val s = vm().state.value as WorkoutState.NotToday
        assertEquals("Workout done", s.title)
        assertTrue(s.note.startsWith("Today’s workout is done."))
    }

    @Test
    fun chipsTextAndErrors() {
        val vm = vm()
        // 1 day a week: full body F, bench press first; its 8 kg start snaps down to 6 kg
        assertEquals("bench", vm.logging().session.exercise.id)

        vm.onCheck()
        assertEquals("Say or type how the set went.", vm.logging().input.error)
        vm.onText("ten reps at 8 kg")
        vm.onCheck()
        assertEquals("You don’t have a 8 kg dumbbell. Try again or edit your equipment.", vm.logging().input.error)
        vm.onText("felt ok")
        vm.onCheck()
        assertEquals("I couldn’t find the rep count. Try again, like “10 reps, hard”.", vm.logging().input.error)
        vm.onText("10 reps at 6 kg, hard, left wrist tight")
        vm.onCheck()
        assertEquals(SetRecord(10, 6.0, Effort.HARD, comment = "left wrist tight"), vm.logging().input.preview)

        // after a note, chips adjust it (keeping its weight and comment)
        vm.onReps(12)
        vm.onEffort(Effort.EASY)
        assertEquals(SetRecord(12, 6.0, Effort.EASY, comment = "left wrist tight"), vm.logging().input.preview)
    }

    @Test
    fun chipsAloneBuildTheRecordAndTappingAgainClears() {
        val vm = vm()
        vm.onReps(12)
        vm.onEffort(Effort.EASY)
        assertEquals(SetRecord(12, 6.0, Effort.EASY), vm.logging().input.preview)
        vm.onEffort(Effort.EASY)
        assertEquals(Effort.JUST_RIGHT, vm.logging().input.preview!!.effort)
        vm.onReps(12)
        assertNull(vm.logging().input.preview)
    }

    @Test
    fun voicePhraseIsCheckedAtOnceAndWinsOverChips() {
        val vm = vm()
        vm.onReps(12)
        vm.onVoiceListening()
        assertTrue(vm.logging().input.listening)
        assertNull(vm.logging().input.preview)
        vm.onVoicePartial("ten reps")
        assertEquals("ten reps", vm.logging().input.text)
        vm.onVoiceResult("ten reps, hard, left wrist tight")
        val input = vm.logging().input
        assertEquals(false, input.listening)
        assertNull(input.reps)
        assertEquals("ten reps, hard, left wrist tight", input.text)
        // one-line readback before Save set: weight defaults to the current target
        assertEquals(SetRecord(10, 6.0, Effort.HARD, comment = "left wrist tight"), input.preview)
    }

    @Test
    fun chipsFollowAndAdjustASpokenNote() {
        val vm = vm()
        vm.onVoiceResult("10 reps at 5 kg, easy, wrist tight")
        var input = vm.logging().input
        assertEquals(10, input.shownReps)
        assertEquals(Effort.EASY, input.shownEffort)
        assertEquals(false, input.shownPain)

        // tapping chips edits the spoken note, keeping its weight and comment
        vm.onEffort(Effort.HARD)
        vm.onReps(9)
        vm.onPain()
        input = vm.logging().input
        assertEquals(SetRecord(9, 5.0, Effort.HARD, setOf(com.homeworkoutcoach.engine.SetFlag.PAIN), "wrist tight"), input.preview)
        assertEquals(Effort.HARD, input.shownEffort)
        assertTrue(input.shownPain)
        vm.onPain()
        assertEquals(false, vm.logging().input.preview!!.pain)
    }

    @Test
    fun voiceProblemsSayWhatToDo() {
        val vm = vm()
        vm.onVoiceListening()
        vm.onVoiceError(VoiceError.NOT_HEARD)
        assertEquals("Couldn’t hear that. Try again or type it.", vm.logging().input.error)
        assertEquals(false, vm.logging().input.listening)
        vm.onVoiceError(VoiceError.NO_PERMISSION)
        assertEquals("The microphone is off for this app. Type instead, or allow it in Settings.", vm.logging().input.error)
        // a phrase with no rep count gets the usual hint
        vm.onVoiceResult("that felt fine")
        assertEquals("I couldn’t find the rep count. Try again, like “10 reps, hard”.", vm.logging().input.error)
        // pain words are flagged in the readback
        vm.onVoiceResult("eight reps, sharp pain in my shoulder")
        assertTrue(vm.logging().input.preview!!.pain)
    }

    @Test
    fun wholeWorkoutSavesHistoryStatesAndExclusions() = runTest {
        val vm = vm()
        var sets = 0
        while (vm.state.value is WorkoutState.Logging) {
            val l = vm.logging()
            // report pain on the first set of the second exercise
            if (l.session.index == 1) vm.onPain()
            vm.onReps(l.session.target.targetReps)
            vm.onSave()
            sets++
        }
        assertTrue(vm.state.value is WorkoutState.Done)
        val saved = training.workouts().first().single()
        assertEquals("F", saved.label)
        assertEquals(monday, saved.dateMillis)
        val s = settings.settings.first()
        assertEquals(1, s.sessionIndex)
        val pained = saved.painIds.single()
        assertTrue(pained in s.excluded && pained in s.painExcluded)
        // 3 x 12 at 6 kg with nothing heavier: one more set next time, 4 x 10 at 6 kg (240 kg, +11%)
        assertEquals(ExerciseState(6.0, 10, 4), training.exerciseStates().first()["bench"])
        assertTrue(sets > 3)
    }

    private val tuesday = monday + 24 * 3_600_000L
    private val mondayDay = java.time.LocalDate.of(2026, 10, 26).toEpochDay()

    @Test
    fun aSkippedWorkoutCanBeMadeUpLaterInTheWeekAndIsSavedAsSuch() = runTest {
        settings.update { it.copy(planStartEpochDay = mondayDay - 30) } // the plan has been running a while
        // the 1-day plan trains on Monday; on Tuesday Monday's workout is offered as a make-up
        val rest = vm(tuesday).state.value as WorkoutState.NotToday
        assertEquals(listOf(MakeUpOption(mondayDay, "Mon: Full body")), rest.makeUps)

        val vm = vm(tuesday, makeUp = mondayDay)
        assertEquals("bench", vm.logging().session.exercise.id) // Monday's own session
        while (vm.state.value is WorkoutState.Logging) {
            val l = vm.logging()
            vm.onReps(l.session.target.targetReps)
            vm.onSave()
        }
        val saved = training.workouts().first().single()
        assertEquals(tuesday, saved.dateMillis) // done on Tuesday
        assertEquals(mondayDay, saved.madeUpForEpochDay) // for Monday

        // nothing is left to make up, and the same day cannot be made up twice
        val again = vm(tuesday).state.value as WorkoutState.NotToday
        assertTrue(again.makeUps.isEmpty())
        assertEquals("Nothing to make up", (vm(tuesday, makeUp = mondayDay).state.value as WorkoutState.NotToday).title)
    }

    @Test
    fun aWorkoutFromAnEarlierWeekCannotBeMadeUp() {
        val nextTuesday = tuesday + 7 * 24 * 3_600_000L
        val s = vm(nextTuesday, makeUp = mondayDay).state.value as WorkoutState.NotToday
        assertEquals("Nothing to make up", s.title)
    }

    @Test
    fun aMakeUpDoesNotCountAsTodaysOwnWorkout() = runTest {
        settings.update { it.copy(planStartEpochDay = mondayDay - 30) }
        // Monday made up on Tuesday: Monday's workout is no longer offered, and nothing marks Tuesday as done
        val vm = vm(tuesday, makeUp = mondayDay)
        while (vm.state.value is WorkoutState.Logging) {
            vm.onReps(vm.logging().session.target.targetReps)
            vm.onSave()
        }
        val rest = vm(tuesday).state.value as WorkoutState.NotToday
        assertEquals("Rest day", rest.title) // Tuesday is still a rest day, not "workout done"
    }
}
