package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.KeywordConcernMapper
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.RoutineDone
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

/** Routines in the export file, in restore and in wipe. */
class RoutineExportTest {
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
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.ofHoursMinutes(5, 30),
        p.habits, p.tasks, p.routines,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks, p.routines)

    private suspend fun Phone.addRoutines() {
        habits.save(storedHabit("water"))
        routines.save(routine("m", "Morning", DayPart.MORNING, steps = 3, position = 0, linkedTo = "water"))
        routines.save(routine("e", "Evening", DayPart.EVENING, steps = 2, position = 1))
        routines.setDone("m", day, true)
        routines.setDone("m", day.minusDays(1), true)
    }

    @Test
    fun routinesAreExportedReadably() = runTest {
        val p = Phone().also { it.addRoutines() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(2, r.routines)
        val list = Json.parseToJsonElement(r.json).jsonObject.getValue("routines").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Morning", "Evening"), list.map { it.getValue("name").jsonPrimitive.content })
        val morning = list[0]
        assertEquals("morning", morning.getValue("part").jsonPrimitive.content)
        val steps = morning.getValue("steps").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Step 0 of Morning", "Step 1 of Morning", "Step 2 of Morning"), steps.map { it.getValue("title").jsonPrimitive.content })
        assertEquals("water", steps[0].getValue("habit").jsonPrimitive.content)
        assertEquals(listOf("2026-10-27", "2026-10-28"), morning.getValue("done").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(emptyList<String>(), list[1].getValue("done").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun routinesRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addRoutines() }
        val file = exporter(p).export("RepPal", now).json

        val fresh = Phone()
        fresh.routines.save(routine("other")) // must go
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)

        assertEquals(p.routines.routines().first(), fresh.routines.routines().first())
        assertEquals(p.routines.done().first(), fresh.routines.done().first())
        assertEquals("water", fresh.routines.routines().first().first().items.first().habitId)
    }

    @Test
    fun aStepLinkedToAHabitThatIsNotThereLosesTheLink() = runTest {
        val file = """{"app":"RepPal","format":4,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"habits":[],
            "routines":[{"id":"m","name":"Morning","part":"morning","steps":[{"id":"s","title":"Drink","habit":"gone"}]}]}"""
        val p = Phone()
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertNull(p.routines.routines().first().single().items.single().habitId)
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheRoutinesAlone() = runTest {
        val old = """{"app":"RepPal","format":3,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"habits":[],"tasks":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.routines)

        val p = Phone().also { it.addRoutines() }
        importer(p).restore(check.restore)
        assertEquals("Old", p.settings.settings.first().name)
        assertEquals(2, p.routines.routines().first().size)
        assertEquals(setOf(RoutineDone("m", day), RoutineDone("m", day.minusDays(1))), p.routines.done().first())
    }

    @Test
    fun aFileWithNoRoutinesClearsThem() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addRoutines() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.routines.routines().first().isEmpty())
        assertTrue(p.routines.done().first().isEmpty())
    }

    @Test
    fun aBadRoutineInAFileChangesNothing() {
        fun file(routine: String) = """{"app":"RepPal","format":4,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"routines":[$routine]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        val ok = """{"id":"m","name":"Morning","part":"morning","steps":[{"id":"s","title":"Stretch"}]}"""
        assertTrue(DataImport.read(lib, file(ok)) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file(ok.replace("morning\"", "teatime\""))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("\"Morning\"", "\" \""))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("\"Stretch\"", "\" \""))))
        assertEquals(bad, DataImport.read(lib, file("""{"id":"m","name":"M","part":"morning","steps":[{"id":"s","title":"A"},{"id":"s","title":"B"}]}""")))
        assertEquals(bad, DataImport.read(lib, file(ok.dropLast(1) + ""","done":["yesterday"]}""")))
    }

    @Test
    fun wipeAlsoErasesRoutines() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addRoutines() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks, p.routines,
        ).wipeAll()
        assertTrue(p.routines.routines().first().isEmpty())
        assertTrue(p.routines.done().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
