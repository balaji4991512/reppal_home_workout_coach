package com.homeworkoutcoach.app.data

import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.flow.Flow

/** Finished focus sessions, kept with Room. (A running timer is kept in the settings.) All on the phone. */
interface FocusRepository {
    /** Every session, newest first. */
    fun sessions(): Flow<List<FocusSession>>

    /** Adds the session, or replaces the one with the same id (so saving a session twice is harmless). */
    suspend fun save(session: FocusSession)

    suspend fun delete(id: String)

    suspend fun wipe()
}
