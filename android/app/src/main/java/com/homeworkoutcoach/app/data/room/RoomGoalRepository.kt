package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.GoalRepository
import com.homeworkoutcoach.engine.Goal
import com.homeworkoutcoach.engine.GoalKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomGoalRepository(private val db: TrainingDatabase) : GoalRepository {
    private val dao get() = db.goals()

    override fun goals(): Flow<List<Goal>> = dao.observeAll().map { rows -> rows.map(::toGoal) }

    override suspend fun save(goal: Goal) = dao.upsert(
        GoalEntity(
            id = goal.id,
            title = goal.title,
            kind = goal.kind.name.lowercase(),
            target = goal.target,
            startEpochDay = goal.startDate.toEpochDay(),
            endEpochDay = goal.endDate.toEpochDay(),
            habitId = goal.habitId,
            startWeightKg = goal.startWeightKg,
        ),
    )

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun wipe() = dao.clear()

    private fun toGoal(e: GoalEntity) = Goal(
        id = e.id,
        title = e.title,
        kind = GoalKind.entries.firstOrNull { it.name.lowercase() == e.kind } ?: GoalKind.WORKOUTS,
        target = e.target,
        startDate = LocalDate.ofEpochDay(e.startEpochDay),
        endDate = LocalDate.ofEpochDay(e.endEpochDay),
        habitId = e.habitId,
        startWeightKg = e.startWeightKg,
    )
}
