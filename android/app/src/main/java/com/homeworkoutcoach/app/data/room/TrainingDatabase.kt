package com.homeworkoutcoach.app.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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

/**
 * A habit. Stored values (kind, scheduleType, auto) are keys; keep them stable. [scheduleValue] is the weekday bit
 * mask (bit 0 = Monday) for "weekdays" and the count for "times", and 0 for "daily". [startEpochDay] is a local
 * calendar day, not a timestamp.
 */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val target: Int,
    val scheduleType: String,
    val scheduleValue: Int,
    val startEpochDay: Long,
    val auto: String,
    val archived: Boolean,
    val icon: String,
    val color: String,
    val position: Int,
)

/** What was entered for a habit on a local calendar day. Deleting the habit deletes its entries. */
@Entity(
    tableName = "habit_entry",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
)
data class HabitEntryEntity(
    val habitId: String,
    val epochDay: Long,
    val amount: Int,
)

/** A planner task. [epochDay] is a local calendar day and [minuteOfDay] the time (null for none), never timestamps. */
@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int?,
    val note: String,
    val done: Boolean,
    val createdAt: Long,
)

/** A routine. [part] is a stored key ("morning", "afternoon", "evening"); keep them stable. */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val part: String,
    val position: Int,
)

/** A step of a routine. Deleting the routine deletes its steps; deleting the linked habit clears the link. */
@Entity(
    tableName = "routine_item",
    primaryKeys = ["routineId", "id"],
    foreignKeys = [
        ForeignKey(RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("habitId")],
)
data class RoutineItemEntity(
    val routineId: String,
    val id: String,
    val title: String,
    val habitId: String?,
    val position: Int,
)

/** A routine finished on a local calendar day. */
@Entity(
    tableName = "routine_done",
    primaryKeys = ["routineId", "epochDay"],
    foreignKeys = [ForeignKey(RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
)
data class RoutineDoneEntity(
    val routineId: String,
    val epochDay: Long,
)

/** A journal entry for a local calendar day. */
@Entity(tableName = "journal_entry")
data class JournalEntity(
    @PrimaryKey val epochDay: Long,
    val win: String,
    val improve: String,
)

/**
 * A goal. [kind] is a stored key ("workouts", "habit", "weight"); keep them stable. [habitId] is not a foreign key:
 * a goal keeps its record if its habit is deleted.
 */
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val target: Double,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val habitId: String?,
    val startWeightKg: Double?,
)

/** A finished focus session. [id] is made from the start time, so the same session saved twice is one row. */
@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val startMillis: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val completed: Boolean,
    val taskTitle: String?,
)

/** A weekly reflection. [weekStartEpochDay] is the Monday of the week, a local calendar day. */
@Entity(tableName = "weekly_reflection")
data class WeeklyReflectionEntity(
    @PrimaryKey val weekStartEpochDay: Long,
    val wentWell: String,
    val differently: String,
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

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit ORDER BY position, name, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_entry")
    fun observeEntries(): Flow<List<HabitEntryEntity>>

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsertEntry(entry: HabitEntryEntity)

    @Query("DELETE FROM habit_entry WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteEntry(habitId: String, epochDay: Long)

    @Query("DELETE FROM habit_entry")
    suspend fun clearEntries()

    @Query("DELETE FROM habit")
    suspend fun clearHabits()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task ORDER BY epochDay, createdAt, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM task")
    suspend fun clear()
}

@Dao
abstract class RoutineDao {
    @Query("SELECT * FROM routine ORDER BY position, name, id")
    abstract fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_item ORDER BY routineId, position")
    abstract fun observeItems(): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_done")
    abstract fun observeDone(): Flow<List<RoutineDoneEntity>>

    @Upsert
    abstract suspend fun upsertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routine_item WHERE routineId = :routineId")
    abstract suspend fun deleteItems(routineId: String)

    @Insert
    abstract suspend fun insertItems(items: List<RoutineItemEntity>)

    /** Replaces a routine and all its steps together. The routine row is updated in place, so finished days stay. */
    @Transaction
    open suspend fun replace(routine: RoutineEntity, items: List<RoutineItemEntity>) {
        upsertRoutine(routine)
        deleteItems(routine.id)
        insertItems(items)
    }

    @Query("DELETE FROM routine WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Upsert
    abstract suspend fun upsertDone(done: RoutineDoneEntity)

    @Query("DELETE FROM routine_done WHERE routineId = :routineId AND epochDay = :epochDay")
    abstract suspend fun deleteDone(routineId: String, epochDay: Long)

    @Query("DELETE FROM routine_done")
    abstract suspend fun clearDone()

    @Query("DELETE FROM routine_item")
    abstract suspend fun clearItems()

    @Query("DELETE FROM routine")
    abstract suspend fun clearRoutines()
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_session ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Upsert
    suspend fun upsert(session: FocusSessionEntity)

    @Query("DELETE FROM focus_session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM focus_session")
    suspend fun clear()
}

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM weekly_reflection ORDER BY weekStartEpochDay DESC")
    fun observeAll(): Flow<List<WeeklyReflectionEntity>>

    @Upsert
    suspend fun upsert(reflection: WeeklyReflectionEntity)

    @Query("DELETE FROM weekly_reflection WHERE weekStartEpochDay = :weekStartEpochDay")
    suspend fun delete(weekStartEpochDay: Long)

    @Query("DELETE FROM weekly_reflection")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal ORDER BY endEpochDay, startEpochDay, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("DELETE FROM goal WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goal")
    suspend fun clear()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entry ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<JournalEntity>>

    @Upsert
    suspend fun upsert(entry: JournalEntity)

    @Query("DELETE FROM journal_entry WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("DELETE FROM journal_entry")
    suspend fun clear()
}

@Database(
    entities = [
        ExerciseStateEntity::class, SwapEntity::class, WorkoutEntity::class, BodyWeightEntity::class,
        HabitEntity::class, HabitEntryEntity::class, TaskEntity::class,
        RoutineEntity::class, RoutineItemEntity::class, RoutineDoneEntity::class, JournalEntity::class, GoalEntity::class, FocusSessionEntity::class,
        WeeklyReflectionEntity::class,
    ],
    version = 9,
    exportSchema = true,
    // 2: body_weight table added. 3: habit and habit_entry tables added. 4: task table added.
    // 5: routine, routine_item and routine_done tables added. 6: journal_entry table added. 7: goal table added. 8: focus_session table added. 9: weekly_reflection table added.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ],
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun exerciseStates(): ExerciseStateDao
    abstract fun swaps(): SwapDao
    abstract fun workouts(): WorkoutDao
    abstract fun bodyWeights(): BodyWeightDao
    abstract fun habits(): HabitDao
    abstract fun tasks(): TaskDao
    abstract fun routines(): RoutineDao
    abstract fun journal(): JournalDao
    abstract fun goals(): GoalDao
    abstract fun focus(): FocusDao
    abstract fun reflections(): ReflectionDao

    companion object {
        fun create(context: Context): TrainingDatabase =
            Room.databaseBuilder(context, TrainingDatabase::class.java, "training.db").build()
    }
}
