package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.Routine
import com.homeworkoutcoach.engine.RoutineDone
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A routine with the days it was finished, for export and restore. */
data class RoutineBundle(val routine: Routine, val done: Set<LocalDate>)

/** Routines, their steps and the days they were finished, kept with Room. All on the phone. */
interface RoutineRepository {
    /** In list order, each with its steps in order. */
    fun routines(): Flow<List<Routine>>

    /** Every day a routine was finished. */
    fun done(): Flow<Set<RoutineDone>>

    /** Adds the routine or replaces it, with all its steps. Finished days are kept. */
    suspend fun save(routine: Routine)

    /** Deletes the routine, its steps and its finished days. Habits linked to its steps are untouched. */
    suspend fun delete(id: String)

    /** Marks a routine finished for a day, or takes that back. */
    suspend fun setDone(routineId: String, date: LocalDate, done: Boolean)

    suspend fun wipe()
}
