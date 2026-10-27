package com.homeworkoutcoach.app.ui.profile

import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.week.SessionCardUi
import com.homeworkoutcoach.app.ui.week.SessionCards
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Progression
import com.homeworkoutcoach.engine.Split
import com.homeworkoutcoach.engine.Volume
import java.time.ZoneId
import kotlin.math.roundToLong

data class ProfileUi(
    val initial: String,
    /** The name, or "Your profile". */
    val title: String,
    /** The email, or "Guest, not signed in". */
    val subtitle: String,
    /** "12 workouts · 5 weigh-ins" */
    val stats: String,
    /** Training setup, one short line each: level and days, equipment, and anything left out. */
    val setup: List<String>,
    /** Exercise name to next prescription, for exercises done at least once. */
    val current: List<Pair<String, String>>,
    val history: List<HistoryItemUi>,
    /** Signed in with Google: the account can be deleted. */
    val signedIn: Boolean = false,
)

data class HistoryItemUi(val id: Long, val title: String, val totals: String, val card: SessionCardUi)

/** Profile [vProfile]: who you are, your setup, where you are now, history, and your data. */
class ProfilePresenter(
    private val lib: Library,
    private val planner: Planner = Planner(lib),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun present(
        settings: UserSettings,
        states: Map<String, ExerciseState>,
        workouts: List<Workout>,
        account: Account? = null,
        weighIns: Int = 0,
    ): ProfileUi {
        val equipment = settings.equipment()
        val trained = workouts.flatMap { w -> w.plan.map { it.exerciseId } }.toSet()
        val leftOut = settings.excluded.mapNotNull { lib.exercises[it]?.name }
        return ProfileUi(
            initial = (settings.name.ifBlank { account?.name ?: "R" }).take(1).uppercase(),
            title = settings.name.ifBlank { "Your profile" },
            subtitle = account?.email ?: "Guest, not signed in",
            stats = "${Copy.count(workouts.size, "workout")} · ${Copy.count(weighIns, "weigh-in")}",
            setup = listOfNotNull(
                "${Copy.levelName(settings.level)} · ${Copy.count(settings.daysPerWeek, "day")} a week · ${splitName(settings.split)}",
                "${Copy.benchName(settings.bench)} · ${dumbbells(settings.dumbbellsKg)}",
                if (leftOut.isNotEmpty()) "Working around: ${leftOut.joinToString(", ")}" else null,
            ),
            // Exercises left out (for pain, or a concern) have no next session, so no target here.
            current = lib.exercises.values.filter { it.id in states && it.id in trained && it.id !in settings.excluded }.map { e ->
                e.name to Copy.prescription(e, Progression.resolveState(e, states[e.id], equipment, settings.level))
            },
            signedIn = account != null,
            history = workouts.reversed().map { w ->
                val s = Volume.summarize(lib, w.plan, w.sets, w.painIds)
                HistoryItemUi(
                    id = w.id,
                    title = planner.dayName(w.label) + ", " + Copy.weekdayDayMonth(w.dateMillis, zone),
                    totals = listOfNotNull(
                        s.liftedKg.takeIf { it > 0 }?.let { "${it.roundToLong()} kg" },
                        s.bodyweightReps.takeIf { it > 0 }?.let { "$it bodyweight reps" },
                    ).joinToString(", "),
                    card = SessionCards.card(lib, w),
                )
            },
        )
    }

    private fun splitName(split: Split) = when (split) {
        Split.AUTO -> "Auto plan"
        Split.CUSTOM -> "Own split"
        else -> Copy.SPLITS.first { it.first == split }.second
    }

    /** "No dumbbells", "2, 4, 8 kg", or "12 pairs, 1–24 kg" for a long list. */
    private fun dumbbells(kg: List<Double>): String {
        val sorted = kg.sorted()
        return when {
            sorted.isEmpty() -> "No dumbbells"
            sorted.size <= 4 -> sorted.joinToString(", ") { Copy.kg(it) } + " kg"
            else -> "${sorted.size} pairs, ${Copy.kg(sorted.first())}–${Copy.kg(sorted.last())} kg"
        }
    }
}
