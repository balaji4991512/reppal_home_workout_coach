package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.ExerciseState
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
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
