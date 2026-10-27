package com.homeworkoutcoach.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * Compares the engine against outputs recorded from the web prototype (the reference behaviour).
 * Regenerate with `node engine/tools/golden.js` after a prototype change.
 */
class PrototypeGoldenTest {
    private val lib = Library.default
    private val planner = Planner(lib, PlanRules.prototype(lib))

    private val golden: JsonObject by lazy {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("golden.json.gz"))
        Json.parseToJsonElement(GZIPInputStream(stream).bufferedReader().use { it.readText() }).jsonObject
    }

    private fun JsonElement.str() = jsonPrimitive.content
    private fun JsonElement.strings() = jsonArray.map { it.str() }
    private fun JsonElement.kgs() = jsonArray.map { it.jsonPrimitive.double }.toSet()
    private fun JsonObject.num(key: String) = getValue(key).jsonPrimitive.double
    private fun JsonObject.integer(key: String) = getValue(key).jsonPrimitive.int

    private fun kg(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

    private fun encode(day: Day, s: PlanSettings): String =
        day.label + "|" + day.items.joinToString(";") { i ->
            val w = Progression.resolveState(lib.exercise(i.exerciseId), null, s.equipment, s.level).weightKg
            "${i.slot}:${i.exerciseId}<${i.baseId}@${kg(w)}"
        } + "|" + day.missing.joinToString(",")

    @Test
    fun weeksMatchPrototype() {
        val custom2 = listOf(
            listOf("abs", "chest", "biceps"), listOf("glutes", "quads", "hams", "abs"), listOf(),
            listOf("chest"), listOf("chest"), listOf("lats", "shoulders", "reardelts", "triceps", "biceps", "abs"), listOf("quads"),
        ).map { d -> d.map(Muscle::fromKey) }
        val mapper = KeywordConcernMapper(lib)
        val weeks = golden.getValue("weeks").jsonArray
        assertTrue(weeks.size > 4000)
        for (w in weeks) {
            val o = w.jsonObject
            val splitKey = o.getValue("split").str()
            var settings = PlanSettings(
                equipment = Equipment(
                    bench = when (o.getValue("bench").str()) {
                        "none" -> Bench.NONE
                        "flat" -> Bench.FLAT
                        else -> Bench.ADJUSTABLE
                    },
                    dumbbellsKg = o.getValue("w").kgs(),
                ),
                level = Level.fromKey(o.getValue("lvl").str()),
                daysPerWeek = o.integer("days"),
                split = when (splitKey) {
                    "auto" -> Split.AUTO
                    "full" -> Split.FULL_BODY
                    "ul" -> Split.UPPER_LOWER
                    "ppl" -> Split.PUSH_PULL_LEGS
                    "bro" -> Split.BODY_PART
                    else -> Split.CUSTOM
                },
                customDays = if (splitKey == "custom2") custom2 else emptyList(),
                excluded = mapper.excludedIds(o.getValue("concern").str()),
            )
            val ctx = o.toString().take(160)
            assertEquals(ctx, o.getValue("seq").strings(), planner.weekSequence(settings))

            val week = planner.buildWeek(settings)
            assertEquals(ctx, o.getValue("days_").strings(), week.map { encode(it, settings) })
            assertEquals(
                ctx,
                o.getValue("alts").strings(),
                week.map { d -> d.items.joinToString(";") { planner.swapAlternatives(it, d, settings).joinToString(",") } },
            )

            val swaps = o["swaps"]?.jsonObject ?: continue
            settings = settings.copy(swaps = swaps.mapValues { it.value.str() })
            assertEquals(ctx, o.getValue("swapped").strings(), planner.buildWeek(settings).map { encode(it, settings) })
        }
    }

    @Test
    fun nextSessionMatchesPrototype() {
        val percent = Regex("\\(\\+(-?\\d+)%\\)")
        for (c in golden.getValue("progress").jsonArray) {
            val o = c.jsonObject
            val e = lib.exercise(o.getValue("id").str())
            val p = o.getValue("plan").jsonObject
            val planned = PlannedExercise(e.id, p.integer("sets"), p.integer("target"), p.num("w"))
            val reps = o.getValue("reps").jsonArray.map { it.jsonPrimitive.int }
            val sl = o.getValue("sl").jsonArray.map { it.jsonObject }
            val sets = reps.mapIndexed { i, r -> LoggedSet(r, sl[i].num("w"), sl[i].integer("target")) }
            val result = Progression.nextSession(e, Equipment(Bench.ADJUSTABLE, o.getValue("owned").kgs()), planned, sets)

            val next = o.getValue("next").jsonObject
            val ctx = o.toString()
            assertEquals(ctx, ExerciseState(next.num("w"), next.integer("target"), next.integer("sets")), result.next)
            val m = o.getValue("m").str()
            when {
                m.contains("short of the target") -> assertTrue(ctx, result is NextSession.Repeat)
                m.startsWith("Volume target met") -> assertTrue(ctx, result is NextSession.NoOption)
                else -> {
                    assertTrue(ctx, result is NextSession.Progressed)
                    val pct = percent.find(m)!!.groupValues[1].toInt()
                    assertEquals(ctx, pct, (result as NextSession.Progressed).increasePercent)
                }
            }
        }
    }

    @Test
    fun liveAdjustMatchesPrototype() {
        for (c in golden.getValue("adjust").jsonArray) {
            val o = c.jsonObject
            val e = lib.exercise(o.getValue("id").str())
            val cp = o.getValue("cp").jsonObject
            val pv = o.getValue("pv").jsonObject
            val set = SetRecord(
                reps = pv.integer("reps"),
                weightKg = pv["weight_kg"].takeUnless { it is JsonNull }?.jsonPrimitive?.double,
                effort = Effort.entries.first { it.key == pv.getValue("effort").str() },
                flags = if (pv.getValue("flags").jsonArray.isEmpty()) emptySet() else setOf(SetFlag.PAIN),
            )
            val result = Progression.adjustLive(
                e, Equipment(Bench.ADJUSTABLE, o.getValue("owned").kgs()), LiveTarget(cp.num("w"), cp.integer("target")), set,
            )
            val next = o.getValue("next").jsonObject
            val ctx = o.toString()
            assertEquals(ctx, LiveTarget(next.num("w"), next.integer("target")), result.next)
            val m = o.getValue("m").str()
            val expected = when {
                m.isEmpty() -> LiveAdjustment.Pain::class
                m.startsWith("Short of") -> LiveAdjustment.ShortOfTarget::class
                m.startsWith("Next set stays") -> LiveAdjustment.NotEasy::class
                m.startsWith("Easy, so the next set goes up") -> LiveAdjustment.AddRep::class
                m.startsWith("Easy at the top") -> LiveAdjustment.MoveUp::class
                else -> LiveAdjustment.NothingHeavier::class
            }
            assertEquals(ctx, expected, result::class)
        }
    }

    @Test
    fun setParserMatchesPrototype() {
        for (c in golden.getValue("parse").jsonArray) {
            val o = c.jsonObject
            val r = o.getValue("r").jsonObject
            val got = KeywordSetParser.parse(o.getValue("t").str())
            val ctx = o.toString()
            assertEquals(ctx, r["reps"].takeUnless { it is JsonNull }?.jsonPrimitive?.int, got.reps)
            assertEquals(ctx, r["weight_kg"].takeUnless { it is JsonNull }?.jsonPrimitive?.double, got.weightKg)
            assertEquals(ctx, r.getValue("effort").str(), got.effort.key)
            assertEquals(ctx, r.getValue("flags").strings().toSet(), got.flags.map { it.key }.toSet())
            assertEquals(ctx, r.getValue("comment").str(), got.comment)
        }
    }

    @Test
    fun concernMapperMatchesPrototype() {
        val mapper = KeywordConcernMapper(lib)
        for (c in golden.getValue("derive").jsonArray) {
            val o = c.jsonObject
            assertEquals(o.toString(), o.getValue("ids").strings().toSet(), mapper.excludedIds(o.getValue("t").str()))
        }
    }

    @Test
    fun levelFromTextMatchesPrototype() {
        for (c in golden.getValue("level").jsonArray) {
            val o = c.jsonObject
            assertEquals(o.toString(), o.getValue("lvl").str(), LevelFromText.parse(o.getValue("t").str()).key)
        }
    }
}
