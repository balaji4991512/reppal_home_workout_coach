package com.homeworkoutcoach.app.data

import kotlinx.coroutines.flow.Flow

enum class WeightSource { SCALE, MANUAL }

data class BodyWeight(
    val id: Long = 0,
    val dateMillis: Long,
    val weightKg: Double,
    val source: WeightSource,
)

/** Weigh-ins, stored with Room next to workouts. */
interface BodyRepository {
    /** Oldest first. */
    fun weights(): Flow<List<BodyWeight>>

    suspend fun add(entry: BodyWeight): Long

    suspend fun delete(id: Long)

    suspend fun wipe()
}
