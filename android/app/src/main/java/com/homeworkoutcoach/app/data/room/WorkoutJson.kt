package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON columns of [WorkoutEntity]. Field names are the stored format: rename only with a migration. */
internal object WorkoutJson {
    @Serializable
    private data class PlannedDto(val id: String, val sets: Int, val target: Int, val w: Double)

    @Serializable
    private data class SetDto(
        val id: String,
        val reps: Int,
        val w: Double,
        val target: Int,
        val effort: Effort = Effort.JUST_RIGHT,
        val comment: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val plans = ListSerializer(PlannedDto.serializer())
    private val sets = ListSerializer(SetDto.serializer())
    private val ids = ListSerializer(String.serializer())

    fun toEntity(w: Workout) = WorkoutEntity(
        id = w.id,
        dateMillis = w.dateMillis,
        label = w.label,
        planJson = json.encodeToString(plans, w.plan.map { PlannedDto(it.exerciseId, it.sets, it.targetReps, it.weightKg) }),
        setsJson = json.encodeToString(sets, w.sets.flatMap { (id, list) ->
            list.map { SetDto(id, it.reps, it.weightKg, it.targetReps, it.effort, it.comment) }
        }),
        painIdsJson = json.encodeToString(ids, w.painIds.toList()),
    )

    fun fromEntity(e: WorkoutEntity) = Workout(
        id = e.id,
        dateMillis = e.dateMillis,
        label = e.label,
        plan = json.decodeFromString(plans, e.planJson).map { PlannedExercise(it.id, it.sets, it.target, it.w) },
        sets = json.decodeFromString(sets, e.setsJson).groupBy(
            keySelector = { it.id },
            valueTransform = { LoggedSet(it.reps, it.w, it.target, it.effort, it.comment) },
        ),
        painIds = json.decodeFromString(ids, e.painIdsJson).toSet(),
    )
}
