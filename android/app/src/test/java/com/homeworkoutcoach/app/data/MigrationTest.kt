package com.homeworkoutcoach.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.room.RoomBodyRepository
import com.homeworkoutcoach.app.data.room.RoomFocusRepository
import com.homeworkoutcoach.app.data.room.RoomGoalRepository
import com.homeworkoutcoach.app.data.room.RoomHabitRepository
import com.homeworkoutcoach.app.data.room.RoomJournalRepository
import com.homeworkoutcoach.app.data.room.RoomRoutineRepository
import com.homeworkoutcoach.app.data.room.RoomTaskRepository
import com.homeworkoutcoach.app.data.room.RoomTrainingRepository
import com.homeworkoutcoach.app.data.room.TrainingDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Phones with older data (version 1: workouts only; version 2: plus weigh-ins) must keep it after updating. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Build a database file exactly as Room created that [version], from the exported schema. */
    private fun create(file: File, version: Int, insertRows: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(
            File("schemas/com.homeworkoutcoach.app.data.room.TrainingDatabase/$version.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema.getValue("entities").jsonArray.forEach { e ->
            val o = e.jsonObject
            db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", o.getValue("tableName").jsonPrimitive.content))
            // Indices were added to the schema in version 5; older schema files have none.
            o["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", o.getValue("tableName").jsonPrimitive.content))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        insertRows(db)
        db.version = version
        db.close()
    }

    private fun insertWorkoutAndState(db: SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO workout (dateMillis, label, planJson, setsJson, painIdsJson) VALUES " +
                "(42, 'A', '[{\"id\":\"bench\",\"sets\":3,\"target\":12,\"w\":5.0}]', " +
                "'[{\"id\":\"bench\",\"reps\":12,\"w\":5.0,\"target\":12}]', '[]')",
        )
        db.execSQL("INSERT INTO exercise_state VALUES ('bench', 6.0, 11, 3)")
    }

    private fun open(file: File) = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java, file.absolutePath)
        .allowMainThreadQueries()
        .build()

    @Test
    fun version1DataSurvivesAndWeighInsWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 1, ::insertWorkoutAndState)

        val db = open(file)
        val training = RoomTrainingRepository(db)
        val body = RoomBodyRepository(db)

        val w = training.workouts().first().single()
        assertEquals("A", w.label)
        assertEquals(12, w.sets.getValue("bench").single().reps)
        assertEquals(6.0, training.exerciseStates().first().getValue("bench").weightKg, 0.0)

        body.add(BodyWeight(dateMillis = 1, weightKg = 83.5, source = WeightSource.SCALE))
        assertEquals(83.5, body.weights().first().single().weightKg, 0.0)
        // version 1 goes all the way to the current version, so habits and tasks work too
        RoomHabitRepository(db).save(storedHabit("water"))
        assertEquals(1, RoomHabitRepository(db).habits().first().size)
        RoomTaskRepository(db).save(task("t"))
        assertEquals(1, RoomTaskRepository(db).tasks().first().size)
        RoomRoutineRepository(db).save(routine("r"))
        assertEquals(1, RoomRoutineRepository(db).routines().first().size)
        RoomJournalRepository(db).save(com.homeworkoutcoach.engine.JournalEntry(java.time.LocalDate.of(2026, 10, 28), "won"))
        assertEquals(1, RoomJournalRepository(db).entries().first().size)
        RoomGoalRepository(db).save(goal("g"))
        assertEquals(1, RoomGoalRepository(db).goals().first().size)
        RoomFocusRepository(db).save(focusSession("f"))
        assertEquals(1, RoomFocusRepository(db).sessions().first().size)
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version7DataSurvivesAndFocusSessionsWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 7) { db ->
            insertWorkoutAndState(db)
            db.execSQL(
                "INSERT INTO goal (id, title, kind, target, startEpochDay, endEpochDay, habitId, startWeightKg) " +
                    "VALUES ('g', 'Twelve workouts', 'workouts', 12.0, 20000, 20029, NULL, NULL)",
            )
        }

        val db = open(file)
        assertEquals("Twelve workouts", RoomGoalRepository(db).goals().first().single().title)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val focus = RoomFocusRepository(db)
        assertEquals(emptyList<com.homeworkoutcoach.engine.FocusSession>(), focus.sessions().first())
        focus.save(focusSession("f"))
        assertEquals(listOf("f"), focus.sessions().first().map { it.id })
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version6DataSurvivesAndGoalsWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 6) { db ->
            insertWorkoutAndState(db)
            db.execSQL("INSERT INTO journal_entry (epochDay, win, improve) VALUES (20001, 'a win', 'do better')")
        }

        val db = open(file)
        assertEquals("a win", RoomJournalRepository(db).entries().first().single().win)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val goals = RoomGoalRepository(db)
        assertEquals(emptyList<com.homeworkoutcoach.engine.Goal>(), goals.goals().first())
        goals.save(goal("g"))
        assertEquals(listOf("g"), goals.goals().first().map { it.id })
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version5DataSurvivesAndTheJournalWorks() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 5) { db ->
            insertWorkoutAndState(db)
            db.execSQL("INSERT INTO routine (id, name, part, position) VALUES ('r', 'Morning', 'morning', 0)")
            db.execSQL("INSERT INTO routine_item (routineId, id, title, habitId, position) VALUES ('r', 's', 'Stretch', NULL, 0)")
            db.execSQL("INSERT INTO routine_done (routineId, epochDay) VALUES ('r', 20001)")
        }

        val db = open(file)
        val routines = RoomRoutineRepository(db)
        assertEquals("Stretch", routines.routines().first().single().items.single().title)
        assertEquals(1, routines.done().first().size)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val journal = RoomJournalRepository(db)
        assertEquals(emptyList<com.homeworkoutcoach.engine.JournalEntry>(), journal.entries().first())
        journal.save(com.homeworkoutcoach.engine.JournalEntry(java.time.LocalDate.of(2026, 10, 28), "a win", "do better"))
        assertEquals("a win", journal.entries().first().single().win)
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version4DataSurvivesAndRoutinesWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 4) { db ->
            insertWorkoutAndState(db)
            db.execSQL(
                "INSERT INTO habit (id, name, kind, target, scheduleType, scheduleValue, startEpochDay, auto, archived, icon, color, position) " +
                    "VALUES ('h', 'Water', 'count', 8, 'daily', 0, 20000, 'none', 0, 'drop', 'blue', 0)",
            )
            db.execSQL("INSERT INTO task (id, title, epochDay, minuteOfDay, note, done, createdAt) VALUES ('t', 'Milk', 20001, 540, '', 0, 5)")
        }

        val db = open(file)
        assertEquals("Water", RoomHabitRepository(db).habits().first().single().habit.name)
        assertEquals(java.time.LocalTime.of(9, 0), RoomTaskRepository(db).tasks().first().single().time)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val routines = RoomRoutineRepository(db)
        assertEquals(emptyList<com.homeworkoutcoach.engine.Routine>(), routines.routines().first())
        routines.save(routine("r", linkedTo = "h"))
        assertEquals("h", routines.routines().first().single().items.first().habitId)
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version3DataSurvivesAndTasksWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 3) { db ->
            insertWorkoutAndState(db)
            db.execSQL("INSERT INTO body_weight (dateMillis, weightKg, source) VALUES (7, 84.5, 'scale')")
            db.execSQL(
                "INSERT INTO habit (id, name, kind, target, scheduleType, scheduleValue, startEpochDay, auto, archived, icon, color, position) " +
                    "VALUES ('h', 'Water', 'count', 8, 'daily', 0, 20000, 'none', 0, 'drop', 'blue', 0)",
            )
            db.execSQL("INSERT INTO habit_entry (habitId, epochDay, amount) VALUES ('h', 20001, 5)")
        }

        val db = open(file)
        assertEquals("Water", RoomHabitRepository(db).habits().first().single().habit.name)
        assertEquals(5, RoomHabitRepository(db).entries().first().getValue("h").values.single())
        assertEquals(84.5, RoomBodyRepository(db).weights().first().single().weightKg, 0.0)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val tasks = RoomTaskRepository(db)
        assertEquals(emptyList<com.homeworkoutcoach.engine.Task>(), tasks.tasks().first())
        tasks.save(task("t"))
        assertEquals(listOf("t"), tasks.tasks().first().map { it.id })
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }

    @Test
    fun version2DataSurvivesAndHabitsWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        create(file, 2) { db ->
            insertWorkoutAndState(db)
            db.execSQL("INSERT INTO body_weight (dateMillis, weightKg, source) VALUES (7, 84.5, 'scale')")
        }

        val db = open(file)
        assertEquals(84.5, RoomBodyRepository(db).weights().first().single().weightKg, 0.0)
        assertEquals("A", RoomTrainingRepository(db).workouts().first().single().label)

        val habits = RoomHabitRepository(db)
        assertEquals(emptyList<StoredHabit>(), habits.habits().first())
        habits.save(storedHabit("water"))
        habits.setAmount("water", java.time.LocalDate.of(2026, 10, 27), 1)
        assertEquals(1, habits.entries().first().getValue("water").size)
        assertEquals(8, db.openHelper.readableDatabase.version)
        db.close()
    }
}
