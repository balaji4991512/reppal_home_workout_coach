package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemoryHabitRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Library
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/** Habits in the export file, in restore and in wipe. */
class HabitExportTest {
    private val lib = Library.default
    private val ist = ZoneOffset.ofHoursMinutes(5, 30)
    private val now = 1_793_093_256_000L
    private val day = LocalDate.of(2026, 10, 27)

    private class Phone(initial: UserSettings = UserSettings(started = true, guest = true)) {
        val settings = InMemorySettingsRepository(initial)
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val habits = InMemoryHabitRepository()
    }

    private fun exporter(p: Phone) = DataExporter(Library.default, p.settings, p.training, p.body, InMemoryAuthRepository(), ZoneOffset.ofHoursMinutes(5, 30), p.habits)

    private suspend fun Phone.addHabits() {
        habits.save(storedHabit("water", name = "Water", kind = HabitKind.COUNT, target = 8, position = 0))
        habits.save(
            storedHabit(
                "gym", name = "Gym", auto = AutoSource.WORKOUT, position = 1,
                schedule = HabitSchedule.Weekdays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)),
            ),
        )
        habits.save(storedHabit("walk", name = "Walk", kind = HabitKind.TIME, target = 30, schedule = HabitSchedule.TimesPerWeek(4), position = 2, archived = true))
        habits.setAmount("water", day, 5)
        habits.setAmount("water", day.minusDays(1), 8)
        habits.setAmount("gym", day, 0) // unticked by hand
    }

    @Test
    fun habitsAreExportedReadably() = runTest {
        val p = Phone().also { it.addHabits() }
        val r = exporter(p).export("RepPal", now)
        assertEquals(3, r.habits)

        val habits = Json.parseToJsonElement(r.json).jsonObject.getValue("habits").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Water", "Gym", "Walk"), habits.map { it.getValue("name").jsonPrimitive.content })
        val water = habits[0]
        assertEquals("count", water.getValue("kind").jsonPrimitive.content)
        assertEquals(8, water.getValue("target").jsonPrimitive.int)
        assertEquals("daily", water.getValue("schedule").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(
            mapOf("2026-10-26" to 8, "2026-10-27" to 5),
            water.getValue("entries").jsonObject.mapValues { it.value.jsonPrimitive.int },
        )
        val gym = habits[1].getValue("schedule").jsonObject
        assertEquals("weekdays", gym.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("MONDAY", "WEDNESDAY"), gym.getValue("days").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("workout", habits[1].getValue("auto").jsonPrimitive.content)
        assertEquals(4, habits[2].getValue("schedule").jsonObject.getValue("times").jsonPrimitive.int)
        assertEquals(true, habits[2].getValue("archived").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun aReminderTimeSurvivesAnExportFile() = runTest {
        val p = Phone().also { it.addHabits() }
        val h = p.habits.habits().first().first()
        p.habits.save(h.copy(habit = h.habit.copy(reminder = java.time.LocalTime.of(10, 0))))
        val file = exporter(p).export("RepPal", now).json
        assertTrue(file.contains("\"reminder\": \"10:00\"") || file.contains("\"reminder\":\"10:00\""))

        val fresh = Phone()
        DataImporter(fresh.settings, fresh.training, fresh.body, fresh.habits).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertEquals(p.habits.habits().first().map { it.habit.reminder }, fresh.habits.habits().first().map { it.habit.reminder })
        assertEquals(java.time.LocalTime.of(10, 0), fresh.habits.habits().first().first { it.habit.id == h.habit.id }.habit.reminder)
    }

    @Test
    fun habitsRoundTripThroughAnExportFile() = runTest {
        val p = Phone().also { it.addHabits() }
        val file = exporter(p).export("RepPal", now).json

        val fresh = Phone()
        fresh.habits.save(storedHabit("other")) // must go
        fresh.habits.setAmount("other", day, 1)
        val check = DataImport.read(lib, file) as ImportCheck.Ok
        DataImporter(fresh.settings, fresh.training, fresh.body, fresh.habits).restore(check.restore)

        assertEquals(p.habits.habits().first(), fresh.habits.habits().first())
        assertEquals(p.habits.entries().first(), fresh.habits.entries().first())
        // the zero stays: it is what keeps a hand-unticked day unticked
        assertEquals(0, fresh.habits.entries().first().getValue("gym").getValue(day))
    }

    @Test
    fun anOlderFileRestoresAndLeavesTheHabitsAlone() = runTest {
        val old = """{"app":"RepPal","format":1,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{"started":true,"name":"Old"},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[]}"""
        val check = DataImport.read(lib, old) as ImportCheck.Ok
        assertEquals(null, check.restore.habits)

        val p = Phone().also { it.addHabits() }
        DataImporter(p.settings, p.training, p.body, p.habits).restore(check.restore)
        assertEquals("Old", p.settings.settings.first().name)
        assertEquals(3, p.habits.habits().first().size)
        assertEquals(2, p.habits.entries().first().getValue("water").size)
    }

    @Test
    fun aFileWithNoHabitsClearsThem() = runTest {
        val empty = Phone()
        val file = exporter(empty).export("RepPal", now).json
        val p = Phone().also { it.addHabits() }
        DataImporter(p.settings, p.training, p.body, p.habits).restore((DataImport.read(lib, file) as ImportCheck.Ok).restore)
        assertTrue(p.habits.habits().first().isEmpty())
        assertTrue(p.habits.entries().first().isEmpty())
    }

    @Test
    fun aBadHabitInAFileChangesNothing() {
        fun file(habit: String) = """{"app":"RepPal","format":2,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[],"habits":[$habit]}"""
        val bad = ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        val base = """"id":"w","name":"W","kind":"count","target":8,"startDate":"2026-10-20","icon":"drop","color":"blue""""
        assertTrue(DataImport.read(lib, file("{$base,\"schedule\":{\"type\":\"daily\"}}")) is ImportCheck.Ok)
        assertEquals(bad, DataImport.read(lib, file("{$base,\"schedule\":{\"type\":\"sometimes\"}}")))
        assertEquals(bad, DataImport.read(lib, file("{$base,\"schedule\":{\"type\":\"times\",\"times\":9}}")))
        assertEquals(bad, DataImport.read(lib, file("{$base,\"schedule\":{\"type\":\"daily\"},\"entries\":{\"yesterday\":1}}")))
        assertEquals(bad, DataImport.read(lib, file("{${base.replace("\"count\"", "\"juggling\"")},\"schedule\":{\"type\":\"daily\"}}")))
        assertEquals(bad, DataImport.read(lib, file("{${base.replace("8", "0")},\"schedule\":{\"type\":\"daily\"}}")))
    }

    @Test
    fun wipeAlsoErasesHabits() = runTest {
        val p = Phone(UserSettings(started = true, name = "Ravi"))
        p.addHabits()
        com.homeworkoutcoach.app.plan.PlanActions(
            p.settings, p.training, com.homeworkoutcoach.engine.KeywordConcernMapper(lib), p.body, p.habits,
        ).wipeAll()
        assertTrue(p.habits.habits().first().isEmpty())
        assertTrue(p.habits.entries().first().isEmpty())
        assertEquals(UserSettings(), p.settings.settings.first())
    }
}
