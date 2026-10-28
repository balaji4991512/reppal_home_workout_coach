package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFocusRepository(private val db: TrainingDatabase) : FocusRepository {
    private val dao get() = db.focus()

    override fun sessions(): Flow<List<FocusSession>> = dao.observeAll().map { rows ->
        rows.map { FocusSession(it.id, it.startMillis, it.plannedMinutes, it.actualMinutes, it.completed, it.taskTitle) }
    }

    override suspend fun save(session: FocusSession) = dao.upsert(
        FocusSessionEntity(session.id, session.startMillis, session.plannedMinutes, session.actualMinutes, session.completed, session.taskTitle),
    )

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun wipe() = dao.clear()
}
