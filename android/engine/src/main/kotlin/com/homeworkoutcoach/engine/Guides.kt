package com.homeworkoutcoach.engine

/** How to do one exercise: the steps in order, and what to watch out for. Plain words for beginners. */
data class Guide(val steps: List<String>, val tips: List<String>)

object Guides {
    private fun g(steps: List<String>, tips: List<String>) = Guide(steps, tips)

    private val all: Map<String, Guide> = mapOf(
        "bench" to g(
            listOf(
                "Lie on the bench with your feet flat on the floor and a dumbbell in each hand at chest level.",
                "Press the dumbbells up until your arms are straight above your chest, breathing out.",
                "Lower them slowly back to the sides of your chest, elbows about 45 degrees from your body.",
            ),
            listOf("Keep your shoulder blades pulled back and down on the bench.", "Don't let the dumbbells drift over your face."),
        ),
        "incl" to g(
            listOf(
                "Set the bench to a low incline and sit back with a dumbbell in each hand at your upper chest.",
                "Press up and slightly back until your arms are straight over your shoulders.",
                "Lower slowly to the top of your chest.",
            ),
            listOf("A low incline is enough; too steep turns it into a shoulder press.", "Keep your feet planted and your lower back on the bench."),
        ),
        "ohp" to g(
            listOf(
                "Stand tall with the dumbbells at shoulder height, palms facing forward.",
                "Brace your stomach and press the dumbbells straight up until your arms are straight.",
                "Lower them back to your shoulders with control.",
            ),
            listOf("Don't lean back or arch your lower back to get the weight up.", "Squeeze your glutes to keep your body steady."),
        ),
        "pushup" to g(
            listOf(
                "Start in a plank with your hands a little wider than your shoulders and your body in a straight line.",
                "Bend your elbows to lower your chest toward the floor, elbows angled back.",
                "Push the floor away to straighten your arms, breathing out.",
            ),
            listOf("Keep your hips in line: don't let them sag or pike up.", "Too hard? Put your knees down or your hands on a bench."),
        ),
        "row" to g(
            listOf(
                "Put one knee and the same hand on the bench, back flat, the other foot on the floor.",
                "Let the dumbbell hang under your shoulder, then pull it toward your hip, elbow close to your side.",
                "Lower it slowly until your arm is straight again. Do all reps, then switch sides.",
            ),
            listOf("Lead with your elbow, not your hand.", "Keep your back flat and don't twist to lift the weight."),
        ),
        "brow" to g(
            listOf(
                "Hold a dumbbell in each hand, soften your knees and hinge forward until your back is at about 45 degrees.",
                "Pull both dumbbells toward your hips, squeezing your shoulder blades together.",
                "Lower them slowly until your arms are straight.",
            ),
            listOf("Keep a flat back and look at the floor a little ahead of you.", "If your lower back tires, use the chest-supported row instead."),
        ),
        "goblet" to g(
            listOf(
                "Hold one dumbbell upright against your chest, feet shoulder-width apart, toes slightly out.",
                "Sit your hips back and down, keeping your chest up, until your thighs are about level with the floor.",
                "Push through your whole foot to stand back up, breathing out.",
            ),
            listOf("Let your knees travel in line with your toes, not inward.", "Only go as low as you can keep your heels down and back straight."),
        ),
        "bsq" to g(
            listOf(
                "Stand with your feet shoulder-width apart, arms in front for balance.",
                "Sit your hips back and down as if onto a chair, chest up.",
                "Stand up by pushing through your heels and the middle of your feet.",
            ),
            listOf("Keep your knees in line with your toes.", "Use a chair behind you to sit to if you need a guide."),
        ),
        "rdl" to g(
            listOf(
                "Stand holding the dumbbells in front of your thighs, knees soft.",
                "Push your hips back and slide the dumbbells down your legs, back flat, until you feel a stretch behind your thighs.",
                "Squeeze your glutes and push your hips forward to stand tall.",
            ),
            listOf("It is a hip hinge, not a squat: your knees bend only a little.", "Keep the dumbbells close to your legs the whole way."),
        ),
        "bridge" to g(
            listOf(
                "Lie on your back with knees bent and feet flat, hip-width apart, arms by your sides.",
                "Squeeze your glutes and lift your hips until your body is in a line from shoulders to knees.",
                "Hold for a moment, then lower slowly.",
            ),
            listOf("Push through your heels, not your toes.", "Don't arch your lower back at the top; stop when your hips are in line."),
        ),
        "dead" to g(
            listOf(
                "Lie on your back with arms pointing to the ceiling and knees bent at 90 degrees above your hips.",
                "Press your lower back gently into the floor.",
                "Slowly lower one arm behind your head and the opposite leg toward the floor, then return and switch sides.",
            ),
            listOf("Your lower back should stay in touch with the floor.", "Go only as far as you can keep it there; small moves are fine."),
        ),
        "crow" to g(
            listOf(
                "Set the bench to a low incline and lie face down on it, dumbbells hanging below your shoulders.",
                "Pull the dumbbells toward your hips, squeezing your shoulder blades together.",
                "Lower them slowly until your arms are straight.",
            ),
            listOf("Keep your chest on the pad the whole time.", "Don't shrug your shoulders up toward your ears."),
        ),
        "pull" to g(
            listOf(
                "Lie on the bench holding one dumbbell with both hands straight above your chest.",
                "Keeping a slight bend in your elbows, lower the dumbbell back over your head in an arc.",
                "Stop when you feel a stretch, then pull it back over your chest.",
            ),
            listOf("Hold the dumbbell securely by the top plate.", "Only lower as far as is comfortable for your shoulders."),
        ),
        "rfly" to g(
            listOf(
                "Lie face down on an incline bench with light dumbbells hanging below your shoulders.",
                "With a slight bend in your elbows, raise your arms out to the sides up to shoulder height.",
                "Lower them slowly.",
            ),
            listOf("Use light weights; the rear shoulder muscles are small.", "Lead with your elbows and don't swing."),
        ),
        "curl" to g(
            listOf(
                "Stand tall with a dumbbell in each hand, arms straight, palms facing forward.",
                "Bend your elbows to curl the dumbbells up toward your shoulders.",
                "Lower them slowly until your arms are straight.",
            ),
            listOf("Keep your elbows by your sides.", "No swinging: if you need to lean back, the weight is too heavy."),
        ),
        "tri" to g(
            listOf(
                "Hold one dumbbell with both hands straight above your head.",
                "Bend your elbows to lower it behind your head, upper arms pointing up.",
                "Straighten your arms to lift it back up.",
            ),
            listOf("Keep your elbows pointing forward, not out to the sides.", "Brace your stomach so your back doesn't arch."),
        ),
        "lat" to g(
            listOf(
                "Stand with light dumbbells at your sides, a slight bend in your elbows.",
                "Raise your arms out to the sides up to shoulder height.",
                "Lower them slowly.",
            ),
            listOf("Lead with your elbows, as if pouring water from a jug.", "Don't shrug or swing; lighter is better here."),
        ),
        "split" to g(
            listOf(
                "Stand in a long stride, one foot forward and the other back on its toes, dumbbells at your sides.",
                "Bend both knees to lower your back knee toward the floor.",
                "Push through your front foot to rise. Do all reps, then switch legs.",
            ),
            listOf("Keep your front knee over your foot, not past your toes by much or caving in.", "Stay upright; hold a wall for balance at first."),
        ),
        "stepup" to g(
            listOf(
                "Stand facing the bench and put one whole foot on it.",
                "Push through that foot to step up until you stand tall on the bench.",
                "Step down slowly with the other foot. Do all reps, then switch legs.",
            ),
            listOf("Make sure the bench is steady and won't slide.", "Let the top leg do the work rather than pushing off the floor."),
        ),
        "hcurl" to g(
            listOf(
                "Lie on your back with your heels on a towel on a smooth floor, knees bent.",
                "Lift your hips into a bridge.",
                "Slide your heels away until your legs are nearly straight, then pull them back in, keeping your hips up.",
            ),
            listOf("Keep your hips lifted the whole time.", "Start with small slides if your hamstrings cramp."),
        ),
        "hthrust" to g(
            listOf(
                "Sit on the floor with your upper back against the edge of the bench, knees bent, feet flat.",
                "Push through your heels to lift your hips until your body is flat from shoulders to knees.",
                "Squeeze your glutes at the top, then lower slowly.",
            ),
            listOf("Tuck your chin slightly and keep your ribs down.", "Your shins should be upright at the top; move your feet if not."),
        ),
        "crunch" to g(
            listOf(
                "Lie on your back with your hands lightly behind your head and your legs raised, knees bent.",
                "Lift your shoulders and bring one elbow toward the opposite knee while the other leg straightens.",
                "Switch sides in a slow pedalling motion.",
            ),
            listOf("Don't pull on your neck; your hands only rest there.", "Slow and controlled beats fast."),
        ),
        "floor" to g(
            listOf(
                "Lie on the floor with knees bent and a dumbbell in each hand, elbows on the floor.",
                "Press the dumbbells up until your arms are straight above your chest.",
                "Lower slowly until your upper arms touch the floor.",
            ),
            listOf("Let your elbows touch down gently; don't bounce them.", "Keep your elbows about 45 degrees from your body."),
        ),
        "fly" to g(
            listOf(
                "Lie on the bench with dumbbells held above your chest, palms facing each other.",
                "With a slight bend in your elbows, open your arms out to the sides in a wide arc.",
                "Stop when you feel a stretch in your chest, then bring them back up together.",
            ),
            listOf("Use lighter weights than for pressing.", "Keep the bend in your elbows the same throughout."),
        ),
        "ifly" to g(
            listOf(
                "Set the bench to a low incline and hold the dumbbells above your chest, palms facing each other.",
                "With elbows slightly bent, open your arms out wide in an arc.",
                "Bring them back together over your chest.",
            ),
            listOf("Don't lower further than is comfortable for your shoulders.", "Keep your feet planted and back on the bench."),
        ),
        "ffly" to g(
            listOf(
                "Lie on the floor with knees bent and dumbbells above your chest, palms facing each other.",
                "With elbows slightly bent, open your arms until your upper arms touch the floor.",
                "Bring the dumbbells back together above your chest.",
            ),
            listOf("The floor stops you going too deep, which makes this a safe start.", "Touch down gently."),
        ),
        "ipush" to g(
            listOf(
                "Put your hands on the edge of a steady bench, a little wider than your shoulders, and walk your feet back.",
                "Lower your chest to the bench edge, body in a straight line.",
                "Push back up until your arms are straight.",
            ),
            listOf("The higher the surface, the easier it is.", "Make sure the bench can't tip or slide."),
        ),
        "wpush" to g(
            listOf(
                "Start in a plank with your hands placed wider than for a normal push-up.",
                "Lower your chest toward the floor, body straight.",
                "Push back up.",
            ),
            listOf("Don't go so wide that your shoulders hurt.", "Knees down is fine while you build strength."),
        ),
        "pike" to g(
            listOf(
                "Start in a push-up position, then walk your feet in and lift your hips so your body makes an upside-down V.",
                "Bend your elbows to lower the top of your head toward the floor between your hands.",
                "Push back up to straight arms.",
            ),
            listOf("Keep your hips high; that puts the work on your shoulders.", "Go slowly and stop short if you feel neck strain."),
        ),
        "arn" to g(
            listOf(
                "Sit or stand holding the dumbbells in front of your shoulders, palms facing you.",
                "Press up, turning your palms to face forward as you go, until your arms are straight.",
                "Reverse the turn as you lower back to the start.",
            ),
            listOf("Make the turn smooth, not jerky.", "Use a little less weight than for a normal shoulder press."),
        ),
        "frt" to g(
            listOf(
                "Stand with light dumbbells in front of your thighs.",
                "Raise them straight in front of you to shoulder height.",
                "Lower slowly.",
            ),
            listOf("Keep a slight bend in your elbows.", "Don't lean back or swing the weights up."),
        ),
        "brfly" to g(
            listOf(
                "Hinge forward with a flat back, knees soft, light dumbbells hanging below your chest.",
                "Raise your arms out to the sides until they are level with your body.",
                "Lower them slowly.",
            ),
            listOf("Keep your back flat and your neck in line.", "Squeeze your shoulder blades at the top."),
        ),
        "yr" to g(
            listOf(
                "Lie face down with your arms stretched overhead in a Y, thumbs up.",
                "Lift your arms a few centimetres off the floor by squeezing your upper back.",
                "Hold for a second, then lower.",
            ),
            listOf("The lift is small; that is normal.", "Keep your forehead down and your neck relaxed."),
        ),
        "snow" to g(
            listOf(
                "Lie face down with your arms by your sides, palms down, just off the floor.",
                "Sweep your arms out and around to above your head, keeping them off the floor.",
                "Sweep them back to your sides.",
            ),
            listOf("Move slowly, like making a snow angel.", "Keep your neck long and your eyes on the floor."),
        ),
        "hammer" to g(
            listOf(
                "Stand tall with dumbbells at your sides, palms facing your legs.",
                "Curl the dumbbells up toward your shoulders, keeping palms facing in.",
                "Lower them slowly.",
            ),
            listOf("Keep your elbows by your sides.", "Don't swing your body."),
        ),
        "icurl" to g(
            listOf(
                "Sit back on an incline bench with your arms hanging straight down, dumbbells in hand.",
                "Curl the dumbbells up without moving your upper arms.",
                "Lower them all the way down slowly.",
            ),
            listOf("This stretches the biceps; use lighter weights.", "Keep your back and head on the bench."),
        ),
        "skull" to g(
            listOf(
                "Lie on the bench holding dumbbells straight above your shoulders, palms facing each other.",
                "Bend only your elbows to lower the dumbbells beside your head.",
                "Straighten your arms to lift them back up.",
            ),
            listOf("Keep your upper arms still and pointing up.", "Go slowly near your head."),
        ),
        "kick" to g(
            listOf(
                "Hinge forward with a flat back, dumbbells in hand, upper arms close to your sides and elbows bent.",
                "Straighten your arms back until they are in line with your body.",
                "Bend them back to the start slowly.",
            ),
            listOf("Only your forearms move; keep your upper arms still.", "Light weights work well."),
        ),
        "cpush" to g(
            listOf(
                "Start in a plank with your hands under your shoulders or a little closer.",
                "Lower your chest, keeping your elbows close to your sides.",
                "Push back up.",
            ),
            listOf("Keep your body in a straight line.", "Put your knees down if you can't keep good form."),
        ),
        "bdip" to g(
            listOf(
                "Sit on the edge of the bench, hands beside your hips, then slide your hips off with your feet on the floor.",
                "Bend your elbows to lower your hips toward the floor.",
                "Push through your hands to straighten your arms.",
            ),
            listOf("Keep your back close to the bench.", "Stop at about 90 degrees at the elbow; deeper can strain the shoulders."),
        ),
        "sumo" to g(
            listOf(
                "Stand with your feet wide and toes turned out, holding one dumbbell between your legs.",
                "Sit your hips down, keeping your chest up and knees pushing out over your toes.",
                "Stand up by pushing through your heels.",
            ),
            listOf("Your knees should follow your toes outward.", "Keep the dumbbell hanging straight down."),
        ),
        "rlunge" to g(
            listOf(
                "Stand tall with dumbbells at your sides.",
                "Step one foot back and bend both knees to lower your back knee toward the floor.",
                "Push through your front foot to step back to standing. Alternate legs.",
            ),
            listOf("Stepping back is easier on the knees than stepping forward.", "Keep your front knee over your foot."),
        ),
        "blunge" to g(
            listOf(
                "Stand tall, hands on your hips or in front for balance.",
                "Step one foot back and lower your back knee toward the floor.",
                "Push through your front foot to return. Alternate legs.",
            ),
            listOf("Take a long enough step that your front shin stays fairly upright.", "Hold a wall for balance at first."),
        ),
        "srdl" to g(
            listOf(
                "Stand on one leg with a dumbbell in the opposite hand, knee soft.",
                "Hinge forward at the hip as your free leg lifts behind you, back flat.",
                "Squeeze your glute to return to standing. Do all reps, then switch.",
            ),
            listOf("Keep your hips level; don't let them open to the side.", "Touch a wall or chair for balance."),
        ),
        "gm" to g(
            listOf(
                "Stand with your feet hip-width apart, hands on your hips, knees soft.",
                "Push your hips back and lean your chest forward with a flat back.",
                "Squeeze your glutes to stand back up.",
            ),
            listOf("Stop when you feel a stretch behind your thighs.", "Keep your back flat; don't round it."),
        ),
        "hwalk" to g(
            listOf(
                "Lie on your back, knees bent, and lift your hips into a bridge.",
                "Walk your heels away from you in small steps, keeping your hips up.",
                "Walk them back in, then lower.",
            ),
            listOf("Only go as far as you can keep your hips lifted.", "Small steps are fine."),
        ),
        "whthrust" to g(
            listOf(
                "Sit with your upper back on the bench edge and a dumbbell resting on your hips, held in place.",
                "Push through your heels to lift your hips until your body is flat from shoulders to knees.",
                "Squeeze your glutes, then lower slowly.",
            ),
            listOf("Put a folded towel under the dumbbell for comfort.", "Keep your chin tucked and ribs down."),
        ),
        "wbridge" to g(
            listOf(
                "Lie on your back, knees bent, feet flat, with a dumbbell resting on your hips.",
                "Squeeze your glutes and lift your hips until they are in line with your shoulders and knees.",
                "Lower slowly.",
            ),
            listOf("Hold the dumbbell so it can't roll.", "Push through your heels."),
        ),
        "sbridge" to g(
            listOf(
                "Lie on your back with one foot flat and the other leg straight or raised.",
                "Push through the planted heel to lift your hips.",
                "Lower slowly. Do all reps, then switch sides.",
            ),
            listOf("Keep your hips level; don't let one side drop.", "Do normal bridges first if this is too hard."),
        ),
        "donkey" to g(
            listOf(
                "Start on hands and knees, hands under shoulders, knees under hips.",
                "Keeping the knee bent, lift one leg back and up until your thigh is level with your body.",
                "Lower it slowly. Do all reps, then switch.",
            ),
            listOf("Keep your back flat; don't arch to lift higher.", "Squeeze your glute at the top."),
        ),
        "hydrant" to g(
            listOf(
                "Start on hands and knees, back flat.",
                "Keeping the knee bent, lift one leg out to the side.",
                "Lower it slowly. Do all reps, then switch.",
            ),
            listOf("Keep your hips square to the floor; don't lean away.", "A small range is fine."),
        ),
        "bird" to g(
            listOf(
                "Start on hands and knees with a flat back.",
                "Reach one arm forward and the opposite leg back until both are in line with your body.",
                "Return slowly and switch sides.",
            ),
            listOf("Keep your hips level, as if balancing a glass on your back.", "Move slowly; balance is the point."),
        ),
        "rcrunch" to g(
            listOf(
                "Lie on your back with your knees bent and lifted above your hips, arms by your sides.",
                "Curl your hips off the floor, bringing your knees toward your chest.",
                "Lower your hips slowly.",
            ),
            listOf("Use your stomach, not momentum.", "Press your arms lightly into the floor for support."),
        ),
        "rtwist" to g(
            listOf(
                "Sit with knees bent and feet on the floor, lean back slightly, holding a dumbbell at your chest.",
                "Turn your upper body to one side, moving the dumbbell beside your hip.",
                "Turn slowly to the other side.",
            ),
            listOf("Keep your back straight, not rounded.", "Feet down is easier; lift them only when that feels easy."),
        ),
        "mclimb" to g(
            listOf(
                "Start in a plank with your hands under your shoulders.",
                "Drive one knee toward your chest, then step it back.",
                "Switch legs in a steady rhythm.",
            ),
            listOf("Keep your hips level; don't let them bounce up.", "Slow is fine; speed comes later."),
        ),
        "sbend" to g(
            listOf(
                "Stand tall with a dumbbell in one hand at your side, the other hand on your hip.",
                "Lean slowly toward the dumbbell side.",
                "Use the other side of your waist to pull yourself back up. Do all reps, then switch.",
            ),
            listOf("Bend only to the side, not forward or back.", "Move slowly; a moderate weight is enough."),
        ),
    )

    fun of(exerciseId: String): Guide? = all[exerciseId]

    val ids: Set<String> get() = all.keys
}
