package com.homeworkoutcoach.app.scale

/**
 * One broadcast from a Flexnest Flexscale (model FLX080). Worked out from a Bluetooth capture; the scale
 * advertises readings openly, no pairing. Layout of the manufacturer data (company id 0xFFFF, which Android strips):
 *
 *  0-1   AA BB          header
 *  2-7   scale MAC      identifies the scale
 *  8-11  clock          Unix seconds, little-endian (set by the Flexnest app)
 *  12-14 unknown        FF FF FF until the scale is set up by the app
 *  15    status         0x02 measuring; bit 0x01 or 0x20 set = reading settled
 *  16    phase          0x00 or 0x10 while weighing, 0x11 with a body-fat result; probably the impedance
 *                       measurement state (not decoded yet). Not used to judge a reading.
 *  17-18 weight         hundredths of a kg, little-endian
 *  19-21 unknown        constant 4C E2 03
 *  22-23 counter        little-endian, goes up with each broadcast
 */
data class ScaleReading(
    val scaleId: String,
    val weightKg: Double,
    val settled: Boolean,
    val status: Int,
    /** Impedance (body-fat) state, not decoded yet. */
    val phase: Int,
    val counter: Int,
)

object Flexscale {
    /** Bluetooth SIG id used by the scale's manufacturer data. */
    const val COMPANY_ID = 0xFFFF
    val HEADER = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
    private const val LENGTH = 24

    /** Parse manufacturer data (without the company id), or null if it isn't a Flexscale packet. */
    fun parse(data: ByteArray?): ScaleReading? {
        if (data == null || data.size < LENGTH) return null
        if (data[0] != HEADER[0] || data[1] != HEADER[1]) return null
        fun u8(i: Int) = data[i].toInt() and 0xFF
        fun u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        val status = u8(15)
        return ScaleReading(
            scaleId = (2..7).joinToString(":") { "%02X".format(u8(it)) },
            weightKg = u16(17) / 100.0,
            settled = status and 0x21 != 0,
            status = status,
            phase = u8(16),
            counter = u16(22),
        )
    }
}

/** What the weigh-in screen shows while listening. */
sealed interface WeighInState {
    /** Listening; nothing from a scale yet, or the scale is idle. */
    data object Waiting : WeighInState

    /** Someone is on the scale; the number is still moving. */
    data class Live(val weightKg: Double) : WeighInState

    /** The reading settled: this is the weight to save. */
    data class Settled(val weightKg: Double, val scaleId: String) : WeighInState
}

/**
 * Turns a stream of broadcasts into one weigh-in. A settled reading only counts once the live number has moved
 * through [MIN_MOVES] different values since the scale was last empty: stepping on always does that, while a
 * scale waking up and re-showing its last weight does not, so an old reading is never saved again.
 * [scaleId] limits it to the user's own scale once known.
 */
class WeighInTracker(private val scaleId: String? = null) {
    var state: WeighInState = WeighInState.Waiting
        private set
    private val liveValues = mutableSetOf<Double>()

    fun accept(r: ScaleReading): WeighInState {
        if (state is WeighInState.Settled) return state
        if (scaleId != null && r.scaleId != scaleId) return state
        if (r.weightKg < MIN_KG) {
            // Empty scale: the next weigh-in starts from scratch.
            liveValues.clear()
            state = WeighInState.Waiting
            return state
        }
        state = if (!r.settled) {
            liveValues += r.weightKg
            WeighInState.Live(r.weightKg)
        } else if (liveValues.size >= MIN_MOVES) {
            WeighInState.Settled(r.weightKg, r.scaleId)
        } else {
            state
        }
        return state
    }

    companion object {
        /** Below this the scale is empty, being touched, or holding an object rather than an adult. */
        const val MIN_KG = 20.0

        /** Different live values needed before a settled reading counts. */
        const val MIN_MOVES = 3
    }
}
