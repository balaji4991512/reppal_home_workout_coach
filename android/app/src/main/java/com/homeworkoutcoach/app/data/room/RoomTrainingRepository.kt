package com.homeworkoutcoach.app.data.room

import androidx.room.withTransaction
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.Workout
import com.homeworkoutcoach.engine.ExerciseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTrainingRepository(private val db: TrainingDatabase) : TrainingRepository {

    override fun exerciseStates(): Flow<Map<String, ExerciseState>> =
        db.exerciseStates().observeAll().map { rows ->
            rows.associate { it.exerciseId to ExerciseState(it.weightKg, it.targetReps, it.sets) }
        }

    override suspend fun putExerciseStates(states: Map<String, ExerciseState>) {
        db.exerciseStates().upsert(states.map { (id, s) -> ExerciseStateEntity(id, s.weightKg, s.targetReps, s.sets) })
    }

    override suspend fun clearExerciseStates() = db.exerciseStates().clear()

    override fun swaps(): Flow<Map<String, String>> =
        db.swaps().observeAll().map { rows -> rows.associate { it.baseId to it.chosenId } }

    override suspend fun setSwap(baseId: String, chosenId: String?) {
        if (chosenId == null || chosenId == baseId) db.swaps().delete(baseId)
        else db.swaps().upsert(SwapEntity(baseId, chosenId))
    }

    override fun workouts(): Flow<List<Workout>> =
        db.workouts().observeAll().map { rows -> rows.map(WorkoutJson::fromEntity) }

    override suspend fun saveWorkout(workout: Workout, nextStates: Map<String, ExerciseState>): Long =
        db.withTransaction {
            val id = db.workouts().insert(WorkoutJson.toEntity(workout))
            putExerciseStates(nextStates)
            id
        }

    override suspend fun wipe() = db.withTransaction {
        db.exerciseStates().clear()
        db.swaps().clear()
        db.workouts().clear()
    }
}
