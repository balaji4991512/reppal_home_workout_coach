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
        assertEquals(1, o.getValue("format").jsonPrimitive.int)
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
