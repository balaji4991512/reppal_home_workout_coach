package com.homeworkoutcoach.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Weekly direct-set targets per level, for big and small muscles. */
data class SetTargets(val big: Int, val small: Int)

/** Weekly "sets per muscle" range shown in the planned volume card. */
data class SetRange(val low: Int, val high: Int)

/**
 * Exercise library and plan tables, loaded from docs/exercise_library.json and docs/plan_tables.json.
 * Map iteration order follows the JSON, which the builders rely on (slot order, keyword order).
 */
class Library(
    val muscleLabels: Map<Muscle, String>,
    val exercises: Map<String, Exercise>,
    val weekSequences: Map<Int, List<String>>,
    val workoutDayIndexes: Map<Int, List<Int>>,
    val dayNames: List<String>,
    val presetPlans: Map<String, Map<String, List<String>>>,
    val presetNames: Map<String, String>,
    val musclePreferenceGroups: Map<Muscle, List<List<String>>>,
    val bigMuscles: Set<Muscle>,
    val smallMuscles: Set<Muscle>,
    val weeklyDirectSetTargets: Map<Level, SetTargets>,
    val weeklySetRanges: Map<Level, SetRange>,
    val dumbbellStepsKg: List<Double>,
    val concernKeywords: Map<String, Regex>,
) {
    fun exercise(id: String): Exercise = exercises.getValue(id)

    companion object {
        const val EXERCISE_FILE = "exercise_library.json"
        const val TABLES_FILE = "plan_tables.json"

        /** The 15 settings of a 24 kg adjustable dumbbell: 2.5, 3.5, 4.5, 5.5, 6.5, 8, 9, 10, 11.5, 13.5, 16, 18, 20.5, 22.5, 24. */
        val ADJUSTABLE_DUMBBELL_STEPS_KG: List<Double> =
            listOf(2.5, 3.5, 4.5, 5.5, 6.5, 8.0, 9.0, 10.0, 11.5, 13.5, 16.0, 18.0, 20.5, 22.5, 24.0)

        /** [kg] moved to the nearest setting of the set (the lighter one when it is halfway), so an old weight still fits. */
        fun snapToDumbbellSet(kg: Double): Double =
            ADJUSTABLE_DUMBBELL_STEPS_KG.minWith(compareBy({ kotlin.math.abs(it - kg) }, { it }))

        /** The bundled data, read from the classpath. */
        val default: Library by lazy {
            fun read(name: String) = checkNotNull(Library::class.java.classLoader.getResourceAsStream(name)) {
                "Missing resource $name"
            }.bufferedReader().use { it.readText() }
            fromJson(read(EXERCISE_FILE), read(TABLES_FILE))
        }

        fun fromJson(exerciseJson: String, tablesJson: String): Library {
            val lib = Json.parseToJsonElement(exerciseJson).jsonObject
            val t = Json.parseToJsonElement(tablesJson).jsonObject

            val labels = lib.arr("muscles").associate {
                val o = it.jsonObject
                Muscle.fromKey(o.str("key")) to o.str("label")
            }
            val exercises = lib.arr("exercises").map { e ->
                val o = e.jsonObject
                Exercise(
                    id = o.str("id"),
                    name = o.str("name"),
                    needs = o.arr("needs").map { Need.fromKey(it.jsonPrimitive.content) }.toSet(),
                    bodyweight = o.getValue("bodyweight").jsonPrimitive.boolean,
                    startKg = o["startKg"].takeUnless { it == null || it is JsonNull }?.jsonPrimitive?.double,
                    primary = o.arr("primary").map { Muscle.fromKey(it.jsonPrimitive.content) },
                    secondary = o.arr("secondary").map { Muscle.fromKey(it.jsonPrimitive.content) },
                )
            }.associateBy { it.id }

            return Library(
                muscleLabels = labels,
                exercises = exercises,
                weekSequences = t.obj("weekSequences").mapKeys { it.key.toInt() }.mapValues { it.value.strings() },
                workoutDayIndexes = t.obj("workoutDayIndexes").mapKeys { it.key.toInt() }
                    .mapValues { v -> v.value.jsonArray.map { it.jsonPrimitive.int } },
                dayNames = t.getValue("dayNames").strings(),
                presetPlans = t.obj("presetPlans").mapValues { plan ->
                    plan.value.jsonObject.mapValues { it.value.strings() }
                },
                presetNames = t.obj("presetNames").mapValues { it.value.jsonPrimitive.content },
                musclePreferenceGroups = t.obj("musclePreferenceGroups").entries.associate { (k, v) ->
                    Muscle.fromKey(k) to v.jsonArray.map { it.strings() }
                },
                bigMuscles = t.getValue("bigMuscles").strings().map(Muscle::fromKey).toSet(),
                smallMuscles = t.getValue("smallMuscles").strings().map(Muscle::fromKey).toSet(),
                weeklyDirectSetTargets = t.obj("weeklyDirectSetTargets").entries.associate { (k, v) ->
                    val a = v.jsonArray.map { it.jsonPrimitive.int }
                    Level.fromKey(k) to SetTargets(a[0], a[1])
                },
                weeklySetRanges = t.obj("volumeTargetsLegacy").entries.associate { (k, v) ->
                    val a = v.jsonArray.map { it.jsonPrimitive.int }
                    Level.fromKey(k) to SetRange(a[0], a[1])
                },
                // The weights of an adjustable dumbbell set, not docs/plan_tables.json's older 1 to 24 kg list.
                dumbbellStepsKg = ADJUSTABLE_DUMBBELL_STEPS_KG,
                concernKeywords = t.obj("concernKeywordsRegex").mapValues { Regex(it.value.jsonPrimitive.content) },
            )
        }

        private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
        private fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
        private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
        private fun JsonElement.strings(): List<String> = jsonArray.map { it.jsonPrimitive.content }
    }
}
