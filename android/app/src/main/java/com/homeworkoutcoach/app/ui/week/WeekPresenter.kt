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
import com.homeworkoutcoach.engine.Schedule
import com.homeworkoutcoach.engine.SessionStatus
import com.homeworkoutcoach.engine.SetCountStatus
import com.homeworkoutcoach.engine.Volume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
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

/** What the top of the week says about today. */
enum class HeroKind {
    /** Today is a training day and the workout is not done yet. */
    TODAY,

    /** Today's workout is done. */
    DONE_TODAY,

    /** Today is not a training day. */
    REST,
}

data class HeroUi(
    /** "Wednesday 28 October" */
    val when_: String,
    /** Today's workout, "Workout done", or "Rest day". */
    val title: String,
    /** "5 exercises, about 45 min", or what is next on a rest day. */
    val summary: String,
    /** Workouts planned this week (not counting days before the plan began), and how many are done. */
    val total: Int,
    val done: Int,
    /** True only when today's own workout can be started. */
    val canStart: Boolean,
    val kind: HeroKind = HeroKind.TODAY,
    /** The next workout after today: its name and weekday ("Fri"), or null when none is left this week. */
    val nextTitle: String? = null,
    val nextDay: String? = null,
)

sealed interface WeekdayUi {
    val abbr: String

    data class Rest(override val abbr: String) : WeekdayUi

    data class Training(
        override val abbr: String,
        /** Position in the week sequence. */
        val index: Int,
        val date: LocalDate,
        val name: String,
        val exerciseCount: Int,
        val status: DayStatus,
        val exercises: List<ExerciseRowUi>,
        val missing: List<String>,
        /** Summary of the saved session, for days already done or made up. */
        val session: SessionCardUi?,
        /** For a made-up day, the weekday it was done on ("Wed"). */
        val madeUpOn: String? = null,
        /** The saved workout for a done or made-up day, so it can be corrected. */
        val workoutId: Long? = null,
    ) : WeekdayUi
}

/** A training day against the calendar: done, skipped (its day passed without one), today, or still to come. */
enum class DayStatus { DONE, MADE_UP, SKIPPED, TODAY, UPCOMING }

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
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeekUi {
        val ps = settings.planSettings(swaps)
        val seq = planner.weekSequence(ps)
        val week: List<Day> = seq.map { planner.buildDay(it, ps) }
        val weekdays = planner.weekdays(ps)

        // Each session sits on its own weekday of this calendar week: done, skipped, today or still to come.
        fun dateOf(w: Workout) = Instant.ofEpochMilli(w.dateMillis).atZone(zone).toLocalDate()
        // A workout with a "makes up" day does not count for the day it was done on, only for the day it makes up.
        val doneDates = workouts.filter { it.madeUpForEpochDay == null }.map(::dateOf).toSet()
        val madeUp = workouts.filter { it.madeUpForEpochDay != null }.associateBy { LocalDate.ofEpochDay(it.madeUpForEpochDay!!) }
        val schedule = Schedule.week(weekdays, today, doneDates, settings.planStartEpochDay?.let(LocalDate::ofEpochDay), madeUp.keys)

        val states = week.flatMap { d -> d.items.map { it.exerciseId } }.distinct().associateWith { id ->
            Progression.resolveState(lib.exercise(id), stored[id], ps.equipment, ps.level)
        }
        // An exercise planned with more sets on one of its days shows that many all week (not stored: it is the day's plan).
        val minSets = week.flatMap { it.items }.groupBy { it.exerciseId }.mapValues { (_, items) -> items.maxOf { it.sets } }
        val state = { id: String ->
            (states[id] ?: Progression.resolveState(lib.exercise(id), stored[id], ps.equipment, ps.level)).atLeast(minSets[id] ?: 0)
        }

        val counted = schedule.count { it.status != SessionStatus.BEFORE_PLAN }
        val done = schedule.count { it.status == SessionStatus.DONE || it.status == SessionStatus.MADE_UP }
        val todaySession = Schedule.today(schedule)
        val doneToday = schedule.firstOrNull { it.date == today && it.status == SessionStatus.DONE }
        val upcoming = Schedule.next(schedule)
        val nextTitle = upcoming?.let { planner.dayName(week[it.index].label) }
        val nextDay = upcoming?.let { lib.dayNames[it.weekday].take(3) }
        val whenText = "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
        val hero = when {
            todaySession != null -> {
                val day = week[todaySession.index]
                HeroUi(
                    whenText, planner.dayName(day.label),
                    "${Copy.count(day.items.size, "exercise")}, about ${planner.estimateMinutes(day, state)} min",
                    counted, done, canStart = day.items.isNotEmpty(), kind = HeroKind.TODAY, nextTitle = nextTitle, nextDay = nextDay,
                )
            }
            doneToday != null -> HeroUi(
                whenText, "Workout done", planner.dayName(week[doneToday.index].label),
                counted, done, canStart = false, kind = HeroKind.DONE_TODAY, nextTitle = nextTitle, nextDay = nextDay,
            )
            else -> HeroUi(
                whenText, "Rest day",
                if (nextTitle != null) "Next: $nextDay, $nextTitle" else "No more workouts this week",
                counted, done, canStart = false, kind = HeroKind.REST, nextTitle = nextTitle, nextDay = nextDay,
            )
        }

        val days = lib.dayNames.mapIndexed { di, dayName ->
            val abbr = dayName.take(3)
            val j = weekdays.indexOf(di)
            if (j < 0) return@mapIndexed WeekdayUi.Rest(abbr)
            val slot = schedule[j]
            // A day before the plan began was never due, so it looks like a rest day.
            if (slot.status == SessionStatus.BEFORE_PLAN) return@mapIndexed WeekdayUi.Rest(abbr)
            val day = week[j]
            val status = when (slot.status) {
                SessionStatus.DONE -> DayStatus.DONE
                SessionStatus.MADE_UP -> DayStatus.MADE_UP
                SessionStatus.SKIPPED -> DayStatus.SKIPPED
                SessionStatus.TODAY -> DayStatus.TODAY
                else -> DayStatus.UPCOMING
            }
            val history = when (status) {
                DayStatus.DONE -> workouts.lastOrNull { it.madeUpForEpochDay == null && dateOf(it) == slot.date }
                DayStatus.MADE_UP -> madeUp[slot.date]
                else -> null
            }
            WeekdayUi.Training(
                abbr = abbr,
                index = j,
                date = slot.date,
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
                workoutId = history?.id,
                madeUpOn = if (status == DayStatus.MADE_UP) madeUp[slot.date]?.let { lib.dayNames[dateOf(it).dayOfWeek.value - 1].take(3) } else null,
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
