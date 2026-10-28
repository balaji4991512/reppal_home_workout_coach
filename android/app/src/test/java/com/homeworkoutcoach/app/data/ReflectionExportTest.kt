package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.memory.InMemoryGoalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryReflectionRepository
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
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

/** Weekly reflections in the export file, in restore and in wipe. */
class ReflectionExportTest {
    private val lib = Library.default
    private val now = 1_793_093_256_000L
    private val mon = LocalDate.of(2026, 10, 26)

    private class Phone(initial: UserSettings = UserSettings(started = true, guest = true)) {
        val settings = InMemorySettingsRepository(initial)
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val habits = InMemoryHabitRepository()
        val tasks = InMemoryTaskRepository()
        val routines = InMemoryRoutineRepository()
        val journal = InMemoryJournalRepository()
        val goals = InMemoryGoalRepository()
        val focus = InMemoryFocusRepository()
        val reflections = InMemoryReflectionRepository()
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.UTC,
        p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus, p.reflections,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus, p.reflections)

    private suspend fun Phone.addReflections() {
        reflections.save(reflection(mon, "Trained three times", "Sleep earlier"))
        reflections.save(reflection(mon.minusDays(7), "", "Less phone"))
    }

    @Test
    fun reflectionsAreExportedReadably() = runTest {
        val p = Phone().also { it.addReflections() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(2, r.reflections)
        val list = Json.parseToJsonElement(r.json).jsonObject.getValue("reflections").jsonArray.map { it.jsonObject }
        assertEquals(listOf("2026-10-19", "2026-10-26"), list.map { it.getValue("week").jsonPrimitive.content })
        assertEquals("Less phone", list[0].getValue("differently").jsonPrimitive.content)
        assertEquals("Trained three times", list[1].getValue("wentWell").jsonPrimitive.content)
    }

    @Test
    fun reflectionsRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addReflections() }
        val file = exporter(p).export("RepPal", now).json
        val fresh = Phone()
        fresh.reflections.save(reflection(mon.minusDays(50))) // must go
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(p.reflections.reflections().first(), fresh.reflections.reflections().first())
    }

    @Test
    fun theChosenFocusTravelsWithTheSettings() = runTest {
        val p = Phone(UserSettings(started = true, guest = true, characterFocus = CharacterFocus(mon.toEpochDay(), "REFLECTION")))
        val file = exporter(p).export("RepPal", now).json
        val fresh = Phone()
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(CharacterFocus(mon.toEpochDay(), "REFLECTION"), fresh.settings.settings.first().characterFocus)
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheReflectionsAlone() = runTest {
        val old = """{"app":"RepPal","format":7,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],
            "habits":[],"tasks":[],"routines":[],"journal":[],"goals":[],"focus":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.reflections)
        val p = Phone().also { it.addReflections() }
        importer(p).restore(check.restore)
        assertEquals(2, p.reflections.reflections().first().size)
    }

    @Test
    fun aFileWithNoReflectionsClearsThem() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addReflections() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.reflections.reflections().first().isEmpty())
    }

    @Test
    fun aBadReflectionInAFileChangesNothing() {
        fun file(r: String) = """{"app":"RepPal","format":8,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"reflections":[$r]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        assertTrue(DataImport.read(lib, file("""{"week":"2026-10-26","wentWell":"x"}""")) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file("""{"week":"2026-10-26"}""")))
        assertEquals(bad, DataImport.read(lib, file("""{"week":"last week","wentWell":"x"}""")))
    }

    @Test
    fun wipeAlsoErasesReflections() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addReflections() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus, p.reflections,
        ).wipeAll()
        assertTrue(p.reflections.reflections().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
