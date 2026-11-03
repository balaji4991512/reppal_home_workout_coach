package com.homeworkoutcoach.engine

/**
 * How each exercise moves, for the drawn figure (see [Figure]). Built from a few families: lying presses and flies,
 * push-ups, rows, squats and lunges, hinges, bridges, curls and raises, and floor core work. Positions are hand-tuned so
 * feet stay on the floor and the figure stays in its box (checked in tests).
 */
object Motions {
    private fun l(upper: Number, lower: Number, reach: Number = 1) = Limb(upper.toFloat(), lower.toFloat(), reach.toFloat())
    private fun at(joint: Joint, x: Number, y: Number) = Anchor(joint, x.toFloat(), y.toFloat())
    private fun pose(
        torso: Number,
        arm: Limb,
        leg: Limb,
        anchor: Anchor,
        armFar: Limb = arm,
        legFar: Limb = leg,
        head: Number = torso,
        foot: Number? = null,
    ) = Pose(torso.toFloat(), arm, leg, anchor, armFar, legFar, head.toFloat(), foot?.toFloat())

    private fun side(vararg frames: Pose, grip: Grip = Grip.NONE, props: List<Prop> = emptyList(), seconds: Number = 3.2) =
        Motion(View.SIDE, frames.toList(), grip, props, seconds.toFloat())

    private const val FOOT_Y = Figure.ANKLE_Y

    // ---- Solvers: bend a limb to reach a point, tilt a plank until the toes touch ----

    private fun angleOf(p: Pt) = Math.toDegrees(kotlin.math.atan2(p.y.toDouble(), p.x.toDouble())).toFloat()
    private fun dir(deg: Float) = Math.toRadians(deg.toDouble()).let { Pt(kotlin.math.cos(it).toFloat(), kotlin.math.sin(it).toFloat()) }

    /**
     * A two-part limb from [root] reaching [target], bending to the left of the line between them ([bend] 1) or to the
     * right (-1). Too far away, it reaches straight toward the target.
     */
    private fun reachTo(root: Pt, target: Pt, a: Float, b: Float, bend: Int): Limb {
        val v = target - root
        val dist = kotlin.math.sqrt(v.x * v.x + v.y * v.y).coerceIn(kotlin.math.abs(a - b) + 0.01f, a + b - 0.01f)
        val base = angleOf(v)
        val off = Math.toDegrees(kotlin.math.acos(((a * a + dist * dist - b * b) / (2 * a * dist)).toDouble().coerceIn(-1.0, 1.0))).toFloat()
        val upper = base + bend * off
        val elbow = root + dir(upper) * a
        return Limb(upper, angleOf(target - elbow))
    }

    /** The pose with its legs bent to put the ankles at [near] and [far] (knees up or forward). */
    private fun legsTo(p: Pose, near: Pt, far: Pt = near): Pose {
        val pts = Figure.points(p, View.SIDE)
        return p.copy(
            legNear = reachTo(pts.hipNear, near, Figure.THIGH, Figure.SHIN, 1),
            legFar = reachTo(pts.hipFar, far, Figure.THIGH, Figure.SHIN, 1),
        )
    }

    /** The pose with both arms reaching [target] (a dumbbell held on the hips), elbows bent down. */
    private fun armsTo(p: Pose, target: (Figure.Points) -> Pt): Pose {
        val pts = Figure.points(p, View.SIDE)
        val arm = reachTo(pts.shoulder, target(pts), Figure.UPPER_ARM, Figure.FOREARM, -1)
        return p.copy(armNear = arm, armFar = arm)
    }

    // ---- Shared pieces ----

    private val down = l(-90, -90)
    private val straight = l(-90, -90)
    private val standing = at(Joint.NEAR_ANKLE, -4, FOOT_Y)

    /** A flat bench, seat at 45 cm. */
    private val flatBench = listOf(Prop.Pad(-80f, 45f, 40f, 45f))

    /** An incline bench: a seat, and a back pad rising toward the head at about [deg] degrees. */
    private fun inclineBench(deg: Double): List<Prop> {
        val r = Math.toRadians(deg)
        return listOf(Prop.Pad(0f, 45f, 38f, 45f), Prop.Pad(0f, 45f, (-70 * Math.cos(r)).toFloat(), (45 + 70 * Math.sin(r)).toFloat()))
    }

    /** Lying face up on a flat bench, head to the left, feet flat on the floor. */
    private fun onBench(arm: Limb, armFar: Limb = arm) =
        pose(180, arm, l(-12, -90), at(Joint.HIP, 18, 56), armFar = armFar, foot = 0)

    /** Sitting back on an incline bench at [deg], feet on the floor. */
    private fun onIncline(deg: Int, arm: Limb) =
        pose(180 - deg, arm, l(-8, -90), at(Joint.HIP, 8, 57), foot = 0)

    /** Lying face up on the floor, head to the left. */
    private fun onFloor(arm: Limb, leg: Limb, legFar: Limb = leg, armFar: Limb = arm, torso: Number = 180, head: Number = torso) =
        pose(torso, arm, leg, at(Joint.SHOULDER, -45, 11), armFar = armFar, legFar = legFar, head = head, foot = 0)

    /**
     * A plank on the hands, head to the right, body in a straight line tilted just enough for the toes to touch the
     * floor. [legNear] or [legFar] replaces a straight leg (a knee drive).
     */
    private fun plank(arm: Limb, handX: Number = 45, handY: Number = 3, legNear: Limb? = null, legFar: Limb? = null): Pose {
        fun tilted(b: Float) = l(180 + b, 180 + b).let { pose(b, arm, it, at(Joint.NEAR_HAND, handX, handY), legFar = it, foot = -80) }
        fun toe(b: Float) = Figure.points(tilted(b), View.SIDE).toeNear.y
        var lo = -10f
        var hi = 70f
        repeat(40) {
            val mid = (lo + hi) / 2
            if (toe(mid) > 1.5f) lo = mid else hi = mid
        }
        val body = tilted((lo + hi) / 2)
        return body.copy(legNear = legNear ?: body.legNear, legFar = legFar ?: body.legFar)
    }

    /** On hands and knees, head to the right. */
    private fun allFours(arm: Limb = down, armFar: Limb = down, leg: Limb = l(-90, 180), legFar: Limb = leg) =
        pose(10, arm, leg, at(Joint.HIP, -12, 52), armFar = armFar, legFar = legFar, foot = 180)

    /** Lying face down, head to the right. */
    private fun prone(arm: Limb, armFar: Limb = arm, head: Number = 8) =
        pose(0, arm, l(180, 180), at(Joint.HIP, -20, 11), armFar = armFar, head = head, foot = -90)

    // ---- Families ----

    private fun benchPress() = side(onBench(l(-72, 95)), onBench(l(90, 90)), grip = Grip.BOTH, props = flatBench)

    private fun inclinePress() = side(onIncline(35, l(-60, 100)), onIncline(35, l(105, 105)), grip = Grip.BOTH, props = inclineBench(35.0))

    private fun floorPress() = side(
        onFloor(l(-12, 92), l(55, -75)),
        onFloor(l(90, 90), l(55, -75)),
        grip = Grip.BOTH,
    )

    private fun benchFly() = side(onBench(l(-80, -60, 0.45)), onBench(l(88, 92)), grip = Grip.BOTH, props = flatBench)

    private fun inclineFly() = side(onIncline(35, l(-70, -55, 0.45)), onIncline(35, l(105, 108)), grip = Grip.BOTH, props = inclineBench(35.0))

    private fun floorFly() = side(onFloor(l(-15, -5, 0.45), l(55, -75)), onFloor(l(88, 92), l(55, -75)), grip = Grip.BOTH)

    private fun pullover() = side(onBench(l(88, 92)), onBench(l(165, 170)), grip = Grip.GOBLET, props = flatBench)

    private fun lyingTriceps() = side(onBench(l(100, 100)), onBench(l(100, 168)), grip = Grip.BOTH, props = flatBench)

    private fun pushUp(reach: Number = 1) = side(
        plank(l(162, -95, reach)),
        plank(l(-88, -90, reach)),
    )

    private fun inclinePushUp() = side(
        plank(l(158, -100), handX = 52, handY = 48),
        plank(l(-86, -90), handX = 52, handY = 48),
        props = listOf(Prop.Pad(40f, 45f, 105f, 45f)),
    )

    private fun pikePushUp() = side(
        pose(-62, l(-105, -60), l(-115, -115), at(Joint.NEAR_HAND, 30, 3), head = -70, foot = 0),
        pose(-48, l(-58, -58), l(-118, -118), at(Joint.NEAR_HAND, 30, 3), head = -48, foot = 0),
    )

    private fun benchDip() = side(
        legsTo(pose(88, l(-145, -40), down, at(Joint.NEAR_HAND, -12, 49), foot = 0), Pt(62f, FOOT_Y)),
        legsTo(pose(90, l(-100, -95), down, at(Joint.NEAR_HAND, -12, 49), foot = 0), Pt(62f, FOOT_Y)),
        props = listOf(Prop.Pad(-80f, 45f, -6f, 45f)),
    )

    private fun overheadPress(arnold: Boolean) = side(
        pose(90, if (arnold) l(-75, 92) else l(-85, 95, 0.55), straight, standing),
        pose(90, l(92, 92), straight, standing),
        grip = Grip.BOTH,
    )

    private fun frontRaise() = side(pose(90, l(-85, -82), straight, standing), pose(90, l(0, 2), straight, standing), grip = Grip.BOTH)

    private fun lateralRaise() = Motion(
        View.FRONT,
        listOf(
            pose(90, l(-80, -82), l(-92, -90), standing, armFar = l(-100, -98), legFar = l(-88, -90)),
            pose(90, l(2, 0), l(-92, -90), standing, armFar = l(178, 180), legFar = l(-88, -90)),
        ),
        Grip.BOTH,
    )

    private fun sideBend() = Motion(
        View.FRONT,
        listOf(
            pose(74, l(-96, -94), l(-92, -90), standing, armFar = l(-80, -45), legFar = l(-88, -90)),
            pose(102, l(-82, -84), l(-92, -90), standing, armFar = l(-70, -30), legFar = l(-88, -90)),
        ),
        Grip.NEAR,
    )

    private fun curl(incline: Boolean = false): Motion = if (incline) {
        side(onIncline(45, l(-92, -90)), onIncline(45, l(-92, 75)), grip = Grip.BOTH, props = inclineBench(45.0))
    } else {
        side(pose(90, l(-90, -88), straight, standing), pose(90, l(-88, 72), straight, standing), grip = Grip.BOTH)
    }

    private fun overheadTriceps() = side(
        pose(90, l(102, -108), straight, standing),
        pose(90, l(98, 96), straight, standing),
        grip = Grip.GOBLET,
    )

    /** Standing hinged forward at [torso] degrees, knees soft. */
    private fun hinge(torso: Number, arm: Limb, armFar: Limb = arm) =
        pose(torso, arm, l(-74, -96), standing, armFar = armFar, head = torso.toFloat() + 10, foot = 0)

    private fun bentRow() = side(hinge(28, l(-90, -90)), hinge(28, l(-150, -88)), grip = Grip.BOTH)

    private fun kickback() = side(hinge(22, l(-170, -92)), hinge(22, l(-170, -168)), grip = Grip.BOTH)

    private fun bentReverseFly() = side(hinge(28, l(-90, -88)), hinge(28, l(-35, -30, 0.45)), grip = Grip.BOTH)

    private fun singleArmRow(): Motion {
        fun p(arm: Limb) = pose(
            8, arm, l(-96, -88), at(Joint.HIP, -28, 93),
            armFar = l(-90, -90), legFar = l(-90, 180), head = 15, foot = 0,
        )
        return side(p(l(-90, -90)), p(l(-165, -85)), grip = Grip.NEAR, props = listOf(Prop.Pad(-55f, 45f, 30f, 45f)))
    }

    /** Lying chest down on an incline bench, feet on the floor behind. */
    private fun chestSupported(arm: Limb) = pose(35, arm, l(-140, -140), at(Joint.HIP, -18, 60), head = 40, foot = -25)

    private fun chestSupportedRow() = side(
        chestSupported(l(-90, -90)), chestSupported(l(-150, -88)),
        grip = Grip.BOTH, props = listOf(Prop.Pad(-30f, 45f, 35f, 91f)),
    )

    private fun chestSupportedFly() = side(
        chestSupported(l(-90, -88)), chestSupported(l(-30, -25, 0.45)),
        grip = Grip.BOTH, props = listOf(Prop.Pad(-30f, 45f, 35f, 91f)),
    )

    /** A squat: from standing to hips back and down. [arm] is how the arms are held. */
    private fun squat(arm: Limb, armTop: Limb, grip: Grip, deep: Boolean = true) = side(
        pose(90, armTop, straight, standing),
        pose(if (deep) 58 else 66, arm, if (deep) l(-12, -115) else l(-25, -110), standing, foot = 0),
        grip = grip,
    )

    private fun rdl(singleLeg: Boolean) = if (singleLeg) side(
        pose(88, l(-90, -90), l(-90, -90), standing, legFar = l(-92, -92), foot = 0),
        pose(8, l(-90, -90), l(-80, -94), standing, legFar = l(185, 185), head = 15, foot = 0),
        grip = Grip.NEAR,
    ) else side(
        pose(88, l(-90, -90), l(-90, -90), standing, foot = 0),
        hinge(12, l(-90, -90)),
        grip = Grip.BOTH,
    )

    private fun goodMorning() = side(
        pose(90, l(-75, -10), l(-90, -90), standing, foot = 0),
        pose(15, l(-160, -55), l(-76, -96), standing, head = 25, foot = 0),
    )

    /** A split stance: front (near) foot fixed; [bottom] is the low point. */
    private fun splitStance(top: Boolean, arm: Limb) = if (top) {
        pose(90, arm, l(-78, -96), at(Joint.NEAR_ANKLE, 25, FOOT_Y), legFar = l(-112, -100), foot = 0)
    } else {
        pose(90, arm, l(-10, -96), at(Joint.NEAR_ANKLE, 25, FOOT_Y), legFar = l(-98, -170), foot = 0)
    }

    private fun splitSquat(grip: Grip) = side(splitStance(true, down), splitStance(false, down), grip = grip)

    private fun reverseLunge(grip: Grip, arm: Limb = down) = side(
        pose(90, arm, l(-90, -90), at(Joint.NEAR_ANKLE, 25, FOOT_Y), foot = 0),
        splitStance(false, arm),
        grip = grip,
    )

    private fun stepUp() = side(
        pose(88, l(-80, -70), l(-35, -100), at(Joint.NEAR_ANKLE, 10, 50), legFar = l(-100, -92), foot = 0),
        pose(90, l(-95, -80), l(-90, -90), at(Joint.NEAR_ANKLE, 10, 50), legFar = l(-80, -110), foot = 0),
        props = listOf(Prop.Pad(-25f, 45f, 45f, 45f)),
    )

    /**
     * Lying face up with the shoulders at the left and the feet planted at [ankleX]; [torso] above 180 lifts the hips.
     * A dumbbell ([grip]) is held on the hips.
     */
    private fun bridgePose(torso: Number, ankleX: Number, grip: Grip = Grip.NONE, farLeg: Limb? = null, shoulder: Pt = Pt(-45f, 11f), head: Number = 182): Pose {
        val base = pose(torso, l(-4, 0), down, at(Joint.SHOULDER, shoulder.x, shoulder.y), head = head, foot = 0)
        val legs = legsTo(base, Pt(ankleX.toFloat(), FOOT_Y))
        val withFar = if (farLeg == null) legs else legs.copy(legFar = farLeg)
        return if (grip == Grip.NONE) withFar else armsTo(withFar) { it.hip + Pt(4f, 8f) }
    }

    private fun bridge(grip: Grip = Grip.NONE, singleLeg: Boolean = false): Motion {
        val far = if (singleLeg) l(22, 22) else null
        return side(bridgePose(180, 38, grip, far?.let { l(48, 48) }), bridgePose(208, 38, grip, far), grip = grip)
    }

    private fun hipThrust(grip: Grip): Motion {
        val s = Pt(-38f, 52f)
        return side(
            bridgePose(140, 48, grip, shoulder = s, head = 120).let { if (grip == Grip.NONE) it.copy(armNear = l(-60, 10), armFar = l(-60, 10)) else it },
            bridgePose(182, 48, grip, shoulder = s, head = 160).let { if (grip == Grip.NONE) it.copy(armNear = l(-60, 10), armFar = l(-60, 10)) else it },
            grip = grip,
            props = listOf(Prop.Pad(-95f, 45f, -30f, 45f)),
        )
    }

    private fun towelCurl() = side(bridgePose(202, 36), bridgePose(194, 78))

    private fun walkout() = side(bridgePose(204, 36), bridgePose(196, 72))

    private fun deadBug(): Motion {
        val armsUp = l(90, 90)
        val table = l(90, 0)
        fun p(arm: Limb, armFar: Limb, leg: Limb, legFar: Limb) =
            pose(180, arm, leg, at(Joint.SHOULDER, -45, 11), armFar = armFar, legFar = legFar, head = 180, foot = 90)
        val rest = p(armsUp, armsUp, table, table)
        return side(
            rest,
            p(armsUp, l(172, 175), l(12, 8), table),
            rest,
            p(l(172, 175), armsUp, table, l(12, 8)),
            seconds = 5.2,
        )
    }

    private fun reverseCrunch() = side(
        onFloor(l(-4, 0), l(90, 0), torso = 180),
        onFloor(l(-4, 0), l(140, 50), torso = 190, head = 182),
    )

    private fun bicycle(): Motion {
        val hands = l(130, -40)
        fun p(nearIn: Boolean) = onFloor(
            hands,
            if (nearIn) l(120, 20) else l(18, 18),
            legFar = if (nearIn) l(18, 18) else l(120, 20),
            torso = 180, head = 150,
        ).copy(torso = 172f)
        return side(p(true), p(false), seconds = 2.4)
    }

    private fun russianTwist() = side(
        pose(118, l(-30, 25, 0.6), l(48, -40), at(Joint.HIP, 0, 12), head = 105, foot = 0),
        pose(118, l(-70, -20, 0.6), l(48, -40), at(Joint.HIP, 0, 12), head = 105, foot = 0),
        grip = Grip.GOBLET,
        seconds = 2.4,
    )

    private fun donkeyKick() = side(allFours(), allFours(leg = l(172, 88)))

    private fun fireHydrant() = side(allFours(), allFours(leg = l(-25, 180, 0.4)))

    private fun birdDog(): Motion {
        val rest = allFours()
        return side(
            rest,
            allFours(arm = l(8, 8), legFar = l(185, 185)),
            rest,
            allFours(armFar = l(8, 8), leg = l(185, 185)),
            seconds = 5.2,
        )
    }

    private fun mountainClimber(): Motion {
        val arm = l(-88, -90)
        val top = plank(arm)
        // the knee comes in tucked and a little across the body, so it clears the floor on the way
        val lift = l(-175, 150, 0.6)
        val drive = l(-22, -175, 0.6)
        return side(
            top, plank(arm, legNear = lift), plank(arm, legNear = drive), plank(arm, legNear = lift),
            top, plank(arm, legFar = lift), plank(arm, legFar = drive), plank(arm, legFar = lift),
            seconds = 3.2,
        )
    }

    private fun yRaise() = side(prone(l(-4, -4, 0.85)), prone(l(14, 14, 0.85), head = 18))

    private fun snowAngel() = side(
        prone(l(178, 178)),
        prone(l(95, 95, 0.15), head = 14),
        prone(l(10, 10, 0.95), head = 14),
        prone(l(95, 95, 0.15), head = 14),
        seconds = 4.4,
    )

    /** Every exercise in the library, by id. */
    val all: Map<String, Motion> by lazy {
        mapOf(
            "bench" to benchPress(),
            "incl" to inclinePress(),
            "ohp" to overheadPress(arnold = false),
            "pushup" to pushUp(),
            "row" to singleArmRow(),
            "brow" to bentRow(),
            "goblet" to squat(l(-62, 100), l(-70, 96), Grip.GOBLET),
            "bsq" to squat(l(0, 0), l(-80, -80), Grip.NONE),
            "rdl" to rdl(singleLeg = false),
            "bridge" to bridge(),
            "dead" to deadBug(),
            "crow" to chestSupportedRow(),
            "pull" to pullover(),
            "rfly" to chestSupportedFly(),
            "curl" to curl(),
            "tri" to overheadTriceps(),
            "lat" to lateralRaise(),
            "split" to splitSquat(Grip.BOTH),
            "stepup" to stepUp(),
            "hcurl" to towelCurl(),
            "hthrust" to hipThrust(Grip.NONE),
            "crunch" to bicycle(),
            "floor" to floorPress(),
            "fly" to benchFly(),
            "ifly" to inclineFly(),
            "ffly" to floorFly(),
            "ipush" to inclinePushUp(),
            "wpush" to pushUp(reach = 0.9),
            "pike" to pikePushUp(),
            "arn" to overheadPress(arnold = true),
            "frt" to frontRaise(),
            "brfly" to bentReverseFly(),
            "yr" to yRaise(),
            "snow" to snowAngel(),
            "hammer" to curl(),
            "icurl" to curl(incline = true),
            "skull" to lyingTriceps(),
            "kick" to kickback(),
            "cpush" to pushUp(),
            "bdip" to benchDip(),
            "sumo" to squat(l(-90, -90), l(-90, -90), Grip.GOBLET, deep = false),
            "rlunge" to reverseLunge(Grip.BOTH),
            "blunge" to reverseLunge(Grip.NONE, l(-60, -10)),
            "srdl" to rdl(singleLeg = true),
            "gm" to goodMorning(),
            "hwalk" to walkout(),
            "whthrust" to hipThrust(Grip.GOBLET),
            "wbridge" to bridge(Grip.GOBLET),
            "sbridge" to bridge(singleLeg = true),
            "donkey" to donkeyKick(),
            "hydrant" to fireHydrant(),
            "bird" to birdDog(),
            "rcrunch" to reverseCrunch(),
            "rtwist" to russianTwist(),
            "mclimb" to mountainClimber(),
            "sbend" to sideBend(),
        )
    }

    fun of(exerciseId: String): Motion? = all[exerciseId]
}
