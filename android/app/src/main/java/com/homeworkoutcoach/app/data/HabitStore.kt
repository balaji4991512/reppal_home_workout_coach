package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.DayPart
import com.homeworkoutcoach.engine.HabitLog
import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import com.homeworkoutcoach.engine.Routines
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A habit as kept on the phone: the rules' [habit] plus how it looks. [icon] and [color] are keys the UI maps. */
data class StoredHabit(
    val habit: Habit,
    val icon: String,
    val color: String,
    /** Position in the habits list, lowest first. */
    val position: Int = 0,
)

/** A habit with everything entered for it by hand, one amount per calendar day (0 means unticked by hand). */
data class HabitData(val stored: StoredHabit, val entries: Map<LocalDate, Int>)

/** Habits and what was entered for them, kept with Room next to workouts. All on the phone. */
interface HabitRepository {
    /** In list order, archived ones included. */
    fun habits(): Flow<List<StoredHabit>>

    /** Amounts entered by hand, by habit id. */
    fun entries(): Flow<Map<String, Map<LocalDate, Int>>>

    /** Adds the habit, or replaces it if the id exists (its entries are kept). */
    suspend fun save(habit: StoredHabit)

    /** Deletes the habit and all its entries. */
    suspend fun delete(id: String)

    /** Sets the amount for a day. 0 is kept as "unticked by hand", which wins over an automatic tick. */
    suspend fun setAmount(habitId: String, date: LocalDate, amount: Int)

    /** Removes what was entered for a day, so an automatic tick (if any) shows again. */
    suspend fun clearAmount(habitId: String, date: LocalDate)

    suspend fun wipe()
}

/** Builds the log the habit rules read, adding the days a habit ticked itself. */
object HabitLogs {
    /** The local calendar days with at least one finished workout. */
    fun workoutDays(workouts: List<Workout>, zone: ZoneId): Set<LocalDate> =
        workouts.map { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() }.toSet()

    /**
     * The days the Morning and Night routine habits tick themselves: days every Morning (or Evening) routine was
     * finished. Deleting a routine takes its finished days with it, so those days stop counting.
     */
    fun routineDays(routines: List<Routine>, done: Collection<RoutineDone>): Map<AutoSource, Set<LocalDate>> = mapOf(
        AutoSource.MORNING_ROUTINE to Routines.autoDays(routines, done, DayPart.MORNING),
        AutoSource.NIGHT_ROUTINE to Routines.autoDays(routines, done, DayPart.EVENING),
    )

    fun log(
        habit: Habit,
        entries: Map<LocalDate, Int>,
        workoutDays: Set<LocalDate>,
        routineDays: Map<AutoSource, Set<LocalDate>> = emptyMap(),
    ): HabitLog = HabitLog(
        entries = entries,
        autoDays = when (habit.auto) {
            AutoSource.WORKOUT -> workoutDays
            AutoSource.MORNING_ROUTINE, AutoSource.NIGHT_ROUTINE -> routineDays[habit.auto].orEmpty()
            AutoSource.NONE -> emptySet()
        },
    )
}
