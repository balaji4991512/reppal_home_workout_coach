package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.app.data.room.WorkoutEntity
import com.homeworkoutcoach.app.data.room.WorkoutJson
import com.homeworkoutcoach.engine.Effort
import com.homeworkoutcoach.engine.LoggedSet
import com.homeworkoutcoach.engine.PlannedExercise
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the stored JSON format. If this fails, saved workouts on users' phones would no longer load. */
class WorkoutJsonTest {
    private val entity = WorkoutEntity(
        id = 7,
        dateMillis = 1_700_000_000_000,
        label = "M:chest,triceps#1",
        planJson = """[{"id":"floor","sets":3,"target":12,"w":8.0}]""",
        setsJson = """[{"id":"floor","reps":12,"w":8.0,"target":12,"effort":"EASY"},""" +
            """{"id":"floor","reps":10,"w":8.0,"target":12,"effort":"HARD","comment":"elbow"}]""",
        painIdsJson = """["floor"]""",
    )

    private val workout = Workout(
        id = 7,
        dateMillis = 1_700_000_000_000,
        label = "M:chest,triceps#1",
        plan = listOf(PlannedExercise("floor", 3, 12, 8.0)),
        sets = mapOf("floor" to listOf(LoggedSet(12, 8.0, 12, Effort.EASY), LoggedSet(10, 8.0, 12, Effort.HARD, "elbow"))),
        painIds = setOf("floor"),
    )

    @Test
    fun readsTheStoredFormat() = assertEquals(workout, WorkoutJson.fromEntity(entity))

    @Test
    fun writesAndReadsBack() = assertEquals(workout, WorkoutJson.fromEntity(WorkoutJson.toEntity(workout)))
}
