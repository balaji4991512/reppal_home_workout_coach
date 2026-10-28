package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.WeeklyReflection
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The end-of-week reflections, one per week (by its Monday), kept with Room. All on the phone. */
interface ReflectionRepository {
    /** Every reflection, newest week first. */
    fun reflections(): Flow<List<WeeklyReflection>>

    /** Adds the reflection, or replaces the one for that week. */
    suspend fun save(reflection: WeeklyReflection)

    suspend fun delete(weekStart: LocalDate)

    suspend fun wipe()
}
