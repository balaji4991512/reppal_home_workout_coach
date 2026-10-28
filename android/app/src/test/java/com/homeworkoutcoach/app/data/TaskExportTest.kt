package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
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
import java.time.LocalTime
import java.time.ZoneOffset

/** Planner tasks in the export file, in restore and in wipe. */
class TaskExportTest {
    private val lib = Library.default
    private val now = 1_793_093_256_000L
    private val day = LocalDate.of(2026, 10, 28)

    private class Phone(initial: UserSettings = UserSettings(started = true, guest = true)) {
        val settings = InMemorySettingsRepository(initial)
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val habits = InMemoryHabitRepository()
        val tasks = InMemoryTaskRepository()
    }

    private fun exporter(p: Phone) = DataExporter(
        Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.ofHoursMinutes(5, 30), p.habits, p.tasks,
    )

    private fun importer(p: Phone) = DataImporter(p.settings, p.training, p.body, p.habits, p.tasks)

    private suspend fun Phone.addTasks() {
        tasks.save(task("a", "Call the bank", day, LocalTime.of(17, 30), "ask about the card", createdAt = 5))
        tasks.save(task("b", "Buy milk", day.minusDays(1), null, "", done = true, createdAt = 2))
        tasks.save(task("c", "Book flights", day.plusDays(3), LocalTime.of(9, 0), "", createdAt = 8))
        tasks.save(task("d", "Deep work", day, LocalTime.of(15, 0), "", createdAt = 9, endTime = LocalTime.of(17, 0)))
        tasks.save(task("e", "Send the report", day, null, "", createdAt = 10, endTime = LocalTime.of(18, 0)))
    }

    @Test
    fun tasksAreExportedReadably() = runTest {
        val p = Phone().also { it.addTasks() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(5, r.tasks)
        val o = Json.parseToJsonElement(r.json).jsonObject
        val tasks = o.getValue("tasks").jsonArray.map { it.jsonObject }
        // oldest day first
        assertEquals(
            listOf("Buy milk", "Call the bank", "Deep work", "Send the report", "Book flights"),
            tasks.map { it.getValue("title").jsonPrimitive.content },
        )
        assertEquals("2026-10-27", tasks[0].getValue("date").jsonPrimitive.content)
        assertEquals("17:30", tasks[1].getValue("time").jsonPrimitive.content)
        assertEquals("ask about the card", tasks[1].getValue("note").jsonPrimitive.content)
        assertEquals("true", tasks[0].getValue("done").jsonPrimitive.content)
        // a block has a start and an end; a deadline only an end
        assertEquals("15:00", tasks[2].getValue("time").jsonPrimitive.content)
        assertEquals("17:00", tasks[2].getValue("endTime").jsonPrimitive.content)
        assertEquals("18:00", tasks[3].getValue("endTime").jsonPrimitive.content)
        assertEquals(null, tasks[1]["endTime"]?.jsonPrimitive?.content?.takeIf { it != "null" })
    }

    @Test
    fun tasksRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addTasks() }
        val file = exporter(p).export("RepPal", now).json

        val fresh = Phone()
        fresh.tasks.save(task("other")) // must go
        val check = DataImport.read(lib, file) as ImportCheck.Ok
        importer(fresh).restore(check.restore)

        assertEquals(p.tasks.tasks().first(), fresh.tasks.tasks().first())
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheTasksAlone() = runTest {
        val old = """{"app":"RepPal","format":2,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"habits":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertNull(check.restore.tasks)

        val p = Phone().also { it.addTasks() }
        importer(p).restore(check.restore)
        assertEquals("Old", p.settings.settings.first().name)
        assertEquals(5, p.tasks.tasks().first().size)
    }

    @Test
    fun aFileWithNoTasksClearsThem() = runTest {
        val file = exporter(Phone()).export("RepPal", now).json
        val p = Phone().also { it.addTasks() }
        importer(p).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.tasks.tasks().first().isEmpty())
    }

    @Test
    fun aBadTaskInAFileChangesNothing() {
        fun file(task: String) = """{"app":"RepPal","format":3,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"tasks":[$task]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        assertTrue(DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"2026-10-28"}""")) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file("""{"id":"a","title":" ","date":"2026-10-28"}""")))
        assertEquals(bad, DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"tomorrow"}""")))
        assertEquals(bad, DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"2026-10-28","time":"half past five"}""")))
        // an end before the start, or one that is not a time
        assertEquals(bad, DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"2026-10-28","time":"17:00","endTime":"16:00"}""")))
        assertEquals(bad, DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"2026-10-28","endTime":"late"}""")))
        assertTrue(DataImport.read(lib, file("""{"id":"a","title":"Milk","date":"2026-10-28","endTime":"18:00"}""")) is ImportCheck.Ok)
    }

    @Test
    fun wipeAlsoErasesTasks() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi")).also { it.addTasks() }
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, KeywordConcernMapper(lib), p.body, p.habits, p.tasks,
        ).wipeAll()
        assertTrue(p.tasks.tasks().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
