package com.homeworkoutcoach.app.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercise_state")
data class ExerciseStateEntity(
    @PrimaryKey val exerciseId: String,
    val weightKg: Double,
    val targetReps: Int,
    val sets: Int,
)

@Entity(tableName = "swap")
data class SwapEntity(
    @PrimaryKey val baseId: String,
    val chosenId: String,
)

/** Plan and sets are stored as JSON (see [WorkoutJson]); they are always read and written whole. */
@Entity(tableName = "workout")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val label: String,
    val planJson: String,
    val setsJson: String,
    val painIdsJson: String,
)

/** One weigh-in. [source] is "scale" or "manual". */
@Entity(tableName = "body_weight")
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val weightKg: Double,
    val source: String,
)

@Dao
interface ExerciseStateDao {
    @Query("SELECT * FROM exercise_state")
    fun observeAll(): Flow<List<ExerciseStateEntity>>

    @Upsert
    suspend fun upsert(states: List<ExerciseStateEntity>)

    @Query("DELETE FROM exercise_state")
    suspend fun clear()
}

@Dao
interface SwapDao {
    @Query("SELECT * FROM swap")
    fun observeAll(): Flow<List<SwapEntity>>

    @Upsert
    suspend fun upsert(swap: SwapEntity)

    @Query("DELETE FROM swap WHERE baseId = :baseId")
    suspend fun delete(baseId: String)

    @Query("DELETE FROM swap")
    suspend fun clear()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout ORDER BY dateMillis, id")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("DELETE FROM workout")
    suspend fun clear()
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight ORDER BY dateMillis, id")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Insert
    suspend fun insert(entry: BodyWeightEntity): Long

    @Query("DELETE FROM body_weight WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM body_weight")
    suspend fun clear()
}

@Database(
    entities = [ExerciseStateEntity::class, SwapEntity::class, WorkoutEntity::class, BodyWeightEntity::class],
    version = 2,
    exportSchema = true,
    // 2: body_weight table added.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun exerciseStates(): ExerciseStateDao
    abstract fun swaps(): SwapDao
    abstract fun workouts(): WorkoutDao
    abstract fun bodyWeights(): BodyWeightDao

    companion object {
        fun create(context: Context): TrainingDatabase =
            Room.databaseBuilder(context, TrainingDatabase::class.java, "training.db").build()
    }
}
