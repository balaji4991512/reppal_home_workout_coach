package com.homeworkoutcoach.app.ui.week

import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.engine.Day
import com.homeworkoutcoach.engine.ExerciseOutcome
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Progression
import com.homeworkoutcoach.engine.SetCountStatus
import com.homeworkoutcoach.engine.Volume
import kotlin.math.roundToLong

data class WeekUi(
    val hero: HeroUi,
    val days: List<WeekdayUi>,
    /** Names of exercises left out because of concerns or pain. */
    val leftOut: List<String>,
    val volume: VolumeUi,
    /** States worked out for this week that are not stored yet (new exercises, or weights snapped to owned ones). */
    val statesToStore: Map<String, ExerciseState>,
)

data class HeroUi(
    /** "Week 2, Wednesday" */
    val when_: String,
    val title: String,
    /** "5 exercises, about 45 min" */
    val summary: String,
    val total: Int,
    val done: Int,
    val canStart: Boolean,
)

sealed interface WeekdayUi {
    val abbr: String

    data class Rest(override val abbr: String) : WeekdayUi

    data class Training(
        override val abbr: String,
        /** Position in the week sequence. */
        val index: Int,
        val name: String,
        val exerciseCount: Int,
        val status: DayStatus,
        val exercises: List<ExerciseRowUi>,
        val missing: List<String>,
        /** Summary of the saved session, for days already done. */
        val session: SessionCardUi?,
    ) : WeekdayUi
}

enum class DayStatus { DONE, NEXT, UPCOMING }

data class ExerciseRowUi(
    val id: String,
    val baseId: String,
    val name: String,
    val baseName: String,
    val prescription: String,
    val swapped: Boolean,
    /** id to name. */
    val alternatives: List<Pair<String, String>>,
)

data class SessionCardUi(
    val percent: Int,
    val reached: Boolean,
    /** "540 kg lifted, 540 kg planned · 36 reps done, 36 planned" */
    val totals: String,
    val rows: List<SessionRowUi>,
)

data class SessionRowUi(val name: String, val detail: String, val outcome: ExerciseOutcome)

data class VolumeUi(
    /** "Lighter this week: biceps." or "This week covers all major muscles." */
    val headline: String,
    val rows: List<VolumeRowUi>,
    /** "Weekly set targets for beginners. ..." Each row shows its muscle's own target. */
    val setTarget: String,
)

data class VolumeRowUi(val muscle: String, val load: String, val sets: String, val status: SetCountStatus)

/** Turns stored settings and history into the This week screen [vWeek, volCard, sessCard]. Pure, so it is unit-tested directly. */
class WeekPresenter(private val lib: Library, private val planner: Planner = Planner(lib)) {

    fun present(
        settings: UserSettings,
        stored: Map<String, ExerciseState>,
        swaps: Map<String, String>,
        workouts: List<Workout>,
    ): WeekUi {
        val ps = settings.planSettings(swaps)
        val seq = planner.weekSequence(ps)
        val week: List<Day> = seq.map { planner.buildDay(it, ps) }
        val weekdays = planner.weekdays(ps)
        val k = settings.sessionIndex % seq.size
        val weekNumber = settings.sessionIndex / seq.size + 1

        val states = week.flatMap { d -> d.items.map { it.exerciseId } }.distinct().associateWith { id ->
            Progression.resolveState(lib.exercise(id), stored[id], ps.equipment, ps.level)
        }
        // An exercise planned with more sets on one of its days shows that many all week (not stored: it is the day's plan).
        val minSets = week.flatMap { it.items }.groupBy { it.exerciseId }.mapValues { (_, items) -> items.maxOf { it.sets } }
        val state = { id: String ->
            (states[id] ?: Progression.resolveState(lib.exercise(id), stored[id], ps.equipment, ps.level)).atLeast(minSets[id] ?: 0)
        }

        val next = week[k]
        val hero = HeroUi(
            when_ = "Week $weekNumber, ${lib.dayNames[weekdays[k]]}",
            title = planner.dayName(next.label),
            summary = "${Copy.count(next.items.size, "exercise")}, about ${planner.estimateMinutes(next, state)} min",
            total = seq.size,
            done = k,
            canStart = next.items.isNotEmpty(),
        )

        val days = lib.dayNames.mapIndexed { di, dayName ->
            val abbr = dayName.take(3)
            val j = weekdays.indexOf(di)
            if (j < 0) return@mapIndexed WeekdayUi.Rest(abbr)
            val day = week[j]
            val status = when {
                j < k -> DayStatus.DONE
                j == k -> DayStatus.NEXT
                else -> DayStatus.UPCOMING
            }
            val history = if (status == DayStatus.DONE) {
                workouts.getOrNull(settings.weekBase + (weekNumber - 1) * seq.size + j)
            } else null
            WeekdayUi.Training(
                abbr = abbr,
                index = j,
                name = planner.dayName(day.label),
                exerciseCount = day.items.size,
                status = status,
                exercises = day.items.map { item ->
                    val e = lib.exercise(item.exerciseId)
                    ExerciseRowUi(
                        id = e.id,
                        baseId = item.baseId,
                        name = e.name,
                        baseName = lib.exercise(item.baseId).name,
                        prescription = Copy.prescription(e, state(e.id)),
                        swapped = item.swapped,
                        alternatives = planner.swapAlternatives(item, day, ps).map { it to lib.exercise(it).name },
                    )
                },
                missing = day.missing,
                session = history?.let { SessionCards.card(lib, it) },
            )
        }

        return WeekUi(
            hero = hero,
            days = days,
            leftOut = settings.excluded.mapNotNull { lib.exercises[it]?.name },
            volume = volume(settings, week, state),
            statesToStore = states.filter { (id, s) -> stored[id] != s },
        )
    }

    private fun volume(settings: UserSettings, week: List<Day>, state: (String) -> ExerciseState): VolumeUi {
        val v = Volume.plannedWeek(lib, settings.level, week, state, planner.rules)
        val low = v.filterValues { it.status == SetCountStatus.LOW }.keys.map { lib.muscleLabels.getValue(it).lowercase() }
        return VolumeUi(
            headline = if (low.isNotEmpty()) "Lighter this week: ${low.joinToString(", ")}." else "This week covers all major muscles.",
            rows = v.map { (m, w) ->
                val parts = listOfNotNull(
                    w.kg.takeIf { it > 0 }?.let { "${it.roundToLong()} kg" },
                    w.reps.takeIf { it > 0 }?.let { "$it reps" },
                )
                VolumeRowUi(
                    muscle = lib.muscleLabels.getValue(m),
                    load = parts.joinToString(", ").ifEmpty { "none" },
                    sets = "${Copy.kg(w.sets)} of ${planner.rules.sets(m, settings.level)} sets, " + when (w.status) {
                        SetCountStatus.LOW -> "Low"
                        SetCountStatus.HIGH -> "High"
                        SetCountStatus.ON_TARGET -> "On target"
                    },
                    status = w.status,
                )
            },
            setTarget = "Weekly set targets for ${settings.level.key}s. Sets where a muscle only helps count as half.",
        )
    }
}
