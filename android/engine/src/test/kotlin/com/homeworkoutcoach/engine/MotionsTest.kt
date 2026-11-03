package com.homeworkoutcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The drawn motions: one per exercise, inside their box, nothing through the floor. */
class MotionsTest {
    private val lib = Library.default
    private val moments = (0 until 40).map { it / 40f }

    @Test
    fun everyExerciseHasAMotion() {
        for (id in lib.exercises.keys) assertNotNull(id, Motions.of(id))
        assertEquals(lib.exercises.keys, Motions.all.keys)
    }

    @Test
    fun everyFigureStaysInsideItsBox() {
        for ((id, m) in Motions.all) for (t in moments) {
            val e = lib.exercise(id)
            for (s in Figure.shapes(m, m.poseAt(t), (e.primary + e.secondary).toSet())) {
                val pts = when (s) {
                    is Shape.Line -> listOf(s.a, s.b)
                    is Shape.Dot -> listOf(s.c)
                }
                for (p in pts) assertTrue("$id at $t: $p", p.x in Figure.MIN_X..Figure.MAX_X && p.y in Figure.MIN_Y..Figure.MAX_Y)
            }
        }
    }

    @Test
    fun nothingGoesThroughTheFloor() {
        for ((id, m) in Motions.all) for (t in moments) {
            val p = Figure.points(m.poseAt(t), m.view)
            val low = (p.joints.values + listOf(p.toeNear, p.toeFar, p.headCenter, p.hip, p.shoulder)).minOf { it.y }
            assertTrue("$id at $t: lowest point $low", low > -3f)
        }
    }

    @Test
    fun theWorkedMusclesAreDrawn() {
        for ((id, m) in Motions.all) {
            val e = lib.exercise(id)
            val drawn = moments.flatMap { t -> Figure.shapes(m, m.poseAt(t), e.primary.toSet()) }.mapNotNull { (it.part as? Part.Worked)?.muscle }.toSet()
            assertEquals(id, e.primary.toSet(), drawn)
        }
    }

    @Test
    fun aLoopEndsWhereItStarts() {
        for ((id, m) in Motions.all) {
            val a = Figure.joints(m.poseAt(0f), m.view)
            val b = Figure.joints(m.poseAt(0.9999f), m.view)
            for (j in Joint.entries) {
                val d = a.getValue(j) - b.getValue(j)
                assertTrue("$id $j", kotlin.math.abs(d.x) < 1f && kotlin.math.abs(d.y) < 1f)
            }
        }
    }

    @Test
    fun drawSheetsWhenAsked() {
        val dir = System.getenv("MOTION_SHEETS") ?: return
        MotionSheets.draw(File(dir))
    }
}

/** The written how-to for each exercise. */
class GuidesTest {
    private val lib = Library.default

    @Test
    fun everyExerciseHasStepsAndTips() {
        assertEquals(lib.exercises.keys, Guides.ids)
        for (id in lib.exercises.keys) {
            val g = Guides.of(id)!!
            assertTrue("$id steps", g.steps.size in 3..5)
            assertTrue("$id tips", g.tips.size in 1..3)
            for (line in g.steps + g.tips) {
                assertTrue("$id: $line", line.length in 10..140)
                assertTrue("$id: $line", line.trim() == line && line.endsWith("."))
            }
        }
    }
}
