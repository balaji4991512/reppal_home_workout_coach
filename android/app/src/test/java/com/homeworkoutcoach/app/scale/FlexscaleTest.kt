package com.homeworkoutcoach.app.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexscaleTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** The scale's broadcasts from a real capture, in order. */
    private val capture: List<ByteArray> by lazy {
        javaClass.classLoader!!.getResource("flexscale_capture.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { hex(it.split(' ')[1]) }
    }

    @Test
    fun parsesWeightStatusAndScale() {
        val measuring = Flexscale.parse(hex("aabbed6739da910b3665e06a3baa0202009e204ce203ac13"))!!
        assertEquals("ED:67:39:DA:91:0B", measuring.scaleId)
        assertEquals(83.50, measuring.weightKg, 0.0)
        assertFalse(measuring.settled)
        assertEquals(0x13ac, measuring.counter)

        val settled = Flexscale.parse(hex("aabbed6739da910b3665e06a3baa0223009e204ce203b013"))!!
        assertTrue(settled.settled)
        assertEquals(0, settled.phase)

        val after = Flexscale.parse(hex("aabbed6739da910b6865e06affffff02109e204ce2030514"))!!
        assertEquals(0x10, after.phase)

        assertNull(Flexscale.parse(hex("0102ed6739da910b3665e06a3baa0223009e204ce203b013")))
        assertNull(Flexscale.parse(hex("aabbed6739da")))
        assertNull(Flexscale.parse(null))
    }

    @Test
    fun replayingTheCaptureGivesTheAppsReadings() {
        // Listening from the start: the first weigh-in settles at 83.50, as the Flexnest app showed.
        val first = WeighInTracker()
        capture.forEach { Flexscale.parse(it)?.let(first::accept) }
        assertEquals(WeighInState.Settled(83.50, "ED:67:39:DA:91:0B"), first.state)

        // Listening after that reading (while the scale still repeats it): the next weigh-in gives 84.05.
        val afterFirst = capture.indexOfFirst { Flexscale.parse(it)!!.phase != 0 }
        val second = WeighInTracker()
        capture.drop(afterFirst).forEach { Flexscale.parse(it)?.let(second::accept) }
        assertEquals(84.05, (second.state as WeighInState.Settled).weightKg, 0.0)
    }

    @Test
    fun aReadingAlreadyOnTheScaleIsNotSavedAgain() {
        val t = WeighInTracker()
        // the scale is still showing a settled 83.50 from before listening started
        t.accept(Flexscale.parse(hex("aabbed6739da910b3665e06a3baa0223009e204ce203b013"))!!)
        assertEquals(WeighInState.Waiting, t.state)
        // and repeats it afterwards with a non-zero phase
        t.accept(Flexscale.parse(hex("aabbed6739da910b6865e06affffff02109e204ce2030514"))!!)
        assertEquals(WeighInState.Waiting, t.state)
    }

    @Test
    fun onlyTheRememberedScaleCounts() {
        val t = WeighInTracker(scaleId = "11:22:33:44:55:66")
        t.accept(Flexscale.parse(hex("aabbed6739da910b3665e06a3baa0202009e204ce203ac13"))!!)
        assertEquals(WeighInState.Waiting, t.state)
    }
}
