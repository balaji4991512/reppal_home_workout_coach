package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.Goal
import kotlinx.coroutines.flow.Flow

/** Goals, kept with Room. All on the phone. */
interface GoalRepository {
    /** Every goal, the one ending soonest first. */
    fun goals(): Flow<List<Goal>>

    /** Adds the goal, or replaces it if the id exists. */
    suspend fun save(goal: Goal)

    suspend fun delete(id: String)

    suspend fun wipe()
}
