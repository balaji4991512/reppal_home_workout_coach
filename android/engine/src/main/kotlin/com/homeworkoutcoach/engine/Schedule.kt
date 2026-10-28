package com.homeworkoutcoach.engine

import java.time.LocalDate

/** Where a planned session stands against the calendar. */
enum class SessionStatus {
    /** A workout was saved on its day. */
    DONE,

    /** Its day passed without one, and the workout was done on a later day of the same week. */
    MADE_UP,

    /** Its day has passed and no workout was saved. */
    SKIPPED,

    /** Its day is today and it is not done yet. */
    TODAY,

    /** Its day is still to come. */
    UPCOMING,

    /** Its day passed before the plan was made, so it was never due. */
    BEFORE_PLAN,
}

/** One session of the plan's week ([index] is its position in the week's sequence) placed on its own [date]. */
data class ScheduledSession(val index: Int, val weekday: Int, val date: LocalDate, val status: SessionStatus)

/**
 * A workout belongs to its own weekday, and a day that ends without one is skipped. A skipped workout can be made up
 * on a later day of the same week, which marks it [SessionStatus.MADE_UP]. Nothing moves a session to another day, and
 * a skipped one does not push the others back.
 */
object Schedule {
    /**
     * Where one session on [date] stands. [planStart] is the day the plan was made (null counts as today, so nothing
     * before today is ever skipped by guesswork); [doneDates] are the days with a workout done on its own day, and
     * [madeUpDates] the days whose workout was done later.
     */
    fun status(
        date: LocalDate,
        today: LocalDate,
        doneDates: Set<LocalDate>,
        planStart: LocalDate?,
        madeUpDates: Set<LocalDate> = emptySet(),
    ): SessionStatus = when {
        date in doneDates -> SessionStatus.DONE
        date in madeUpDates -> SessionStatus.MADE_UP
        date < (planStart ?: today) -> SessionStatus.BEFORE_PLAN
        date < today -> SessionStatus.SKIPPED
        date == today -> SessionStatus.TODAY
        else -> SessionStatus.UPCOMING
    }

    /** The sessions of the week that contains [today] (Monday to Sunday), each on the weekday [weekdays] gives it. */
    fun week(
        weekdays: List<Int>,
        today: LocalDate,
        doneDates: Set<LocalDate>,
        planStart: LocalDate?,
        madeUpDates: Set<LocalDate> = emptySet(),
    ): List<ScheduledSession> {
        val monday = Habits.weekStart(today)
        return weekdays.mapIndexed { i, w ->
            val date = monday.plusDays(w.toLong())
            ScheduledSession(i, w, date, status(date, today, doneDates, planStart, madeUpDates))
        }
    }

    /** The skipped sessions of this week that can still be made up, oldest first. */
    fun toMakeUp(week: List<ScheduledSession>): List<ScheduledSession> = week.filter { it.status == SessionStatus.SKIPPED }

    /** Whether a workout done on [doneOn] may stand in for the session on [date]: later, in the same week. */
    fun canMakeUp(date: LocalDate, doneOn: LocalDate): Boolean = date < doneOn && Habits.weekStart(date) == Habits.weekStart(doneOn)

    /** Today's session, if today is a training day and it is not done yet. */
    fun today(week: List<ScheduledSession>): ScheduledSession? = week.firstOrNull { it.status == SessionStatus.TODAY }

    /** The next session still to come, after today. */
    fun next(week: List<ScheduledSession>): ScheduledSession? = week.firstOrNull { it.status == SessionStatus.UPCOMING }
}
