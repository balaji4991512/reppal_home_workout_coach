package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A focus session that is running: it started at [startMillis] and ends [plannedMinutes] later, pushed back by the time
 * spent paused. The timer counts from the end time, so it keeps counting with the screen off or the app closed.
 * [pausedMillis] is the paused time already over; [pausedAtMillis] is when the current pause began, null while running.
 */
data class FocusRun(
    val startMillis: Long,
    val plannedMinutes: Int,
    val taskTitle: String? = null,
    val tag: String? = null,
    val pausedMillis: Long = 0,
    val pausedAtMillis: Long? = null,
) {
    /** When it ends, counting the pauses that are over. While paused this keeps moving on; see [Focus.remainingMillis]. */
    val endMillis: Long get() = startMillis + plannedMinutes * MINUTE_MS + pausedMillis

    val paused: Boolean get() = pausedAtMillis != null

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
    /** What the time went on ("Reading", "Study"); a habit linked to the same tag counts these minutes. */
    val tag: String? = null,
) {
    init {
        require(plannedMinutes in 1..Focus.MAX_MINUTES) { "planned minutes must be 1..${Focus.MAX_MINUTES}" }
        require(actualMinutes in 1..plannedMinutes) { "actual minutes must be 1..planned" }
    }
}

object Focus {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 180
    const val MAX_TAG = 24

    /** Tags offered before any has been used. */
    val starterTags = listOf("Reading", "Work", "Study", "Writing", "Coding", "Learning", "Practice", "Creative", "Planning", "Meditation")

    /** The quick choices, in minutes. */
    val presets = listOf(15, 25, 45, 60)

    /** What is wrong with a typed length, or null. */
    fun problem(minutes: Int?): String? =
        if (minutes == null || minutes !in MIN_MINUTES..MAX_MINUTES) "Enter minutes from $MIN_MINUTES to $MAX_MINUTES." else null

    /** Time focused so far, leaving out the pauses. It stands still while paused. */
    fun focusedMillis(run: FocusRun, now: Long): Long =
        ((run.pausedAtMillis ?: now) - run.startMillis - run.pausedMillis).coerceIn(0, run.plannedMinutes * FocusRun.MINUTE_MS)

    fun remainingMillis(run: FocusRun, now: Long): Long = run.plannedMinutes * FocusRun.MINUTE_MS - focusedMillis(run, now)

    /** A paused session never runs out. */
    fun isFinished(run: FocusRun, now: Long): Boolean = !run.paused && now >= run.endMillis

    /** 0 at the start to 1 at the end. */
    fun fraction(run: FocusRun, now: Long): Double =
        focusedMillis(run, now).toDouble() / (run.plannedMinutes * FocusRun.MINUTE_MS)

    /** [run] paused at [now]; unchanged if it already is. */
    fun pause(run: FocusRun, now: Long): FocusRun = if (run.paused) run else run.copy(pausedAtMillis = now)

    /** [run] going again at [now], its end pushed back by the pause; unchanged if it is not paused. */
    fun resume(run: FocusRun, now: Long): FocusRun {
        val at = run.pausedAtMillis ?: return run
        return run.copy(pausedMillis = run.pausedMillis + (now - at).coerceAtLeast(0), pausedAtMillis = null)
    }

    /** The countdown as "24:59", or "1:05:00" from an hour up. Rounded up, so it never shows 0:00 while time is left. */
    fun format(millis: Long): String {
        val total = (millis + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * Ends [run] at [now]: the whole time if it ran out, otherwise the whole minutes focused so far (pauses left out).
     * Under a minute is too short to count, so nothing is saved (null).
     */
    fun finish(run: FocusRun, now: Long, id: String): FocusSession? {
        val finished = isFinished(run, now)
        val spent = if (finished) run.plannedMinutes else (focusedMillis(run, now) / FocusRun.MINUTE_MS).toInt()
        if (spent < 1) return null
        return FocusSession(id, run.startMillis, run.plannedMinutes, spent, finished, run.taskTitle, run.tag)
    }

    /** A typed tag tidied up: spaces squeezed, cut to [MAX_TAG] characters; null if nothing is left. */
    fun cleanTag(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_TAG)?.trim()?.takeIf { it.isNotEmpty() }

    /** Tags are the same whatever the capital letters ("reading" is "Reading"). */
    fun sameTag(a: String?, b: String?): Boolean = a != null && b != null && a.trim().equals(b.trim(), ignoreCase = true)

    /**
     * The tag to keep for what was typed: the spelling already in use if there is one ("reading" becomes "Reading"),
     * otherwise the typed name tidied up. Null for a blank name.
     */
    fun tagFor(raw: String?, known: Collection<String>): String? {
        val clean = cleanTag(raw) ?: return null
        return known.firstOrNull { sameTag(it, clean) } ?: clean
    }

    /** The tags to choose from: the ones used before, latest first, then the starters not used yet. */
    fun tags(sessions: List<FocusSession>): List<String> {
        val used = sessions.sortedByDescending { it.startMillis }.mapNotNull { it.tag }
        return (used + starterTags).distinctBy { it.lowercase() }
    }

    /** Minutes under [tag] on each day, by when the sessions started. */
    fun minutesByDay(sessions: List<FocusSession>, tag: String, zone: ZoneId): Map<LocalDate, Int> =
        sessions.filter { sameTag(it.tag, tag) }.groupBy { day(it, zone) }.mapValues { (_, list) -> list.sumOf { it.actualMinutes } }

    /** The same, for every tag at once, keyed by the tag in lower case. */
    fun minutesByTagAndDay(sessions: List<FocusSession>, zone: ZoneId): Map<String, Map<LocalDate, Int>> =
        sessions.filter { it.tag != null }.groupBy { it.tag!!.trim().lowercase() }
            .mapValues { (_, list) -> list.groupBy { day(it, zone) }.mapValues { (_, d) -> d.sumOf { it.actualMinutes } } }

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
