package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.WeightSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBodyRepository(private val db: TrainingDatabase) : BodyRepository {

    override fun weights(): Flow<List<BodyWeight>> = db.bodyWeights().observeAll().map { rows ->
        rows.map { BodyWeight(it.id, it.dateMillis, it.weightKg, if (it.source == SCALE) WeightSource.SCALE else WeightSource.MANUAL) }
    }

    override suspend fun add(entry: BodyWeight): Long = db.bodyWeights().insert(
        BodyWeightEntity(
            dateMillis = entry.dateMillis,
            weightKg = entry.weightKg,
            source = if (entry.source == WeightSource.SCALE) SCALE else MANUAL,
        ),
    )

    override suspend fun delete(id: Long) = db.bodyWeights().delete(id)

    override suspend fun wipe() = db.bodyWeights().clear()

    private companion object {
        // Stored values; keep them stable.
        const val SCALE = "scale"
        const val MANUAL = "manual"
    }
}
