package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.engine.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomJournalRepository(private val db: TrainingDatabase) : JournalRepository {
    private val dao get() = db.journal()

    override fun entries(): Flow<List<JournalEntry>> = dao.observeAll().map { rows ->
        rows.map { JournalEntry(LocalDate.ofEpochDay(it.epochDay), it.win, it.improve) }
    }

    override suspend fun save(entry: JournalEntry) = dao.upsert(JournalEntity(entry.date.toEpochDay(), entry.win, entry.improve))

    override suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())

    override suspend fun wipe() = dao.clear()
}
