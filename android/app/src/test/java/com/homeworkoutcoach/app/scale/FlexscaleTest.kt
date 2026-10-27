package com.homeworkoutcoach.app.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexscaleTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun load(name: String): List<ByteArray> =
        javaClass.classLoader!!.getResource(name)!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { hex(it.split(' ')[1]) }

    /** The scale's broadcasts while the Flexnest app weighed (83.50, then 84.05), in order. */
    private val capture by lazy { load("flexscale_capture.txt") }

    /** The scale's broadcasts while RepPal listened: phase byte 0x10 throughout, settled at 83.55. */
    private val reppalCapture by lazy { load("flexscale_capture_reppal.txt") }

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

        // Listening after that reading, during the body-fat part: the next weigh-in gives 84.05.
        val afterFirst = capture.indexOfFirst { Flexscale.parse(it)!!.phase != 0 }
        val second = WeighInTracker()
        capture.drop(afterFirst).forEach { Flexscale.parse(it)?.let(second::accept) }
        assertEquals(84.05, (second.state as WeighInState.Settled).weightKg, 0.0)
    }

    @Test
    fun theSessionRepPalMissedNowSettles() {
        // The first version ignored every packet here because the phase byte was 0x10.
        val t = WeighInTracker()
        reppalCapture.forEach { Flexscale.parse(it)?.let(t::accept) }
        assertEquals(WeighInState.Settled(83.55, "ED:67:39:DA:91:0B"), t.state)
    }

    @Test
    fun tapsAndObjectsAreNotWeighIns() {
        // Only the end of the RepPal capture: an object around 10 kg, then taps on the empty scale.
        val t = WeighInTracker()
        val afterSettle = reppalCapture.indexOfFirst { Flexscale.parse(it)!!.settled && Flexscale.parse(it)!!.weightKg > 80 }
        reppalCapture.drop(afterSettle + 1).forEach { Flexscale.parse(it)?.let(t::accept) }
        assertEquals(WeighInState.Waiting, t.state)
    }

    @Test
    fun aWakingScaleReShowingItsLastWeightIsNotSavedAgain() {
        // From the first capture: the scale wakes and shows 84.05 (measuring, then settled) with nothing moving.
        val t = WeighInTracker()
        t.accept(Flexscale.parse(hex("aabbed6739da910b9065e06affffff0200d5204ce2034614"))!!)
        t.accept(Flexscale.parse(hex("aabbed6739da910b9165e06affffff0300d5204ce2034114"))!!)
        assertEquals(WeighInState.Live(84.05), t.state)
    }

    @Test
    fun onlyTheRememberedScaleCounts() {
        val t = WeighInTracker(scaleId = "11:22:33:44:55:66")
        t.accept(Flexscale.parse(hex("aabbed6739da910b3665e06a3baa0202009e204ce203ac13"))!!)
        assertEquals(WeighInState.Waiting, t.state)
    }
}
