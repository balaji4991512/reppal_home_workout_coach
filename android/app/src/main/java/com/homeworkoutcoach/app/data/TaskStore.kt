package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.Task
import kotlinx.coroutines.flow.Flow

/** Planner tasks, kept with Room next to workouts and habits. All on the phone. */
interface TaskRepository {
    /** Every task, oldest day first. The planner rules sort them for each day. */
    fun tasks(): Flow<List<Task>>

    /** Adds the task, or replaces it if the id exists. */
    suspend fun save(task: Task)

    suspend fun delete(id: String)

    suspend fun wipe()
}
