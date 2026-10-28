package com.homeworkoutcoach.app.ui.journal

import com.homeworkoutcoach.app.data.JournalRepository
import com.homeworkoutcoach.app.ui.planner.PlannerFormat
import com.homeworkoutcoach.app.ui.components.DayBadge
import com.homeworkoutcoach.engine.Habits
import com.homeworkoutcoach.engine.Journal
import com.homeworkoutcoach.engine.JournalEntry
import java.time.LocalDate

/** An earlier entry in the list. */
data class PastEntryUi(val date: LocalDate, val label: String, val win: String, val improve: String)

data class JournalUi(
    val date: LocalDate,
    /** "Today", "Yesterday", "Fri 23 Oct". */
    val title: String,
    /** "Wednesday 28 October". */
    val subtitle: String,
    val isToday: Boolean,
    /** What is saved for [date]; empty if nothing. */
    val win: String,
    val improve: String,
    val hasEntry: Boolean,
    /** "Written on 4 of the last 7 days." */
    val summary: String,
    /** Every other day with an entry, newest first. */
    val past: List<PastEntryUi>,
    /** A tick under each day of the week shown that has an entry. */
    val badges: Map<LocalDate, DayBadge> = emptyMap(),
)

class JournalPresenter {
    fun present(entries: List<JournalEntry>, date: LocalDate, today: LocalDate): JournalUi {
        val mine = Journal.forDay(entries, date)
        val lastWeek = (0L..6L).map { today.minusDays(it) }.toSet()
        val written = entries.count { it.date in lastWeek }
        return JournalUi(
            date = date,
            title = PlannerFormat.dayTitle(date, today),
            subtitle = PlannerFormat.dayLong(date),
            isToday = date == today,
            win = mine?.win.orEmpty(),
            improve = mine?.improve.orEmpty(),
            hasEntry = mine != null,
            summary = if (written == 0) "Two lines a day is plenty." else "Written on $written of the last 7 days.",
            past = Journal.newestFirst(entries).filter { it.date != date }
                .map { PastEntryUi(it.date, PlannerFormat.dayTitle(it.date, today), it.win, it.improve) },
            badges = (0L..6L).map { Habits.weekStart(date).plusDays(it) }
                .filter { d -> !d.isAfter(today) && Journal.forDay(entries, d) != null }
                .associateWith { DayBadge("✓", complete = true) },
        )
    }
}

/** What the journal screen does to entries. */
class JournalActions(private val repo: JournalRepository) {
    /** Saves the entry for [date]. Returns what to fix, or null once it is saved. */
    suspend fun save(date: LocalDate, win: String, improve: String): String? {
        Journal.problem(win, improve)?.let { return it }
        repo.save(Journal.create(date, win, improve))
        return null
    }

    suspend fun delete(date: LocalDate) = repo.delete(date)
}
