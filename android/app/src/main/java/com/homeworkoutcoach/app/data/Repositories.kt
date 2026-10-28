package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.ExerciseState
import kotlinx.coroutines.flow.Flow

/** Settings, stored with DataStore. */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)

    /** Back to defaults (wipe data). */
    suspend fun reset()
}

/** Per-exercise plan state, swaps and workout history, stored with Room. */
interface TrainingRepository {
    fun exerciseStates(): Flow<Map<String, ExerciseState>>

    suspend fun putExerciseStates(states: Map<String, ExerciseState>)

    /** Forget all exercise states, so starting weights are worked out again (first plan build). */
    suspend fun clearExerciseStates()

    /** baseId -> chosen exercise id. */
    fun swaps(): Flow<Map<String, String>>

    /** Choose [chosenId] in place of [baseId]. Null, or the base itself, removes the swap. */
    suspend fun setSwap(baseId: String, chosenId: String?)

    /** Oldest first. */
    fun workouts(): Flow<List<Workout>>

    /** Save a finished workout and the next-session states together, so one is never saved without the other. */
    suspend fun saveWorkout(workout: Workout, nextStates: Map<String, ExerciseState>): Long

    /** Replace a saved workout (found by its id) with a corrected one, and save the states that follow from it together. */
    suspend fun updateWorkout(workout: Workout, nextStates: Map<String, ExerciseState>)

    /** Delete states, swaps and history. */
    suspend fun wipe()
}
