package com.homeworkoutcoach.app.ui.progress

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Volume
import java.time.ZoneId
import kotlin.math.roundToLong

/** Chart range tabs [RANGES]. [days] 0 means all time. */
enum class Range(val tab: String, val days: Int, val label: String) {
    WEEK("1W", 7, "1 week"),
    MONTH("1M", 30, "1 month"),
    QUARTER("3M", 91, "3 months"),
    HALF("6M", 182, "6 months"),
    YEAR("1Y", 365, "1 year"),
    ALL("All", 0, "All time"),
}

data class ProgressItemUi(val id: String, val name: String, val summary: String)

/** One workout's sets of one exercise. [volume] is kg, or reps for bodyweight. */
data class ExerciseSession(val dateMillis: Long, val volume: Double, val sets: List<LoggedSet>, val pr: Boolean)

data class ChartPoint(val dateMillis: Long, val value: Double)

data class RecentSetUi(val date: String, val text: String, val effort: String?, val pr: Boolean)

data class ExerciseDetailUi(
    val name: String,
    val bodyweight: Boolean,
    val range: Range,
    val points: List<ChartPoint>,
    val recent: List<RecentSetUi>,
)

/** Progress list and per-exercise detail [vProg, vEx, histFor, withPR]. */
class ProgressPresenter(private val lib: Library, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** Every logged session of [id], oldest first, with PR flags. */
    fun history(id: String, workouts: List<Workout>): List<ExerciseSession> {
        val bw = lib.exercise(id).bodyweight
        val logged = workouts.filter { !it.sets[id].isNullOrEmpty() }.sortedBy { it.dateMillis }
        val prs = Volume.personalRecords(bw, logged.map { it.sets.getValue(id) })
        return logged.mapIndexed { i, w ->
            val sets = w.sets.getValue(id)
            ExerciseSession(
                dateMillis = w.dateMillis,
                volume = if (bw) sets.sumOf { it.reps }.toDouble() else sets.sumOf { it.reps * it.weightKg },
                sets = sets,
                pr = prs[i],
            )
        }
    }

    /** Exercises with at least one logged session, in library order. */
    fun list(workouts: List<Workout>): List<ProgressItemUi> = lib.exercises.values.mapNotNull { e ->
        val h = history(e.id, workouts)
        if (h.isEmpty()) return@mapNotNull null
        ProgressItemUi(
            id = e.id,
            name = e.name,
            summary = "${Copy.count(h.size, "session")}, last ${h.last().volume.roundToLong()} ${if (e.bodyweight) "reps" else "kg"}",
        )
    }

    fun detail(id: String, workouts: List<Workout>, range: Range, nowMillis: Long): ExerciseDetailUi {
        val e = lib.exercise(id)
        val all = history(id, workouts)
        val cut = if (range.days > 0) nowMillis - range.days * DAY_MS else Long.MIN_VALUE
        // Best set of a session: highest weight x reps (reps for bodyweight), the later one on a tie.
        fun score(s: LoggedSet) = if (e.bodyweight) s.reps.toDouble() else s.weightKg * s.reps
        return ExerciseDetailUi(
            name = e.name,
            bodyweight = e.bodyweight,
            range = range,
            points = all.filter { it.dateMillis >= cut }.map { ChartPoint(it.dateMillis, it.volume) },
            recent = all.reversed().take(8).map { s ->
                val top = s.sets.reduce { best, t -> if (score(t) >= score(best)) t else best }
                RecentSetUi(
                    date = Copy.dayMonth(s.dateMillis, zone),
                    text = if (e.bodyweight) "${top.reps} reps" else "${Copy.kg(top.weightKg)} kg × ${top.reps}",
                    effort = top.effort.takeIf { it != Effort.JUST_RIGHT }?.key,
                    pr = s.pr,
                )
            },
        )
    }

    companion object {
        const val DAY_MS = 86_400_000L
    }
}
