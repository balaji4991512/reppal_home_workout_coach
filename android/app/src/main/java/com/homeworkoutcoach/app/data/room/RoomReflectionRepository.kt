package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.ReflectionRepository
import com.homeworkoutcoach.engine.WeeklyReflection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomReflectionRepository(private val db: TrainingDatabase) : ReflectionRepository {
    private val dao get() = db.reflections()

    override fun reflections(): Flow<List<WeeklyReflection>> = dao.observeAll().map { rows ->
        rows.map { WeeklyReflection(LocalDate.ofEpochDay(it.weekStartEpochDay), it.wentWell, it.differently) }
    }

    override suspend fun save(reflection: WeeklyReflection) =
        dao.upsert(WeeklyReflectionEntity(reflection.weekStart.toEpochDay(), reflection.wentWell, reflection.differently))

    override suspend fun delete(weekStart: LocalDate) = dao.delete(weekStart.toEpochDay())

    override suspend fun wipe() = dao.clear()
}
