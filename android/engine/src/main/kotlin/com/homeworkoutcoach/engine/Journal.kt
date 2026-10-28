package com.homeworkoutcoach.engine

import java.time.LocalDate

/** One day's journal: a win and one thing to do better (specs/JOURNAL_GOALS_FOCUS_CHARACTER_SPEC.md, section 1). */
data class JournalEntry(val date: LocalDate, val win: String = "", val improve: String = "") {
    init {
        require(win.isNotBlank() || improve.isNotBlank()) { "an entry needs at least one line" }
    }
}

object Journal {
    const val MAX_LINE = 200

    const val WIN_PROMPT = "One win today"
    const val IMPROVE_PROMPT = "One thing to do better"

    /** The first thing to fix in what was typed, or null if it can be saved. */
    fun problem(win: String, improve: String): String? = when {
        win.isBlank() && improve.isBlank() -> "Write at least one line."
        win.trim().length > MAX_LINE || improve.trim().length > MAX_LINE -> "Keep each line to $MAX_LINE characters."
        else -> null
    }

    /** An entry from what was typed (trimmed). Check [problem] first. */
    fun create(date: LocalDate, win: String, improve: String): JournalEntry = JournalEntry(date, win.trim(), improve.trim())

    /** The entry for [date], if there is one. */
    fun forDay(entries: List<JournalEntry>, date: LocalDate): JournalEntry? = entries.firstOrNull { it.date == date }

    /** Newest day first. */
    fun newestFirst(entries: List<JournalEntry>): List<JournalEntry> = entries.sortedByDescending { it.date }

    /** The days the Journal habit ticks itself: days with an entry. */
    fun days(entries: Collection<JournalEntry>): Set<LocalDate> = entries.map { it.date }.toSet()
}
