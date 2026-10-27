package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.TaskRepository
import com.homeworkoutcoach.engine.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

class RoomTaskRepository(private val db: TrainingDatabase) : TaskRepository {
    private val dao get() = db.tasks()

    override fun tasks(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map(::toTask) }

    override suspend fun save(task: Task) = dao.upsert(toEntity(task))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun wipe() = dao.clear()

    private fun toEntity(t: Task) = TaskEntity(
        id = t.id,
        title = t.title,
        epochDay = t.date.toEpochDay(),
        minuteOfDay = t.time?.let { it.hour * 60 + it.minute },
        note = t.note,
        done = t.done,
        createdAt = t.createdAt,
    )

    private fun toTask(e: TaskEntity) = Task(
        id = e.id,
        title = e.title,
        date = LocalDate.ofEpochDay(e.epochDay),
        time = e.minuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
        note = e.note,
        done = e.done,
        createdAt = e.createdAt,
    )
}
