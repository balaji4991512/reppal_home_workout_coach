package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.PlannedExercise
import com.homeworkoutcoach.engine.Task
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
    /** 1: workouts and weigh-ins. 2: habits added. 3: planner tasks added (older files still restore). */
    const val FORMAT = 3

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
    )

    /** [type] is "daily", "weekdays" (with [days], e.g. "MONDAY") or "times" (with [times] a week). */
    @Serializable
    data class ScheduleDto(val type: String, val days: List<String> = emptyList(), val times: Int = 0)

    /** [date] is a calendar day (2026-10-27); [time] is 24-hour "18:30" or null. */
    @Serializable
    data class TaskDto(
        val id: String,
        val title: String,
        val date: String,
        val time: String? = null,
        val note: String = "",
        val done: Boolean = false,
        val createdAt: Long = 0,
    )

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
                )
            },
            tasks = tasks.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id })).map {
                TaskDto(it.id, it.title, it.date.toString(), it.time?.toString(), it.note, it.done, it.createdAt)
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
                    exportedAt = millis(file.exportedAt),
                ),
            )
        } catch (e: Exception) {
            ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        }
    }

    /** Throws if anything in the task is invalid; [read] then reports that nothing was changed. */
    private fun task(d: DataExport.TaskDto) = Task(
        id = d.id,
        title = d.title,
        date = LocalDate.parse(d.date),
        time = d.time?.let(LocalTime::parse),
        note = d.note,
        done = d.done,
        createdAt = d.createdAt,
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
        // Keep how the user is signed in on this phone; everything else comes from the file.
        settings.update { now -> r.settings.copy(guest = now.guest) }
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
) {
    data class Result(val json: String, val workouts: Int, val weighIns: Int, val habits: Int = 0, val tasks: Int = 0)

    suspend fun export(appName: String, nowMillis: Long = System.currentTimeMillis()): Result {
        val workouts = training.workouts().first()
        val weights = body.weights().first()
        val habitData = habits?.let { repo ->
            val entries = repo.entries().first()
            repo.habits().first().map { HabitData(it, entries[it.habit.id].orEmpty()) }
        }.orEmpty()
        val taskList = tasks?.tasks()?.first().orEmpty()
        val text = DataExport.build(
            lib, appName, settings.settings.first(), training.exerciseStates().first(), training.swaps().first(),
            workouts, weights, auth.account.value, nowMillis, zone, habitData, taskList,
        )
        return Result(text, workouts.size, weights.size, habitData.size, taskList.size)
    }
}
