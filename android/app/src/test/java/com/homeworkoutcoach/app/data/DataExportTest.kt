package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.memory.InMemoryAuthRepository
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.data.memory.InMemoryTrainingRepository
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class DataExportTest {
    private val lib = Library.default
    private val ist = ZoneOffset.ofHoursMinutes(5, 30)
    private val now = 1_793_093_256_000L // 2026-10-27 14:57:36 IST

    @Test
    fun exportsEverythingReadably() = runTest {
        val settings = InMemorySettingsRepository(UserSettings(started = true, name = "Balaji", heightCm = 171))
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        val auth = InMemoryAuthRepository(Account("me@gmail.com", "Me"))
        training.setSwap("bench", "floor")
        training.saveWorkout(
            Workout(
                dateMillis = now - 3_600_000, label = "A",
                plan = listOf(PlannedExercise("floor", 3, 12, 5.0)),
                sets = mapOf("floor" to listOf(LoggedSet(12, 5.0, 12, Effort.EASY), LoggedSet(10, 5.0, 12, Effort.HARD, "wrist"))),
                painIds = setOf("floor"),
            ),
            mapOf("floor" to ExerciseState(6.0, 11, 3)),
        )
        body.add(BodyWeight(dateMillis = now, weightKg = 84.5, source = WeightSource.SCALE))

        val r = DataExporter(lib, settings, training, body, auth, ist).export("RepPal", now)
        assertEquals(1, r.workouts)
        assertEquals(1, r.weighIns)

        val o = Json.parseToJsonElement(r.json).jsonObject
        assertEquals("RepPal", o.getValue("app").jsonPrimitive.content)
        assertEquals(4, o.getValue("format").jsonPrimitive.int)
        assertEquals("2026-10-27T14:57:36+05:30", o.getValue("exportedAt").jsonPrimitive.content)
        assertEquals("me@gmail.com", o.getValue("account").jsonPrimitive.content)
        assertEquals("Balaji", o.getValue("settings").jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("floor", o.getValue("swaps").jsonObject.getValue("bench").jsonPrimitive.content)
        assertEquals(
            "Dumbbell floor press",
            o.getValue("exerciseStates").jsonObject.getValue("floor").jsonObject.getValue("name").jsonPrimitive.content,
        )

        val w = o.getValue("workouts").jsonArray.single().jsonObject
        assertEquals("Full body A", w.getValue("day").jsonPrimitive.content)
        assertEquals("2026-10-27T13:57:36+05:30", w.getValue("date").jsonPrimitive.content)
        val sets = w.getValue("sets").jsonArray.map { it.jsonObject }
        assertEquals(listOf("easy", "hard"), sets.map { it.getValue("effort").jsonPrimitive.content })
        assertEquals("wrist", sets[1].getValue("comment").jsonPrimitive.content)
        assertEquals("floor", w.getValue("pain").jsonArray.single().jsonPrimitive.content)

        val weigh = o.getValue("weighIns").jsonArray.single().jsonObject
        assertEquals("scale", weigh.getValue("source").jsonPrimitive.content)
        assertEquals("84.5", weigh.getValue("weightKg").jsonPrimitive.content)
    }

    @Test
    fun exportThenRestoreGivesTheSameData() = runTest {
        // a phone with data
        val settings = InMemorySettingsRepository(UserSettings(started = true, name = "Balaji", heightCm = 171, sessionIndex = 1))
        val training = InMemoryTrainingRepository()
        val body = InMemoryBodyRepository()
        training.setSwap("bench", "floor")
        val workout = Workout(
            dateMillis = now - 3_600_000, label = "M:chest,triceps#1",
            plan = listOf(PlannedExercise("floor", 3, 12, 5.0), PlannedExercise("pushup", 3, 12, 0.0)),
            sets = linkedMapOf(
                "floor" to listOf(LoggedSet(12, 5.0, 12, Effort.EASY), LoggedSet(11, 6.0, 11, Effort.HARD, "wrist")),
                "pushup" to listOf(LoggedSet(9, 0.0, 12)),
            ),
            painIds = setOf("pushup"),
        )
        training.saveWorkout(workout, mapOf("floor" to ExerciseState(6.0, 11, 3)))
        body.add(BodyWeight(dateMillis = now, weightKg = 84.05, source = WeightSource.SCALE))
        body.add(BodyWeight(dateMillis = now + 60_000, weightKg = 84.3, source = WeightSource.MANUAL))
        val file = DataExporter(lib, settings, training, body, InMemoryAuthRepository(), ist).export("RepPal", now).json

        // a fresh phone, signed in as a guest, with some other data that must go
        val s2 = InMemorySettingsRepository(UserSettings(guest = true, name = "Other"))
        val t2 = InMemoryTrainingRepository()
        val b2 = InMemoryBodyRepository()
        t2.putExerciseStates(mapOf("rdl" to ExerciseState(20.0)))
        b2.add(BodyWeight(dateMillis = 1, weightKg = 99.0, source = WeightSource.MANUAL))

        val check = DataImport.read(lib, file) as ImportCheck.Ok
        DataImporter(s2, t2, b2).restore(check.restore)

        assertEquals(settings.settings.first().copy(guest = true), s2.settings.first())
        assertEquals(training.exerciseStates().first(), t2.exerciseStates().first())
        assertEquals(training.swaps().first(), t2.swaps().first())
        val restored = t2.workouts().first().single()
        assertEquals(workout.copy(id = restored.id), restored)
        assertEquals(body.weights().first().map { it.copy(id = 0) }, b2.weights().first().map { it.copy(id = 0) })
        assertEquals(now, check.restore.exportedAt)
    }

    @Test
    fun badFilesChangeNothing() {
        assertEquals(ImportCheck.Bad("This isn’t an export file from this app."), DataImport.read(lib, "{\"hello\": 1}"))
        assertEquals(ImportCheck.Bad("This isn’t an export file from this app."), DataImport.read(lib, "not json"))
        val newer = """{"app":"RepPal","format":99,"exportedAt":"2026-10-27T14:57:36+05:30","account":null,
            "settings":{},"exerciseStates":{},"swaps":{},"workouts":[],"weighIns":[]}"""
        assertEquals(
            ImportCheck.Bad("This file comes from a newer version of the app. Update the app, then try again."),
            DataImport.read(lib, newer),
        )
        val unknown = newer.replace("\"format\":99", "\"format\":1").replace("\"swaps\":{}", "\"swaps\":{\"bench\":\"teleport\"}")
        assertEquals(
            ImportCheck.Bad("This file has exercises this version doesn’t know: teleport."),
            DataImport.read(lib, unknown),
        )
        val badDate = newer.replace("\"format\":99", "\"format\":1").replace("2026-10-27T14:57:36+05:30", "yesterday")
        assertEquals(ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed."), DataImport.read(lib, badDate))
    }

    @Test
    fun guestAndEmpty() = runTest {
        val r = DataExporter(
            lib, InMemorySettingsRepository(), InMemoryTrainingRepository(), InMemoryBodyRepository(), InMemoryAuthRepository(), ist,
        ).export("RepPal", now)
        val o = Json.parseToJsonElement(r.json).jsonObject
        assertEquals("null", o.getValue("account").toString())
        assertEquals(0, o.getValue("workouts").jsonArray.size)
        assertEquals("reppal-export-2026-10-27.json", DataExport.fileName("RepPal", now, ist))
    }
}
