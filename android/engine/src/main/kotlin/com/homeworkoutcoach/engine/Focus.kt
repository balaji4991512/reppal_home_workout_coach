package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A focus session that is running: it started at [startMillis] and ends [plannedMinutes] later. The timer counts from
 * the end time, so it keeps counting with the screen off or the app closed.
 */
data class FocusRun(val startMillis: Long, val plannedMinutes: Int, val taskTitle: String? = null) {
    val endMillis: Long get() = startMillis + plannedMinutes * MINUTE_MS

    companion object {
        const val MINUTE_MS = 60_000L
    }
}

/** A finished (or stopped) focus session. [actualMinutes] is the whole minutes spent, never more than planned. */
data class FocusSession(
    val id: String,
    val startMillis: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    /** True if the whole planned time was spent. */
    val completed: Boolean,
    val taskTitle: String? = null,
) {
    init {
        require(plannedMinutes in 1..Focus.MAX_MINUTES) { "planned minutes must be 1..${Focus.MAX_MINUTES}" }
        require(actualMinutes in 1..plannedMinutes) { "actual minutes must be 1..planned" }
    }
}

object Focus {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180

    /** The quick choices, in minutes. */
    val presets = listOf(15, 25, 45, 60)

    /** What is wrong with a typed length, or null. */
    fun problem(minutes: Int?): String? =
        if (minutes == null || minutes !in MIN_MINUTES..MAX_MINUTES) "Enter minutes from $MIN_MINUTES to $MAX_MINUTES." else null

    fun remainingMillis(run: FocusRun, now: Long): Long = (run.endMillis - now).coerceAtLeast(0)

    fun isFinished(run: FocusRun, now: Long): Boolean = now >= run.endMillis

    /** 0 at the start to 1 at the end. */
    fun fraction(run: FocusRun, now: Long): Double =
        ((now - run.startMillis).toDouble() / (run.endMillis - run.startMillis)).coerceIn(0.0, 1.0)

    /** The countdown as "24:59", or "1:05:00" from an hour up. Rounded up, so it never shows 0:00 while time is left. */
    fun format(millis: Long): String {
        val total = (millis + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * Ends [run] at [now]: the whole time if it ran out, otherwise the whole minutes spent so far. Under a minute is too
     * short to count, so nothing is saved (null).
     */
    fun finish(run: FocusRun, now: Long, id: String): FocusSession? {
        val finished = isFinished(run, now)
        val spent = if (finished) run.plannedMinutes else ((now - run.startMillis) / FocusRun.MINUTE_MS).toInt().coerceIn(0, run.plannedMinutes)
        if (spent < 1) return null
        return FocusSession(id, run.startMillis, run.plannedMinutes, spent, finished, run.taskTitle)
    }

    private fun day(s: FocusSession, zone: ZoneId): LocalDate = Instant.ofEpochMilli(s.startMillis).atZone(zone).toLocalDate()

    /** Minutes spent on a day (by when the sessions started). */
    fun minutesOn(sessions: List<FocusSession>, date: LocalDate, zone: ZoneId): Int =
        sessions.filter { day(it, zone) == date }.sumOf { it.actualMinutes }

    /** Minutes in the week (Monday to Sunday) that contains [date]. */
    fun minutesInWeek(sessions: List<FocusSession>, date: LocalDate, zone: ZoneId): Int {
        val first = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = first.plusDays(6)
        return sessions.filter { day(it, zone) in first..last }.sumOf { it.actualMinutes }
    }

    fun sessionsOn(sessions: List<FocusSession>, date: LocalDate, zone: ZoneId): Int = sessions.count { day(it, zone) == date }

    /** The latest [n] sessions, newest first. */
    fun recent(sessions: List<FocusSession>, n: Int): List<FocusSession> = sessions.sortedByDescending { it.startMillis }.take(n)
}
