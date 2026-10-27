package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.RoutineRepository
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.RoutineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomRoutineRepository(private val db: TrainingDatabase) : RoutineRepository {
    private val dao get() = db.routines()

    override fun routines(): Flow<List<Routine>> = combine(dao.observeRoutines(), dao.observeItems()) { routines, items ->
        val byRoutine = items.groupBy { it.routineId }
        routines.map { r ->
            Routine(
                id = r.id,
                name = r.name,
                part = DayPart.entries.firstOrNull { it.name.lowercase() == r.part } ?: DayPart.MORNING,
                items = byRoutine[r.id].orEmpty().sortedBy { it.position }.map { RoutineItem(it.id, it.title, it.habitId) },
                position = r.position,
            )
        }
    }

    override fun done(): Flow<Set<RoutineDone>> =
        dao.observeDone().map { rows -> rows.map { RoutineDone(it.routineId, LocalDate.ofEpochDay(it.epochDay)) }.toSet() }

    override suspend fun save(routine: Routine) = dao.replace(
        RoutineEntity(routine.id, routine.name, routine.part.name.lowercase(), routine.position),
        routine.items.mapIndexed { i, it -> RoutineItemEntity(routine.id, it.id, it.title, it.habitId, i) },
    )

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun setDone(routineId: String, date: LocalDate, done: Boolean) {
        if (done) dao.upsertDone(RoutineDoneEntity(routineId, date.toEpochDay())) else dao.deleteDone(routineId, date.toEpochDay())
    }

    override suspend fun wipe() {
        dao.clearDone()
        dao.clearItems()
        dao.clearRoutines()
    }
}
