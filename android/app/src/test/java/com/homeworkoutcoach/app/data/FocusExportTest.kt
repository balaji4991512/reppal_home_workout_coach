package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.memory.InMemoryGoalRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemoryJournalRepository
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
import java.time.ZoneOffset

/** Focus sessions in the export file, in restore and in wipe. */
class FocusExportTest {
    private val lib = Library.default
    private val now = 1_793_093_256_000L
    private val ist = ZoneOffset.ofHoursMinutes(5, 30)

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
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ist, p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus)

    private suspend fun Phone.addSessions() {
        focus.save(focusSession("focus-1", start = now - 3_600_000, planned = 45, task = "Write the report"))
        focus.save(focusSession("focus-2", start = now - 1_800_000, planned = 25, actual = 7))
    }

    @Test
    fun sessionsAreExportedReadably() = runTest {
        val p = Phone().also { it.addSessions() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(2, r.focus)
        val list = Json.parseToJsonElement(r.json).jsonObject.getValue("focus").jsonArray.map { it.jsonObject }
        assertEquals(listOf("2026-10-27T13:57:36+05:30", "2026-10-27T14:27:36+05:30"), list.map { it.getValue("start").jsonPrimitive.content })
        assertEquals("Write the report", list[0].getValue("task").jsonPrimitive.content)
        assertEquals("7", list[1].getValue("actualMinutes").jsonPrimitive.content)
        assertEquals("false", list[1].getValue("completed").jsonPrimitive.content)
    }

    @Test
    fun sessionsRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addSessions() }
        val file = exporter(p).export("RepPal", now).json
        val fresh = Phone()
        fresh.focus.save(focusSession("other")) // must go
        importer(fresh).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(p.focus.sessions().first(), fresh.focus.sessions().first())
    }

    @Test
    fun aRestoreKeepsAnyTimerThatIsRunningOnThisPhone() = runTest {
        val running = ActiveFocus(startMillis = now, plannedMinutes = 25, taskTitle = "Now")
        val phone = Phone(UserSettings(started = true, guest = true, activeFocus = running))
        val file = """{"app":"RepPal","format":7,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old","activeFocus":{"startMillis":1,"plannedMinutes":60}},
            "exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[]}"""
        importer(phone).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals("Old", phone.settings.settings.first().name)
        assertEquals(running, phone.settings.settings.first().activeFocus)
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheSessionsAlone() = runTest {
        val old = """{"app":"RepPal","format":6,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],
            "habits":[],"tasks":[],"routines":[],"journal":[],"goals":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.focus)
        val p = Phone().also { it.addSessions() }
        importer(p).restore(check.restore)
        assertEquals(2, p.focus.sessions().first().size)
    }

    @Test
    fun aFileWithNoSessionsClearsThem() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addSessions() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.focus.sessions().first().isEmpty())
    }

    @Test
    fun aBadSessionInAFileChangesNothing() {
        fun file(f: String) = """{"app":"RepPal","format":7,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"focus":[$f]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        val ok = """{"id":"f","start":"2026-10-27T14:57:36+05:30","plannedMinutes":25,"actualMinutes":25,"completed":true}"""
        assertTrue(DataImport.read(lib, file(ok)) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file(ok.replace("\"actualMinutes\":25", "\"actualMinutes\":26"))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("\"actualMinutes\":25", "\"actualMinutes\":0"))))
        assertEquals(bad, DataImport.read(lib, file(ok.replace("2026-10-27T14:57:36+05:30", "later"))))
    }

    @Test
    fun wipeAlsoErasesSessions() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addSessions() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks, p.routines, p.journal, p.goals, p.focus,
        ).wipeAll()
        assertTrue(p.focus.sessions().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
