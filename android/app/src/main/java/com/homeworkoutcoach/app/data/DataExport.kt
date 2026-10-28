package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.FocusSession
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.GoalKind
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.JournalEntry
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineItem
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.WeeklyReflection
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the app stores, as one readable JSON file the user keeps (Profile, "Export my data").
 * Field names are the file format: add fields freely, rename only with a new [FORMAT].
 */
object DataExport {
    /** 1: workouts and weigh-ins. 2: habits. 3: planner tasks. 4: routines. 5: journal. 6: goals. 7: focus sessions. 8: weekly reflections (older files still restore). */
    const val FORMAT = 11

    @Serializable
    data class File(
        val app: String,
        val format: Int,
        val exportedAt: String,
        val account: String?,
        val settings: UserSettings,
        val exerciseStates: Map<String, StateDto>,
        val swaps: Map<String, String>,
        val workouts: List<WorkoutDto>,
        val weighIns: List<WeighInDto>,
        /** Null in files from before habits existed; restoring such a file leaves the phone's habits alone. */
        val habits: List<HabitDto>? = null,
        /** Null in files from before the planner existed; restoring such a file leaves the phone's tasks alone. */
        val tasks: List<TaskDto>? = null,
        /** Null in files from before routines existed; restoring such a file leaves the phone's routines alone. */
        val routines: List<RoutineDto>? = null,
        /** Null in files from before the journal existed; restoring such a file leaves the phone's journal alone. */
        val journal: List<JournalDto>? = null,
        /** Null in files from before goals existed; restoring such a file leaves the phone's goals alone. */
        val goals: List<GoalDto>? = null,
        /** Null in files from before the focus timer existed; restoring such a file leaves the phone's sessions alone. */
        val focus: List<FocusDto>? = null,
        /** Null in files from before weekly reflections existed; restoring such a file leaves the phone's alone. */
        val reflections: List<ReflectionDto>? = null,
    )

    @Serializable
    data class StateDto(val name: String, val weightKg: Double, val targetReps: Int, val sets: Int)

    @Serializable
    data class WorkoutDto(
        val date: String,
        val label: String,
        val day: String,
        val plan: List<PlannedDto>,
        val sets: List<SetDto>,
        val pain: List<String>,
        /** The date (2026-10-26) of the skipped workout this one makes up; absent for a workout done on its own day. */
        val madeUpFor: String? = null,
    )

    @Serializable
    data class PlannedDto(val exercise: String, val name: String, val sets: Int, val targetReps: Int, val weightKg: Double)

    @Serializable
    data class SetDto(
        val exercise: String,
        val reps: Int,
        val weightKg: Double,
        val targetReps: Int,
        val effort: String,
        val comment: String = "",
    )

    @Serializable
    data class WeighInDto(val date: String, val weightKg: Double, val source: String)

    /** [kind], [auto] and the schedule use lower-case keys; [entries] maps a calendar day (2026-10-27) to its amount. */
    @Serializable
    data class HabitDto(
        val id: String,
        val name: String,
        val kind: String,
        val target: Int,
        val schedule: ScheduleDto,
        val startDate: String,
        val auto: String = "none",
        val archived: Boolean = false,
        val icon: String,
        val color: String,
        val entries: Map<String, Int> = emptyMap(),
        /** The reminder time, 24-hour "10:00", or absent for none (format 11 on). */
        val reminder: String? = null,
    )

    /** [type] is "daily", "weekdays" (with [days], e.g. "MONDAY") or "times" (with [times] a week). */
    @Serializable
    data class ScheduleDto(val type: String, val days: List<String> = emptyList(), val times: Int = 0)

    /** [date] is a calendar day (2026-10-27); [time] and [endTime] are 24-hour "18:30" or null (end: format 9 on). */
    @Serializable
    data class TaskDto(
        val id: String,
        val title: String,
        val date: String,
        val time: String? = null,
        val note: String = "",
        val done: Boolean = false,
        val createdAt: Long = 0,
        val endTime: String? = null,
    )

    /** [part] is "morning", "afternoon" or "evening"; [done] are the days it was finished (2026-10-27). */
    @Serializable
    data class RoutineDto(
        val id: String,
        val name: String,
        val part: String,
        val steps: List<StepDto> = emptyList(),
        val done: List<String> = emptyList(),
    )

    /** A step; [habit] is the id of the habit it ticks, if any. */
    @Serializable
    data class StepDto(val id: String, val title: String, val habit: String? = null)

    /** [date] is a calendar day (2026-10-27). */
    @Serializable
    data class JournalDto(val date: String, val win: String = "", val improve: String = "")

    /** [kind] is "workouts", "habit" or "weight"; [habit] is the habit's id; days are 2026-10-27. */
    @Serializable
    data class GoalDto(
        val id: String,
        val title: String,
        val kind: String,
        val target: Double,
        val start: String,
        val end: String,
        val habit: String? = null,
        val startWeightKg: Double? = null,
    )

    /** A finished focus session; [start] is when it began (ISO date and time with the offset). */
    @Serializable
    data class FocusDto(
        val id: String,
        val start: String,
        val plannedMinutes: Int,
        val actualMinutes: Int,
        val completed: Boolean,
        val task: String? = null,
    )

    /** [week] is the Monday of the week (2026-10-26). */
    @Serializable
    data class ReflectionDto(val week: String, val wentWell: String = "", val differently: String = "")

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(
        lib: Library,
        appName: String,
        settings: UserSettings,
        states: Map<String, ExerciseState>,
        swaps: Map<String, String>,
        workouts: List<Workout>,
        weights: List<BodyWeight>,
        account: Account?,
        nowMillis: Long,
        zone: ZoneId,
        habits: List<HabitData> = emptyList(),
        tasks: List<Task> = emptyList(),
        routines: List<RoutineBundle> = emptyList(),
        journal: List<JournalEntry> = emptyList(),
        goals: List<Goal> = emptyList(),
        focus: List<FocusSession> = emptyList(),
        reflections: List<WeeklyReflection> = emptyList(),
    ): String {
        val planner = Planner(lib)
        fun iso(ms: Long) = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(ms).atZone(zone))
        fun name(id: String) = lib.exercises[id]?.name ?: id
        val file = File(
            app = appName,
            format = FORMAT,
            exportedAt = iso(nowMillis),
            account = account?.email,
            settings = settings,
            exerciseStates = states.toSortedMap().mapValues { (id, s) -> StateDto(name(id), s.weightKg, s.targetReps, s.sets) },
            swaps = swaps.toSortedMap(),
            workouts = workouts.sortedBy { it.dateMillis }.map { w ->
                WorkoutDto(
                    date = iso(w.dateMillis),
                    label = w.label,
                    day = planner.dayName(w.label),
                    plan = w.plan.map { PlannedDto(it.exerciseId, name(it.exerciseId), it.sets, it.targetReps, it.weightKg) },
                    sets = w.sets.flatMap { (id, list) ->
                        list.map { SetDto(id, it.reps, it.weightKg, it.targetReps, it.effort.key, it.comment) }
                    },
                    pain = w.painIds.sorted(),
                    madeUpFor = w.madeUpForEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
                )
            },
            weighIns = weights.sortedBy { it.dateMillis }.map {
                WeighInDto(iso(it.dateMillis), it.weightKg, if (it.source == WeightSource.SCALE) "scale" else "manual")
            },
            habits = habits.sortedWith(compareBy({ it.stored.position }, { it.stored.habit.name })).map { d ->
                val h = d.stored.habit
                HabitDto(
                    id = h.id,
                    name = h.name,
                    kind = h.kind.name.lowercase(),
                    target = h.target,
                    schedule = when (val sc = h.schedule) {
                        HabitSchedule.Daily -> ScheduleDto("daily")
                        is HabitSchedule.Weekdays -> ScheduleDto("weekdays", days = sc.days.sorted().map { it.name })
                        is HabitSchedule.TimesPerWeek -> ScheduleDto("times", times = sc.times)
                    },
                    startDate = h.startDate.toString(),
                    auto = h.auto.name.lowercase(),
                    archived = h.archived,
                    icon = d.stored.icon,
                    color = d.stored.color,
                    entries = d.entries.toSortedMap().mapKeys { it.key.toString() },
                    reminder = h.reminder?.toString(),
                )
            },
            tasks = tasks.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map {
                TaskDto(it.id, it.title, it.date.toString(), it.time?.toString(), it.note, it.done, it.createdAt, it.endTime?.toString())
            },
            routines = routines.sortedWith(compareBy({ it.routine.position }, { it.routine.name })).map { b ->
                RoutineDto(
                    id = b.routine.id,
                    name = b.routine.name,
                    part = b.routine.part.name.lowercase(),
                    steps = b.routine.items.map { StepDto(it.id, it.title, it.habitId) },
                    done = b.done.sorted().map { it.toString() },
                )
            },
            journal = journal.sortedBy { it.date }.map { JournalDto(it.date.toString(), it.win, it.improve) },
            reflections = reflections.sortedBy { it.weekStart }.map { ReflectionDto(it.weekStart.toString(), it.wentWell, it.differently) },
            focus = focus.sortedBy { it.startMillis }.map {
                FocusDto(it.id, iso(it.startMillis), it.plannedMinutes, it.actualMinutes, it.completed, it.taskTitle)
            },
            goals = goals.sortedWith(compareBy({ it.endDate }, { it.startDate }, { it.id })).map {
                GoalDto(it.id, it.title, it.kind.name.lowercase(), it.target, it.startDate.toString(), it.endDate.toString(), it.habitId, it.startWeightKg)
            },
        )
        return json.encodeToString(File.serializer(), file)
    }

    /** Suggested file name, for example reppal-export-2026-10-27.json. */
    fun fileName(appName: String, nowMillis: Long, zone: ZoneId): String =
        "${appName.lowercase().filter { it.isLetterOrDigit() }}-export-${Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()}.json"
}

/** Everything read from an export file, ready to restore. */
data class Restore(
    val settings: UserSettings,
    val states: Map<String, ExerciseState>,
    val swaps: Map<String, String>,
    val workouts: List<Workout>,
    val weights: List<BodyWeight>,
    /** Null when the file predates habits: the phone's habits stay as they are. */
    val habits: List<HabitData>?,
    /** Null when the file predates the planner: the phone's tasks stay as they are. */
    val tasks: List<Task>?,
    /** Null when the file predates routines: the phone's routines stay as they are. */
    val routines: List<RoutineBundle>?,
    /** Null when the file predates the journal: the phone's journal stays as it is. */
    val journal: List<JournalEntry>?,
    /** Null when the file predates goals: the phone's goals stay as they are. */
    val goals: List<Goal>?,
    /** Null when the file predates the focus timer: the phone's sessions stay as they are. */
    val focus: List<FocusSession>?,
    /** Null when the file predates weekly reflections: the phone's stay as they are. */
    val reflections: List<WeeklyReflection>?,
    val exportedAt: Long,
)

sealed interface ImportCheck {
    data class Ok(val restore: Restore) : ImportCheck
    data class Bad(val message: String) : ImportCheck
}

/** Reads an export file back [DataExport]. Checks everything before anything is written. */
object DataImport {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(lib: Library, text: String): ImportCheck {
        val file = try {
            json.decodeFromString(DataExport.File.serializer(), text)
        } catch (e: Exception) {
            return ImportCheck.Bad("This isn’t an export file from this app.")
        }
        if (file.format > DataExport.FORMAT) {
            return ImportCheck.Bad("This file comes from a newer version of the app. Update the app, then try again.")
        }
        fun millis(iso: String) = OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        val efforts = Effort.entries.associateBy { it.key }

        return try {
            val ids = file.exerciseStates.keys + file.swaps.keys + file.swaps.values +
                file.workouts.flatMap { w -> w.plan.map { it.exercise } + w.sets.map { it.exercise } + w.pain }
            val unknown = ids.filter { it !in lib.exercises }.toSet()
            if (unknown.isNotEmpty()) return ImportCheck.Bad("This file has exercises this version doesn’t know: ${unknown.joinToString(", ")}.")

            ImportCheck.Ok(
                Restore(
                    settings = file.settings,
                    states = file.exerciseStates.mapValues { ExerciseState(it.value.weightKg, it.value.targetReps, it.value.sets) },
                    swaps = file.swaps,
                    workouts = file.workouts.map { w ->
                        Workout(
                            dateMillis = millis(w.date),
                            label = w.label,
                            plan = w.plan.map { PlannedExercise(it.exercise, it.sets, it.targetReps, it.weightKg) },
                            sets = w.sets.groupBy({ it.exercise }) {
                                LoggedSet(it.reps, it.weightKg, it.targetReps, efforts[it.effort] ?: Effort.JUST_RIGHT, it.comment)
                            },
                            painIds = w.pain.toSet(),
                            madeUpForEpochDay = w.madeUpFor?.let { LocalDate.parse(it).toEpochDay() },
                        )
                    },
                    weights = file.weighIns.map {
                        BodyWeight(
                            dateMillis = millis(it.date),
                            weightKg = it.weightKg,
                            source = if (it.source == "scale") WeightSource.SCALE else WeightSource.MANUAL,
                        )
                    },
                    habits = file.habits?.map(::habitData),
                    tasks = file.tasks?.map(::task),
                    routines = file.routines?.map(::routine),
                    journal = file.journal?.map { JournalEntry(LocalDate.parse(it.date), it.win, it.improve) },
                    reflections = file.reflections?.map { WeeklyReflection(LocalDate.parse(it.week), it.wentWell, it.differently) },
                    focus = file.focus?.map { f -> FocusSession(f.id, millis(f.start), f.plannedMinutes, f.actualMinutes, f.completed, f.task) },
                    goals = file.goals?.map { g ->
                        Goal(
                            id = g.id,
                            title = g.title,
                            kind = GoalKind.entries.first { it.name.lowercase() == g.kind },
                            target = g.target,
                            startDate = LocalDate.parse(g.start),
                            endDate = LocalDate.parse(g.end),
                            habitId = g.habit,
                            startWeightKg = g.startWeightKg,
                        )
                    },
                    exportedAt = millis(file.exportedAt),
                ),
            )
        } catch (e: Exception) {
            ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        }
    }

    /** Throws if anything in the routine is invalid; [read] then reports that nothing was changed. */
    private fun routine(d: DataExport.RoutineDto) = RoutineBundle(
        Routine(
            id = d.id,
            name = d.name,
            part = DayPart.entries.first { it.name.lowercase() == d.part },
            items = d.steps.map { RoutineItem(it.id, it.title, it.habit) },
        ),
        d.done.map(LocalDate::parse).toSet(),
    )

    /** Throws if anything in the task is invalid; [read] then reports that nothing was changed. */
    private fun task(d: DataExport.TaskDto) = Task(
        id = d.id,
        title = d.title,
        date = LocalDate.parse(d.date),
        time = d.time?.let(LocalTime::parse),
        note = d.note,
        done = d.done,
        createdAt = d.createdAt,
        endTime = d.endTime?.let(LocalTime::parse),
    )

    /** Throws if anything in the habit is invalid; [read] then reports that nothing was changed. */
    private fun habitData(d: DataExport.HabitDto): HabitData {
        val schedule = when (d.schedule.type) {
            "daily" -> HabitSchedule.Daily
            "weekdays" -> HabitSchedule.Weekdays(d.schedule.days.map { DayOfWeek.valueOf(it) }.toSet())
            "times" -> HabitSchedule.TimesPerWeek(d.schedule.times)
            else -> error("unknown schedule ${d.schedule.type}")
        }
        val habit = Habit(
            id = d.id,
            name = d.name,
            kind = HabitKind.entries.first { it.name.lowercase() == d.kind },
            target = d.target,
            schedule = schedule,
            startDate = LocalDate.parse(d.startDate),
            auto = AutoSource.entries.firstOrNull { it.name.lowercase() == d.auto } ?: AutoSource.NONE,
            archived = d.archived,
            reminder = d.reminder?.let(java.time.LocalTime::parse),
        )
        return HabitData(
            StoredHabit(habit, d.icon, d.color),
            d.entries.entries.associate { LocalDate.parse(it.key) to it.value.coerceAtLeast(0) },
        )
    }
}

/** Replaces everything on the phone with a [Restore]. Workouts stay on the phone; the account is untouched. */
class DataImporter(
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val body: BodyRepository,
    private val habits: HabitRepository? = null,
    private val tasks: TaskRepository? = null,
    private val routines: RoutineRepository? = null,
    private val journal: JournalRepository? = null,
    private val goals: GoalRepository? = null,
    private val focus: FocusRepository? = null,
    private val reflections: ReflectionRepository? = null,
) {
    suspend fun restore(r: Restore) {
        training.wipe()
        body.wipe()
        r.workouts.sortedBy { it.dateMillis }.forEach { training.saveWorkout(it, emptyMap()) }
        training.putExerciseStates(r.states)
        r.swaps.forEach { (base, chosen) -> training.setSwap(base, chosen) }
        r.weights.sortedBy { it.dateMillis }.forEach { body.add(it) }
        // A file from before habits existed has none to give back, so the habits on the phone are left alone.
        if (r.habits != null && habits != null) {
            habits.wipe()
            r.habits.forEachIndexed { i, d ->
                habits.save(d.stored.copy(position = i))
                d.entries.forEach { (day, amount) -> habits.setAmount(d.stored.habit.id, day, amount) }
            }
        }
        // Likewise for a file from before the planner.
        if (r.tasks != null && tasks != null) {
            tasks.wipe()
            r.tasks.forEach { tasks.save(it) }
        }
        // And for a file from before routines. Steps keep their habit link only if that habit is on the phone now.
        if (r.routines != null && routines != null) {
            routines.wipe()
            val known = habits?.habits()?.first()?.map { it.habit.id }?.toSet().orEmpty()
            r.routines.forEachIndexed { i, b ->
                val steps = b.routine.items.map { if (it.habitId != null && it.habitId !in known) it.copy(habitId = null) else it }
                routines.save(b.routine.copy(items = steps, position = i))
                b.done.forEach { routines.setDone(b.routine.id, it, true) }
            }
        }
        // And for a file from before the journal.
        if (r.journal != null && journal != null) {
            journal.wipe()
            r.journal.forEach { journal.save(it) }
        }
        // And for a file from before goals.
        if (r.goals != null && goals != null) {
            goals.wipe()
            r.goals.forEach { goals.save(it) }
        }
        // And for a file from before the focus timer.
        if (r.focus != null && focus != null) {
            focus.wipe()
            r.focus.forEach { focus.save(it) }
        }
        // And for a file from before weekly reflections.
        if (r.reflections != null && reflections != null) {
            reflections.wipe()
            r.reflections.forEach { reflections.save(it) }
        }
        // Keep how the user is signed in on this phone; everything else comes from the file.
        settings.update { now -> r.settings.copy(guest = now.guest, activeFocus = now.activeFocus) }
    }
}

/** Collects the current data from every store and builds the export. */
class DataExporter(
    private val lib: Library,
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val body: BodyRepository,
    private val auth: AuthRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val habits: HabitRepository? = null,
    private val tasks: TaskRepository? = null,
    private val routines: RoutineRepository? = null,
    private val journal: JournalRepository? = null,
    private val goals: GoalRepository? = null,
    private val focus: FocusRepository? = null,
    private val reflections: ReflectionRepository? = null,
) {
    data class Result(
        val json: String,
        val workouts: Int,
        val weighIns: Int,
        val habits: Int = 0,
        val tasks: Int = 0,
        val routines: Int = 0,
        val journal: Int = 0,
        val goals: Int = 0,
        val focus: Int = 0,
        val reflections: Int = 0,
    )

    suspend fun export(appName: String, nowMillis: Long = System.currentTimeMillis()): Result {
        val workouts = training.workouts().first()
        val weights = body.weights().first()
        val habitData = habits?.let { repo ->
            val entries = repo.entries().first()
            repo.habits().first().map { HabitData(it, entries[it.habit.id].orEmpty()) }
        }.orEmpty()
        val taskList = tasks?.tasks()?.first().orEmpty()
        val routineData = routines?.let { repo ->
            val done = repo.done().first()
            repo.routines().first().map { r -> RoutineBundle(r, done.filter { it.routineId == r.id }.map { it.date }.toSet()) }
        }.orEmpty()
        val journalList = journal?.entries()?.first().orEmpty()
        val goalList = goals?.goals()?.first().orEmpty()
        val focusList = focus?.sessions()?.first().orEmpty()
        val reflectionList = reflections?.reflections()?.first().orEmpty()
        val text = DataExport.build(
            lib, appName, settings.settings.first(), training.exerciseStates().first(), training.swaps().first(),
            workouts, weights, auth.account.value, nowMillis, zone, habitData, taskList, routineData, journalList, goalList, focusList, reflectionList,
        )
        return Result(
            text, workouts.size, weights.size, habitData.size, taskList.size, routineData.size, journalList.size, goalList.size,
            focusList.size, reflectionList.size,
        )
    }
}
