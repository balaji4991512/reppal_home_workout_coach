package com.homeworkoutcoach.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A drawn figure that shows how an exercise is done: a few body parts at angles, moved between keyframes. Angles are
 * degrees, counterclockwise from pointing right, so up is 90 and down is -90. The figure faces right in a side view.
 * Units are roughly centimetres; the floor is at y = 0.
 */
enum class View {
    /** From the side: most moves. */
    SIDE,

    /** From the front: side-to-side moves such as a lateral raise. */
    FRONT,
}

/**
 * An arm or a leg: the angles of its upper and lower part. [reach] is how much of its length shows: 1 when it is side on,
 * less when it points at the viewer (an arm raised out to the side, seen from the side).
 */
data class Limb(val upper: Float, val lower: Float, val reach: Float = 1f)

/** Which point of the body is held in place, and where. */
enum class Joint { HIP, SHOULDER, HEAD, NEAR_HAND, FAR_HAND, NEAR_KNEE, FAR_KNEE, NEAR_ANKLE, FAR_ANKLE }

data class Anchor(val joint: Joint, val x: Float, val y: Float)

/**
 * One position. [torso] runs from hip to shoulder, [head] from shoulder to head. Near limbs are the ones nearer the
 * viewer (drawn on top); in a front view they are on the right. [foot] is the angle of both feet, or null for square to
 * the shin. The whole figure is moved so [anchor] lands where it says.
 */
data class Pose(
    val torso: Float,
    val armNear: Limb,
    val legNear: Limb,
    val anchor: Anchor,
    val armFar: Limb = armNear,
    val legFar: Limb = legNear,
    val head: Float = torso,
    val foot: Float? = null,
    /** How much of the torso shows (less when it points at the viewer, in a front view). */
    val torsoReach: Float = 1f,
)

/** Something drawn with the figure. */
sealed interface Prop {
    /** A bench or step pad, as a thick line; its legs reach the floor. */
    data class Pad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Prop
}

/** What is held: a dumbbell in the near hand, in both, or one held at the chest with both hands. */
enum class Grip { NONE, NEAR, BOTH, GOBLET }

/**
 * How an exercise moves: keyframes played in a loop (first to last and back to the first). [secondsPerRep] is one loop.
 */
data class Motion(
    val view: View,
    val frames: List<Pose>,
    val grip: Grip = Grip.NONE,
    val props: List<Prop> = emptyList(),
    val secondsPerRep: Float = 3.2f,
) {
    init {
        require(frames.size >= 2) { "a motion needs at least two keyframes" }
    }

    /**
     * The pose at [t] (0 to 1, looping): it moves from each keyframe to the next with an ease in and out, and holds a
     * moment at each.
     */
    fun poseAt(t: Float): Pose {
        val n = frames.size
        val x = ((t % 1f) + 1f) % 1f * n
        val i = x.toInt().coerceIn(0, n - 1)
        val f = x - i
        val hold = 0.18f
        val p = ((f - hold) / (1f - hold)).coerceIn(0f, 1f)
        return Figure.lerp(frames[i], frames[(i + 1) % n], p * p * (3 - 2 * p))
    }
}

/** A point. */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
}

/** What each drawn piece is, so the screen can colour it. */
sealed interface Part {
    data object Far : Part
    data object Near : Part
    data object Torso : Part
    data object Head : Part
    data object Prop : Part
    data object Dumbbell : Part
    data class Worked(val muscle: Muscle) : Part
}

sealed interface Shape {
    val part: Part

    data class Line(val a: Pt, val b: Pt, val width: Float, override val part: Part) : Shape
    data class Dot(val c: Pt, val r: Float, override val part: Part) : Shape
}

/** The body's measurements, joint positions and the shapes to draw. */
object Figure {
    const val TORSO = 50f
    const val NECK = 7f
    const val HEAD_R = 10f
    const val UPPER_ARM = 29f
    const val FOREARM = 29f
    const val THIGH = 43f
    const val SHIN = 42f
    const val FOOT = 13f
    const val SHOULDER_HALF = 17f
    const val HIP_HALF = 10f

    /** Ankle height when the foot is flat on the floor. */
    const val ANKLE_Y = 5f

    /** The box every motion is drawn in: x from -115 to 115, y from -8 to 206. */
    const val MIN_X = -115f
    const val MAX_X = 115f
    const val MIN_Y = -8f
    const val MAX_Y = 206f

    private fun dir(deg: Float): Pt = (deg * PI / 180).let { Pt(cos(it).toFloat(), sin(it).toFloat()) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Between two angles the short way round, so 162 to -88 turns through 180, not through 37. */
    private fun turn(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return a + d * t
    }

    private fun lerp(a: Limb, b: Limb, t: Float) = Limb(turn(a.upper, b.upper, t), turn(a.lower, b.lower, t), lerp(a.reach, b.reach, t))

    /** In between two poses; an anchor on another joint is blended by where it puts the whole figure. */
    fun lerp(a: Pose, b: Pose, t: Float): Pose {
        val angles = Pose(
            torso = turn(a.torso, b.torso, t),
            armNear = lerp(a.armNear, b.armNear, t),
            legNear = lerp(a.legNear, b.legNear, t),
            anchor = a.anchor,
            armFar = lerp(a.armFar, b.armFar, t),
            legFar = lerp(a.legFar, b.legFar, t),
            head = turn(a.head, b.head, t),
            foot = if (a.foot == null && b.foot == null) null else turn(a.foot ?: footOf(a), b.foot ?: footOf(b), t),
            torsoReach = lerp(a.torsoReach, b.torsoReach, t),
        )
        if (a.anchor.joint == b.anchor.joint) {
            return angles.copy(anchor = Anchor(a.anchor.joint, lerp(a.anchor.x, b.anchor.x, t), lerp(a.anchor.y, b.anchor.y, t)))
        }
        // different joints held: blend where each puts the hip
        val ha = joints(a, View.SIDE).getValue(Joint.HIP)
        val hb = joints(b, View.SIDE).getValue(Joint.HIP)
        return angles.copy(anchor = Anchor(Joint.HIP, lerp(ha.x, hb.x, t), lerp(ha.y, hb.y, t)))
    }

    private fun footOf(p: Pose) = p.legNear.lower + 90f

    /** Where the points of the body are, the figure moved so its anchor lands in place. */
    fun joints(p: Pose, view: View): Map<Joint, Pt> = points(p, view).let { it.joints }

    /** Every point of a pose, before drawing. */
    class Points(
        val joints: Map<Joint, Pt>,
        val shoulderNear: Pt,
        val shoulderFar: Pt,
        val hipNear: Pt,
        val hipFar: Pt,
        val neck: Pt,
        val headCenter: Pt,
        val elbowNear: Pt,
        val elbowFar: Pt,
        val toeNear: Pt,
        val toeFar: Pt,
        val hip: Pt,
        val shoulder: Pt,
    )

    fun points(p: Pose, view: View): Points {
        val hip0 = Pt(0f, 0f)
        val up = dir(p.torso)
        val shoulder0 = hip0 + up * (TORSO * p.torsoReach)
        // in a front view the shoulders and hips are side by side; near is on the right of the torso line
        val side = Pt(up.y, -up.x)
        val sw = if (view == View.FRONT) SHOULDER_HALF else 0f
        val hw = if (view == View.FRONT) HIP_HALF else 0f
        val sN0 = shoulder0 + side * sw
        val sF0 = shoulder0 - side * sw
        val hN0 = hip0 + side * hw
        val hF0 = hip0 - side * hw
        val neck0 = shoulder0 + dir(p.head) * NECK
        val head0 = shoulder0 + dir(p.head) * (NECK + HEAD_R)
        val eN0 = sN0 + dir(p.armNear.upper) * (UPPER_ARM * p.armNear.reach)
        val handN0 = eN0 + dir(p.armNear.lower) * (FOREARM * p.armNear.reach)
        val eF0 = sF0 + dir(p.armFar.upper) * (UPPER_ARM * p.armFar.reach)
        val handF0 = eF0 + dir(p.armFar.lower) * (FOREARM * p.armFar.reach)
        val kN0 = hN0 + dir(p.legNear.upper) * (THIGH * p.legNear.reach)
        val aN0 = kN0 + dir(p.legNear.lower) * (SHIN * p.legNear.reach)
        val kF0 = hF0 + dir(p.legFar.upper) * (THIGH * p.legFar.reach)
        val aF0 = kF0 + dir(p.legFar.lower) * (SHIN * p.legFar.reach)
        val footAngleN = p.foot ?: (p.legNear.lower + 90f)
        val footAngleF = p.foot ?: (p.legFar.lower + 90f)
        val footLen = if (view == View.FRONT) FOOT * 0.45f else FOOT
        val tN0 = aN0 + dir(footAngleN) * footLen
        val tF0 = aF0 + dir(footAngleF) * footLen
        val raw = mapOf(
            Joint.HIP to hip0, Joint.SHOULDER to shoulder0, Joint.HEAD to head0,
            Joint.NEAR_HAND to handN0, Joint.FAR_HAND to handF0,
            Joint.NEAR_KNEE to kN0, Joint.FAR_KNEE to kF0,
            Joint.NEAR_ANKLE to aN0, Joint.FAR_ANKLE to aF0,
        )
        val shift = Pt(p.anchor.x, p.anchor.y) - raw.getValue(p.anchor.joint)
        return Points(
            joints = raw.mapValues { it.value + shift },
            shoulderNear = sN0 + shift, shoulderFar = sF0 + shift, hipNear = hN0 + shift, hipFar = hF0 + shift,
            neck = neck0 + shift, headCenter = head0 + shift, elbowNear = eN0 + shift, elbowFar = eF0 + shift,
            toeNear = tN0 + shift, toeFar = tF0 + shift, hip = hip0 + shift, shoulder = shoulder0 + shift,
        )
    }

    /**
     * The shapes for one pose, back to front: far limbs, torso and head, near limbs, then the worked muscles of [worked]
     * on top. Props and dumbbells come from [motion].
     */
    fun shapes(motion: Motion, pose: Pose, worked: Set<Muscle>): List<Shape> {
        val view = motion.view
        val pt = points(pose, view)
        val j = pt.joints
        val out = mutableListOf<Shape>()

        for (prop in motion.props) when (prop) {
            is Prop.Pad -> {
                val a = Pt(prop.x1, prop.y1)
                val b = Pt(prop.x2, prop.y2)
                out += Shape.Line(a, b, 7f, Part.Prop)
                // legs from the low end(s) to the floor
                val low = listOf(a, b).filter { it.y > 1f }
                for (l in low) out += Shape.Line(Pt(l.x + (if (l == a) 6f else -6f), l.y - 3f), Pt(l.x + (if (l == a) 6f else -6f), 0f), 3.5f, Part.Prop)
            }
        }

        val frontView = view == View.FRONT
        val none = Pt(0f, 0f)
        fun band(a: Pt, b: Pt, from: Float, to: Float, offset: Pt, width: Float, m: Muscle) {
            if (m !in worked) return
            out += Shape.Line(a + (b - a) * from + offset, a + (b - a) * to + offset, width, Part.Worked(m))
        }
        // the front of an arm or leg: to the left of its direction (the biceps of a hanging arm face forward)
        fun front(a: Pt, b: Pt): Pt = norm(b - a).let { Pt(-it.y, it.x) }
        fun arm(shoulder: Pt, elbow: Pt, hand: Pt, part: Part) {
            out += Shape.Line(shoulder, elbow, 10f, part)
            out += Shape.Line(elbow, hand, 8f, part)
            val f = if (frontView) none else front(shoulder, elbow)
            band(shoulder, elbow, 0.25f, 0.85f, f * 2.2f, if (frontView) 7f else 5.5f, Muscle.BICEPS)
            band(shoulder, elbow, 0.25f, 0.85f, f * -2.2f, if (frontView) 7f else 5.5f, Muscle.TRICEPS)
            band(shoulder, elbow, -0.04f, 0.22f, f * 1.5f, 10f, Muscle.SHOULDERS)
            band(shoulder, elbow, -0.04f, 0.2f, f * -2.5f, 8f, Muscle.REAR_DELTS)
        }
        fun leg(hip: Pt, knee: Pt, ankle: Pt, toe: Pt, part: Part) {
            out += Shape.Line(hip, knee, 15f, part)
            out += Shape.Line(knee, ankle, 11f, part)
            out += Shape.Line(ankle, toe, 7f, part)
            val f = if (frontView) none else front(hip, knee)
            band(hip, knee, 0.15f, 0.85f, f * 3.5f, if (frontView) 11f else 8f, Muscle.QUADS)
            band(hip, knee, 0.2f, 0.85f, f * -3.5f, if (frontView) 11f else 8f, Muscle.HAMS)
            if (frontView) band(hip, knee, -0.02f, 0.15f, none, 13f, Muscle.GLUTES)
        }

        // far side first; seen from the front both sides are in plain view
        val farPart = if (frontView) Part.Near else Part.Far
        arm(pt.shoulderFar, pt.elbowFar, j.getValue(Joint.FAR_HAND), farPart)
        leg(pt.hipFar, j.getValue(Joint.FAR_KNEE), j.getValue(Joint.FAR_ANKLE), pt.toeFar, farPart)
        if (motion.grip == Grip.BOTH) out += Shape.Dot(j.getValue(Joint.FAR_HAND), 6.5f, Part.Dumbbell)

        // torso and head
        if (frontView) {
            // a rounded block from hips to shoulders, a little wider at the shoulders
            out += Shape.Line(pt.hip + (pt.shoulder - pt.hip) * 0.12f, pt.shoulder - (pt.shoulder - pt.hip) * 0.1f, 30f, Part.Torso)
            out += Shape.Line(pt.shoulderNear, pt.shoulderFar, 12f, Part.Torso)
        } else {
            out += Shape.Line(pt.hip, pt.shoulder, 22f, Part.Torso)
        }
        out += Shape.Line(pt.shoulder, pt.neck, 8f, Part.Torso)
        out += Shape.Dot(pt.headCenter, HEAD_R, Part.Head)
        // the chest side of the torso: to the right of hip-to-shoulder when standing facing right
        val up = norm(pt.shoulder - pt.hip)
        val chest = if (frontView) none else Pt(up.y, -up.x)
        band(pt.hip, pt.shoulder, 0.58f, 0.9f, chest * 5f, if (frontView) 20f else 10f, Muscle.CHEST)
        band(pt.hip, pt.shoulder, 0.12f, 0.5f, chest * 5f, if (frontView) 12f else 9f, Muscle.ABS)
        band(pt.hip, pt.shoulder, 0.35f, 0.85f, chest * -5f, if (frontView) 22f else 10f, Muscle.LATS)
        if (!frontView) band(pt.hip, pt.shoulder, -0.06f, 0.14f, chest * -6f, 13f, Muscle.GLUTES)

        // near side on top
        leg(pt.hipNear, j.getValue(Joint.NEAR_KNEE), j.getValue(Joint.NEAR_ANKLE), pt.toeNear, Part.Near)
        arm(pt.shoulderNear, pt.elbowNear, j.getValue(Joint.NEAR_HAND), Part.Near)

        // what is held, on top
        when (motion.grip) {
            Grip.NEAR, Grip.BOTH -> out += Shape.Dot(j.getValue(Joint.NEAR_HAND), 6.5f, Part.Dumbbell)
            Grip.GOBLET -> out += Shape.Dot((j.getValue(Joint.NEAR_HAND) + j.getValue(Joint.FAR_HAND)) * 0.5f, 8f, Part.Dumbbell)
            Grip.NONE -> Unit
        }
        return out
    }

    /** The area a motion covers over a whole loop (with line widths), so a screen can fit it; the floor is always in. */
    data class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    fun bounds(motion: Motion, worked: Set<Muscle> = emptySet()): Box {
        var minX = Float.MAX_VALUE
        var minY = -2f
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun add(p: Pt, pad: Float) {
            minX = minOf(minX, p.x - pad); maxX = maxOf(maxX, p.x + pad)
            minY = minOf(minY, p.y - pad); maxY = maxOf(maxY, p.y + pad)
        }
        for (i in 0 until 48) for (s in shapes(motion, motion.poseAt(i / 48f), worked)) when (s) {
            is Shape.Line -> { add(s.a, s.width / 2); add(s.b, s.width / 2) }
            is Shape.Dot -> add(s.c, s.r)
        }
        return Box(minX, minY, maxX, maxY)
    }

    private fun norm(p: Pt): Pt {
        val l = kotlin.math.sqrt(p.x * p.x + p.y * p.y)
        return if (l < 1e-4f) Pt(0f, 1f) else Pt(p.x / l, p.y / l)
    }
}
