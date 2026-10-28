package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryGoalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Goals in the export file, in restore and in wipe. */
class GoalExportTest {
    private val lib = Library.default
    private val now = 1_793_093_256_000L
    private val day = LocalDate.of(2026, 10, 28)

    private class Phone(initial: UserSettings = UserSettings(started = true, guest = true)) {
        val settings = InMemorySettingsRepository(initial)
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val habits = InMemoryHabitRepository()
        val tasks = InMemoryTaskRepository()
        val routines = InMemoryRoutineRepository()
        val journal = InMemoryJournalRepository()
        val goals = InMemoryGoalRepository()
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.ofHoursMinutes(5, 30),
        p.habits, p.tasks, p.routines, p.journal, p.goals,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks, p.routines, p.journal, p.goals)

    private suspend fun Phone.addGoals() {
        goals.save(goal("w", "Twelve workouts", GoalKind.WORKOUTS, 12.0, day, day.plusDays(29)))
        goals.save(goal("h", "Read", GoalKind.HABIT, 20.0, day, day.plusDays(59)))
        goals.save(goal("kg", "Reach 80 kg", GoalKind.WEIGHT, end = day.plusDays(89)))
    }

    @Test
    fun goalsAreExportedReadably() = runTest {
        val p = Phone().also { it.addGoals() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(3, r.goals)
        val list = Json.parseToJsonElement(r.json).jsonObject.getValue("goals").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Twelve workouts", "Read", "Reach 80 kg"), list.map { it.getValue("title").jsonPrimitive.content })
        assertEquals("workouts", list[0].getValue("kind").jsonPrimitive.content)
        assertEquals("2026-11-26", list[0].getValue("end").jsonPrimitive.content)
        assertEquals("read", list[1].getValue("habit").jsonPrimitive.content)
        assertEquals("86.0", list[2].getValue("startWeightKg").jsonPrimitive.content)
    }

    @Test
    fun goalsRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addGoals() }
        val file = exporter(p).export("RepPal", now).json
        val fresh = Phone()
        fresh.goals.save(goal("other")) // must go
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(p.goals.goals().first(), fresh.goals.goals().first())
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheGoalsAlone() = runTest {
        val old = """{"app":"RepPal","format":5,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],
            "habits":[],"tasks":[],"routines":[],"journal":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.goals)
        val p = Phone().also { it.addGoals() }
        importer(p).restore(check.restore)
        assertEquals("Old", p.settings.settings.first().name)
        assertEquals(3, p.goals.goals().first().size)
    }

    @Test
    fun aFileWithNoGoalsClearsThem() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addGoals() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.goals.goals().first().isEmpty())
    }

    @Test
    fun aBadGoalInAFileChangesNothing() {
        fun file(g: String) = """{"app":"RepPal","format":6,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"goals":[$g]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        val ok = """{"id":"g","title":"Twelve","kind":"workouts","target":12.0,"start":"2026-10-28","end":"2026-11-26"}"""
        assertTrue(DataImport.read(lib, file(ok)) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file(ok.replace("workouts", "swimming"))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("12.0", "0.0"))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("\"end\":\"2026-11-26\"", "\"end\":\"2026-10-01\""))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("workouts", "habit")))) // a habit goal with no habit
        assertEquals(bad, DataImport.read(lib, file(ok.replace("2026-10-28", "soon"))))
    }

    @Test
    fun wipeAlsoErasesGoals() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addGoals() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks, p.routines, p.journal, p.goals,
        ).wipeAll()
        assertTrue(p.goals.goals().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
