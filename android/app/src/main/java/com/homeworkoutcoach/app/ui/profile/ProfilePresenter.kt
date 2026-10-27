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
import com.homeworkoutcoach.engine.Volume
import java.time.ZoneId
import kotlin.math.roundToLong

data class ProfileUi(
    val initial: String,
    val title: String,
    /** "3 workouts saved on this device." */
    val saved: String,
    val rows: List<Pair<String, String>>,
    /** Exercise name to next prescription, for exercises done at least once. */
    val current: List<Pair<String, String>>,
    val history: List<HistoryItemUi>,
)

data class HistoryItemUi(val id: Long, val title: String, val totals: String, val card: SessionCardUi)

/** History and profile [vProfile]. */
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
    ): ProfileUi {
        val equipment = settings.equipment()
        val trained = workouts.flatMap { w -> w.plan.map { it.exerciseId } }.toSet()
        return ProfileUi(
            initial = (settings.name.ifBlank { "R" }).take(1).uppercase(),
            title = settings.name.ifBlank { "Your profile" },
            saved = "${Copy.count(workouts.size, "workout")} saved on this device.",
            rows = listOf(
                "Account" to (account?.email ?: "Guest, not signed in"),
                "Level" to Copy.levelName(settings.level),
                "Days per week" to "${settings.daysPerWeek}",
                "Bench" to Copy.benchName(settings.bench),
                "Dumbbells" to settings.dumbbellsKg.sorted().joinToString(", ") { Copy.kg(it) } + " kg",
                "Working around" to settings.excluded.mapNotNull { lib.exercises[it]?.name }.joinToString(", ").ifEmpty { "Nothing" },
            ),
            // Exercises left out (for pain, or a concern) have no next session, so no target here.
            current = lib.exercises.values.filter { it.id in states && it.id in trained && it.id !in settings.excluded }.map { e ->
                e.name to Copy.prescription(e, Progression.resolveState(e, states[e.id], equipment, settings.level))
            },
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
}
