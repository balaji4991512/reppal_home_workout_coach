package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryRoutineRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTaskRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.engine.JournalEntry
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

/** The journal in the export file, in restore and in wipe. */
class JournalExportTest {
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
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.ofHoursMinutes(5, 30),
        p.habits, p.tasks, p.routines, p.journal,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks, p.routines, p.journal)

    private suspend fun Phone.addEntries() {
        journal.save(JournalEntry(day, "Finished the report", "Start earlier"))
        journal.save(JournalEntry(day.minusDays(1), "", "Less phone"))
    }

    @Test
    fun theJournalIsExportedReadably() = runTest {
        val p = Phone().also { it.addEntries() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(2, r.journal)
        val list = Json.parseToJsonElement(r.json).jsonObject.getValue("journal").jsonArray.map { it.jsonObject }
        assertEquals(listOf("2026-10-27", "2026-10-28"), list.map { it.getValue("date").jsonPrimitive.content })
        assertEquals("Less phone", list[0].getValue("improve").jsonPrimitive.content)
        assertEquals("Finished the report", list[1].getValue("win").jsonPrimitive.content)
    }

    @Test
    fun theJournalRoundTripsThroughAnExportFile() = runTest {
        val p = Phone().also { it.addEntries() }
        val file = exporter(p).export("RepPal", now).json
        val fresh = Phone()
        fresh.journal.save(JournalEntry(day.minusDays(9), "other")) // must go
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(p.journal.entries().first(), fresh.journal.entries().first())
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheJournalAlone() = runTest {
        val old = """{"app":"RepPal","format":4,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],
            "habits":[],"tasks":[],"routines":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.journal)
        val p = Phone().also { it.addEntries() }
        importer(p).restore(check.restore)
        assertEquals("Old", p.settings.settings.first().name)
        assertEquals(2, p.journal.entries().first().size)
    }

    @Test
    fun aFileWithNoJournalClearsIt() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addEntries() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.journal.entries().first().isEmpty())
    }

    @Test
    fun aBadEntryInAFileChangesNothing() {
        fun file(entry: String) = """{"app":"RepPal","format":5,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"journal":[$entry]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        assertTrue(DataImport.read(lib, file("""{"date":"2026-10-28","win":"x"}""")) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file("""{"date":"2026-10-28"}"""))) // an entry with no lines
        assertEquals(bad, DataImport.read(lib, file("""{"date":"yesterday","win":"x"}""")))
    }

    @Test
    fun wipeAlsoErasesTheJournal() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addEntries() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks, p.routines, p.journal,
        ).wipeAll()
        assertTrue(p.journal.entries().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
