package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Journal entries, one per day, kept with Room. All on the phone. */
interface JournalRepository {
    /** Every entry, newest day first. */
    fun entries(): Flow<List<JournalEntry>>

    /** Adds the entry, or replaces the one for that day. */
    suspend fun save(entry: JournalEntry)

    suspend fun delete(date: LocalDate)

    suspend fun wipe()
}
