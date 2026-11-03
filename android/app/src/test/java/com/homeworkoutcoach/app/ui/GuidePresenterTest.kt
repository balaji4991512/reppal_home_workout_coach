package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.ui.guide.GuidePresenter
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePresenterTest {
    private val lib = Library.default
    private val presenter = GuidePresenter(lib)

    @Test
    fun benchPressShowsItsMusclesEquipmentAndSteps() {
        val g = presenter.present("bench")!!
        assertEquals("Dumbbell bench press", g.name)
        assertEquals(setOf(Muscle.CHEST), g.primary)
        assertEquals(setOf(Muscle.TRICEPS, Muscle.SHOULDERS), g.secondary)
        assertEquals(lib.muscleLabels.getValue(Muscle.CHEST), g.main)
        assertEquals("Dumbbells and a bench", g.needs)
        assertNotNull(g.motion)
        assertEquals(3, g.steps.size)
        assertTrue(g.tips.isNotEmpty())
        // the helpers read as one line: the first capitalised, the rest not
        val also = g.also!!
        assertTrue(also, also.first().isUpperCase() && also.contains(", ") && also.substringAfter(", ").first().isLowerCase())
    }

    @Test
    fun equipmentReadsPlainly() {
        assertEquals("Just your body", presenter.present("pushup")!!.needs)
        assertEquals("Dumbbells", presenter.present("curl")!!.needs)
        assertEquals("Dumbbells and an adjustable bench", presenter.present("incl")!!.needs)
        assertEquals("A bench", presenter.present("stepup")!!.needs)
    }

    @Test
    fun anExerciseWorkingOneMuscleHasNoAlsoLine() {
        assertNull(presenter.present("curl")!!.also)
    }

    @Test
    fun everyExerciseHasAGuideToShow() {
        for (id in lib.exercises.keys) {
            val g = presenter.present(id)!!
            assertNotNull(id, g.motion)
            assertTrue(id, g.steps.isNotEmpty() && g.tips.isNotEmpty())
        }
    }

    @Test
    fun anUnknownExerciseHasNoGuide() {
        assertNull(presenter.present("nope"))
    }
}
