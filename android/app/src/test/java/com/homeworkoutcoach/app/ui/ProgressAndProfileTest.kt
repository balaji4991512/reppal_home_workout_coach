package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.ui.profile.ProfilePresenter
import com.homeworkoutcoach.app.ui.progress.ProgressPresenter
import com.homeworkoutcoach.app.ui.progress.ProgressPresenter.Companion.DAY_MS
import com.homeworkoutcoach.app.ui.progress.Range
import com.homeworkoutcoach.engine.Bench
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Split
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressPresenterTest {
    private val lib = Library.default
    private val presenter = ProgressPresenter(lib, ZoneOffset.UTC)

    private fun day(iso: String) = LocalDate.parse(iso).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun workout(date: String, bench: List<LoggedSet>, pushups: List<LoggedSet> = emptyList()) = Workout(
        dateMillis = day(date),
        label = "A",
        plan = listOf(PlannedExercise("bench", 3, 12, 5.0), PlannedExercise("pushup", 3, 12, 0.0)),
        sets = buildMap {
            put("bench", bench)
            if (pushups.isNotEmpty()) put("pushup", pushups)
        },
    )

    private val workouts = listOf(
        // stored oldest first, but history sorts by date anyway
        workout("2026-10-01", List(3) { LoggedSet(10, 5.0, 12) }, List(3) { LoggedSet(12, 0.0, 12) }),
        workout("2026-10-08", listOf(LoggedSet(12, 5.0, 12), LoggedSet(12, 5.0, 12, Effort.HARD), LoggedSet(11, 5.0, 12))),
        workout("2026-10-15", listOf(LoggedSet(12, 5.0, 12, Effort.EASY), LoggedSet(10, 6.0, 11, Effort.HARD))),
        workout("2026-06-01", List(3) { LoggedSet(8, 5.0, 12) }),
    )

    @Test
    fun listsLoggedExercisesInLibraryOrder() {
        val items = presenter.list(workouts)
        assertEquals(listOf("bench", "pushup"), items.map { it.id })
        assertEquals("4 sessions, last 120 kg", items[0].summary)
        assertEquals("1 session, last 36 reps", items[1].summary)
        assertTrue(presenter.list(emptyList()).isEmpty())
    }

    @Test
    fun personalRecordsAndVolumes() {
        val h = presenter.history("bench", workouts)
        assertEquals(listOf(120.0, 150.0, 175.0, 120.0), h.map { it.volume })
        // best set: 40 (Jun), 50 (Oct 1, PR), 60 (Oct 8, PR), 60 (Oct 15: ties, no PR)
        assertEquals(listOf(false, true, true, false), h.map { it.pr })
    }

    @Test
    fun rangeFiltersTheChartButNotRecentSets() {
        val now = day("2026-10-20")
        val three = presenter.detail("bench", workouts, Range.QUARTER, now)
        assertEquals(3, three.points.size)
        assertEquals(4, presenter.detail("bench", workouts, Range.ALL, now).points.size)
        assertEquals(1, presenter.detail("bench", workouts, Range.WEEK, now).points.size)
        assertEquals(now - 7 * DAY_MS, day("2026-10-13"))

        // newest first, best set per workout, effort shown unless just right
        val recent = three.recent
        assertEquals(listOf("15 Oct", "8 Oct", "1 Oct", "1 Jun"), recent.map { it.date })
        assertEquals("6 kg × 10", recent[0].text)
        assertEquals("hard", recent[0].effort)
        assertEquals("5 kg × 12", recent[1].text)
        assertEquals("hard", recent[1].effort) // the later of two equal best sets
        assertEquals(listOf(false, true, true, false), recent.map { it.pr })
    }
}

class ProfilePresenterTest {
    private val lib = Library.default

    @Test
    fun summaryCurrentTargetsAndHistory() {
        val settings = UserSettings(
            started = true, name = "asha", bench = Bench.NONE, dumbbellsKg = listOf(8.0, 2.5), daysPerWeek = 4,
            excluded = listOf("rdl"),
        )
        val w = Workout(
            id = 3, dateMillis = LocalDate.parse("2026-10-24").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            label = "U1", plan = listOf(PlannedExercise("ohp", 3, 12, 8.0), PlannedExercise("dead", 3, 12, 0.0), PlannedExercise("rdl", 3, 12, 8.0)),
            sets = mapOf("ohp" to List(3) { LoggedSet(12, 8.0, 12) }, "dead" to List(3) { LoggedSet(10, 0.0, 12) }),
        )
        val states = mapOf("ohp" to ExerciseState(8.0, 11, 3), "curl" to ExerciseState(2.5), "rdl" to ExerciseState(8.0))
        val ui = ProfilePresenter(lib, zone = ZoneOffset.UTC).present(settings, states, listOf(w), weighIns = 5)
        assertEquals("A", ui.initial)
        assertEquals("asha", ui.title)
        assertEquals("Guest, not signed in", ui.subtitle)
        assertEquals("1 workout · 5 weigh-ins", ui.stats)
        assertEquals(
            listOf(
                "Beginner · 4 days a week · Auto plan",
                "No bench · 2.5, 8 kg",
                "Working around: Dumbbell Romanian deadlift",
            ),
            ui.setup,
        )
        // only trained exercises still in the plan: curl has no workout yet, rdl is left out
        assertEquals(listOf("Dumbbell overhead press" to "3 × 11 at 8 kg"), ui.current)
        assertEquals("Upper A, Sat, 24 Oct", ui.history.single().title)
        assertEquals("288 kg, 30 bodyweight reps", ui.history.single().totals)
    }

    @Test
    fun emptyProfile() {
        val ui = ProfilePresenter(lib).present(UserSettings(), emptyMap(), emptyList())
        assertEquals("R", ui.initial)
        assertEquals("Your profile", ui.title)
        assertEquals("0 workouts · 0 weigh-ins", ui.stats)
        // nothing left out: no "Working around" line
        assertEquals(listOf("Beginner · 3 days a week · Auto plan", "Flat bench · 4.5 kg"), ui.setup)
    }

    @Test
    fun longDumbbellListsAreSummarised() {
        val all = Library.default.dumbbellStepsKg
        val ui = ProfilePresenter(lib).present(
            UserSettings(bench = Bench.ADJUSTABLE, dumbbellsKg = all, split = Split.BODY_PART, daysPerWeek = 5),
            emptyMap(), emptyList(),
        )
        assertEquals(listOf("Beginner · 5 days a week · Body-part", "Adjustable bench · 15 pairs, 2.5–24 kg"), ui.setup)
    }
}

class WipeTest {
    @Test
    fun wipeErasesEverything() = runTest {
        val settings = InMemorySettingsRepository(UserSettings(started = true, theme = ThemeMode.DARK, name = "Ravi"))
        val training = InMemoryTrainingRepository()
        training.saveWorkout(Workout(dateMillis = 1, label = "A", plan = emptyList(), sets = emptyMap()), mapOf("bench" to ExerciseState(5.0)))
        training.setSwap("bench", "floor")
        PlanActions(settings, training, KeywordConcernMapper(Library.default)).wipeAll()
        assertEquals(UserSettings(), settings.settings.first())
        assertTrue(training.workouts().first().isEmpty())
        assertTrue(training.exerciseStates().first().isEmpty())
        assertTrue(training.swaps().first().isEmpty())
    }
}
