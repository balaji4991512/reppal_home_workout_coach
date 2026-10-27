package com.homeworkoutcoach.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.homeworkoutcoach.app.data.room.RoomBodyRepository
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

/** Phones with version 1 data (workouts, no weigh-ins) must keep it after updating. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Build a version 1 database file exactly as Room 1 created it, from the exported schema. */
    private fun createVersion1(file: File) {
        val schema = Json.parseToJsonElement(
            File("schemas/com.homeworkoutcoach.app.data.room.TrainingDatabase/1.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema.getValue("entities").jsonArray.forEach { e ->
            val o = e.jsonObject
            db.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", o.getValue("tableName").jsonPrimitive.content))
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL(
            "INSERT INTO workout (dateMillis, label, planJson, setsJson, painIdsJson) VALUES " +
                "(42, 'A', '[{\"id\":\"bench\",\"sets\":3,\"target\":12,\"w\":5.0}]', " +
                "'[{\"id\":\"bench\",\"reps\":12,\"w\":5.0,\"target\":12}]', '[]')",
        )
        db.execSQL("INSERT INTO exercise_state VALUES ('bench', 6.0, 11, 3)")
        db.version = 1
        db.close()
    }

    @Test
    fun version1DataSurvivesAndWeighInsWork() = runTest {
        val file = tmp.newFile("training.db").also { it.delete() }
        createVersion1(file)

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), TrainingDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
        val training = RoomTrainingRepository(db)
        val body = RoomBodyRepository(db)

        val w = training.workouts().first().single()
        assertEquals("A", w.label)
        assertEquals(12, w.sets.getValue("bench").single().reps)
        assertEquals(6.0, training.exerciseStates().first().getValue("bench").weightKg, 0.0)

        body.add(BodyWeight(dateMillis = 1, weightKg = 83.5, source = WeightSource.SCALE))
        assertEquals(83.5, body.weights().first().single().weightKg, 0.0)
        assertEquals(2, db.openHelper.readableDatabase.version)
        db.close()
    }
}
