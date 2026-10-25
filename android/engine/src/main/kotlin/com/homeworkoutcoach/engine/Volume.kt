package com.homeworkoutcoach.engine

enum class ExerciseOutcome { HIT, SHORT, STOPPED }

data class ExerciseSummary(
    val planned: PlannedExercise,
    val sets: List<LoggedSet>,
    val outcome: ExerciseOutcome,
    /** kg for weighted moves, reps for bodyweight. */
    val plannedVolume: Double,
    val actualVolume: Double,
)

data class SessionSummary(
    val rows: List<ExerciseSummary>,
    val liftedKg: Double,
    val plannedKg: Double,
    val bodyweightReps: Int,
    val plannedBodyweightReps: Int,
    /** Weighted percent of plan, or the reps percent when the plan is bodyweight only. Null for an empty plan. */
    val percent: Int?,
    val targetReached: Boolean,
    /** Primary muscle credit only: kg for weighted, reps for bodyweight. */
    val kgByMuscle: Map<Muscle, Double>,
    val repsByMuscle: Map<Muscle, Int>,
)

enum class SetCountStatus { LOW, ON_TARGET, HIGH }

data class MuscleWeek(
    val kg: Double,
    val reps: Int,
    /** Weekly sets, secondary muscles counted at half, rounded to 0.5. */
    val sets: Double,
    val status: SetCountStatus,
)

object Volume {
    private val HALF_RANGE_MUSCLES = setOf(Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS)

    /** External load only: bodyweight sets count 0 kg. */
    fun setKg(exercise: Exercise, reps: Int, weightKg: Double): Double = if (exercise.bodyweight) 0.0 else reps * weightKg

    /** Workout summary [finishLoad + sessCard]. Rows follow [plan] order. */
    fun summarize(
        lib: Library,
        plan: List<PlannedExercise>,
        sets: Map<String, List<LoggedSet>>,
        pain: Set<String>,
    ): SessionSummary {
        val kgByMuscle = mutableMapOf<Muscle, Double>()
        val repsByMuscle = mutableMapOf<Muscle, Int>()
        var lifted = 0.0
        var reps = 0
        for ((id, list) in sets) {
            val e = lib.exercise(id)
            for (s in list) {
                if (e.bodyweight) {
                    e.primary.forEach { repsByMuscle.merge(it, s.reps, Int::plus) }
                    reps += s.reps
                } else {
                    val kg = s.reps * s.weightKg
                    e.primary.forEach { kgByMuscle.merge(it, kg, Double::plus) }
                    lifted += kg
                }
            }
        }

        var plannedKg = 0.0
        var plannedReps = 0
        val rows = plan.map { p ->
            val e = lib.exercise(p.exerciseId)
            val done = sets[p.exerciseId].orEmpty()
            val expected = p.sets * p.targetReps * (if (e.bodyweight) 1.0 else p.weightKg)
            val got = if (e.bodyweight) done.sumOf { it.reps }.toDouble() else done.sumOf { it.reps * it.weightKg }
            val metLiveTargets = done.all { it.reps >= it.targetReps }
            val stopped = p.exerciseId in pain
            val hit = !stopped && done.size >= p.sets && (got >= expected || (metLiveTargets && !e.bodyweight))
            if (e.bodyweight) plannedReps += expected.toInt() else plannedKg += expected
            ExerciseSummary(
                planned = p,
                sets = done,
                outcome = when {
                    hit -> ExerciseOutcome.HIT
                    stopped -> ExerciseOutcome.STOPPED
                    else -> ExerciseOutcome.SHORT
                },
                plannedVolume = expected,
                actualVolume = got,
            )
        }

        val kgPct = if (plannedKg > 0) jsRound(lifted / plannedKg * 100) else null
        val repsPct = if (plannedReps > 0) jsRound(reps.toDouble() / plannedReps * 100) else null
        return SessionSummary(
            rows = rows,
            liftedKg = lifted,
            plannedKg = plannedKg,
            bodyweightReps = reps,
            plannedBodyweightReps = plannedReps,
            percent = kgPct ?: repsPct,
            targetReached = (kgPct == null || kgPct >= 100) && (repsPct == null || repsPct >= 100),
            kgByMuscle = kgByMuscle,
            repsByMuscle = repsByMuscle,
        )
    }

    /** Planned volume for the week per muscle [volCard]. Every muscle is present in the result. */
    fun plannedWeek(lib: Library, level: Level, week: List<Day>, stateOf: (String) -> ExerciseState): Map<Muscle, MuscleWeek> {
        val sets = mutableMapOf<Muscle, Double>()
        val kg = mutableMapOf<Muscle, Double>()
        val reps = mutableMapOf<Muscle, Int>()
        for (day in week) for (item in day.items) {
            val e = lib.exercise(item.exerciseId)
            val s = stateOf(item.exerciseId)
            e.primary.forEach { sets.merge(it, s.sets.toDouble(), Double::plus) }
            e.secondary.forEach { sets.merge(it, s.sets / 2.0, Double::plus) }
            if (e.bodyweight) e.primary.forEach { reps.merge(it, s.sets * s.targetReps, Int::plus) }
            else e.primary.forEach { kg.merge(it, s.sets * s.targetReps * s.weightKg, Double::plus) }
        }
        val range = lib.weeklySetRanges.getValue(level)
        return Muscle.entries.associateWith { m ->
            val f = if (m in HALF_RANGE_MUSCLES) 0.5 else 1.0
            val v = jsRound((sets[m] ?: 0.0) * 2) / 2.0
            MuscleWeek(
                kg = kg[m] ?: 0.0,
                reps = reps[m] ?: 0,
                sets = v,
                status = when {
                    v < range.low * f -> SetCountStatus.LOW
                    v > range.high * f -> SetCountStatus.HIGH
                    else -> SetCountStatus.ON_TARGET
                },
            )
        }
    }

    /**
     * Personal records [withPR]. [sessions] are oldest first, each the sets of one workout.
     * A session is a PR when one of its sets beats every earlier set (weight x reps, or reps for bodyweight).
     */
    fun personalRecords(bodyweight: Boolean, sessions: List<List<LoggedSet>>): List<Boolean> {
        var best = 0.0
        return sessions.map { session ->
            var pr = false
            for (s in session) {
                val score = if (bodyweight) s.reps.toDouble() else s.weightKg * s.reps
                if (score > best) {
                    if (best > 0) pr = true
                    best = score
                }
            }
            pr
        }
    }

    /** Axis step for charts: 1, 2, 5 or 10 times a power of ten. [niceStep] */
    fun niceStep(range: Double): Double {
        val r = if (range == 0.0) 1.0 else range
        val p = Math.pow(10.0, Math.floor(Math.log10(r)))
        val f = r / p
        return (if (f <= 1) 1 else if (f <= 2) 2 else if (f <= 5) 5 else 10) * p
    }
}
