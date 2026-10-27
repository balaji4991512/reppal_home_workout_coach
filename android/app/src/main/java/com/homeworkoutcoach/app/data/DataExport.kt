package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.PlannedExercise
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the app stores, as one readable JSON file the user keeps (History and profile, "Export my data").
 * Field names are the file format: add fields freely, rename only with a new [FORMAT].
 */
object DataExport {
    const val FORMAT = 1

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
                    exportedAt = millis(file.exportedAt),
                ),
            )
        } catch (e: Exception) {
            ImportCheck.Bad("Part of this file couldn’t be read, so nothing was changed.")
        }
    }
}

/** Replaces everything on the phone with a [Restore]. Workouts stay on the phone; the account is untouched. */
class DataImporter(
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val body: BodyRepository,
) {
    suspend fun restore(r: Restore) {
        training.wipe()
        body.wipe()
        r.workouts.sortedBy { it.dateMillis }.forEach { training.saveWorkout(it, emptyMap()) }
        training.putExerciseStates(r.states)
        r.swaps.forEach { (base, chosen) -> training.setSwap(base, chosen) }
        r.weights.sortedBy { it.dateMillis }.forEach { body.add(it) }
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
) {
    data class Result(val json: String, val workouts: Int, val weighIns: Int)

    suspend fun export(appName: String, nowMillis: Long = System.currentTimeMillis()): Result {
        val workouts = training.workouts().first()
        val weights = body.weights().first()
        val text = DataExport.build(
            lib, appName, settings.settings.first(), training.exerciseStates().first(), training.swaps().first(),
            workouts, weights, auth.account.value, nowMillis, zone,
        )
        return Result(text, workouts.size, weights.size)
    }
}
