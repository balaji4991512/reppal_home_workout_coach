package com.homeworkoutcoach.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** How a habit is tracked (specs/HABITS_PLANNER_SPEC.md, section 1). */
enum class HabitKind {
    /** Done or not done; target is 1. */
    DONE,

    /** A number (glasses of water); target is the count to reach. */
    COUNT,

    /** Minutes (reading); target is the minutes to reach. */
    TIME,
}

/** When a habit is due. */
sealed interface HabitSchedule {
    data object Daily : HabitSchedule

    /** Only on these weekdays. */
    data class Weekdays(val days: Set<DayOfWeek>) : HabitSchedule

    /** [times] days a week, on any days: due every day until the week's count is met. */
    data class TimesPerWeek(val times: Int) : HabitSchedule
}

/** What ticks a habit by itself. A tick made by hand always wins, including unticking. */
enum class AutoSource { NONE, WORKOUT, MORNING_ROUTINE, NIGHT_ROUTINE, JOURNAL }

data class Habit(
    val id: String,
    val name: String,
    val kind: HabitKind,
    /** 1 for [HabitKind.DONE]; the count or minutes otherwise. */
    val target: Int,
    val schedule: HabitSchedule,
    val startDate: LocalDate,
    val auto: AutoSource = AutoSource.NONE,
    val archived: Boolean = false,
    /** The times of day to be reminded, on the days the habit is due; empty for no reminder. Water may want many. */
    val reminders: List<LocalTime> = emptyList(),
) {
    init {
        require(reminders.size <= Habits.MAX_REMINDERS) { "at most ${Habits.MAX_REMINDERS} reminders" }
        require(reminders.distinct().size == reminders.size) { "reminder times must differ" }
        require(target >= 1) { "target must be at least 1" }
        require(kind != HabitKind.DONE || target == 1) { "a done/not done habit has target 1" }
        when (schedule) {
            is HabitSchedule.Weekdays -> require(schedule.days.isNotEmpty()) { "choose at least one weekday" }
            is HabitSchedule.TimesPerWeek -> require(schedule.times in 1..7) { "times a week must be 1..7" }
            HabitSchedule.Daily -> Unit
        }
    }
}

/**
 * What was recorded for one habit. [entries] holds the amounts typed or ticked by hand per calendar day (0 means
 * unticked by hand); [autoDays] are days the habit's [AutoSource] happened. Days are local calendar days, never
 * timestamps, so a day does not move with the time zone.
 */
data class HabitLog(
    val entries: Map<LocalDate, Int> = emptyMap(),
    val autoDays: Set<LocalDate> = emptySet(),
)

/** How a single day looks in the habit's calendar. No state means "missed": there are no red marks. */
enum class DayState { DONE, PARTIAL, OPEN, NOT_DUE }

/** [done] of [target] in a week, e.g. "4 of 5". */
data class WeekProgress(val done: Int, val target: Int) {
    val complete: Boolean get() = done >= target
}

/** [done] days of the [due] days that were due so far. A day or week still in progress is never counted as missed. */
data class HabitStats(val done: Int, val due: Int)

/**
 * How long a habit has gone on without a break. [current] and [best] count days, or weeks for a times-a-week habit
 * ([weekly]). A break only ever shows on a day or week that has finished, so a streak is never lost by today not being
 * done yet.
 */
data class Streaks(val current: Int, val best: Int, val weekly: Boolean)

object Habits {
    // ---- Days and amounts ----

    /** The week starts on Monday. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Amount counted on [date]: what was entered by hand, otherwise the target if it ticked itself, otherwise 0. */
    fun amount(habit: Habit, log: HabitLog, date: LocalDate): Int =
        log.entries[date] ?: if (date in log.autoDays) habit.target else 0

    fun isDone(habit: Habit, log: HabitLog, date: LocalDate): Boolean = amount(habit, log, date) >= habit.target

    /** The new amount after adding [delta] (a tick is +1 or -1 on a done/not done habit). Never below 0. */
    fun adjusted(habit: Habit, current: Int, delta: Int): Int {
        val next = (current + delta).coerceAtLeast(0)
        return if (habit.kind == HabitKind.DONE) next.coerceAtMost(1) else next
    }

    /** 0.0 to 1.0: how far [date] got towards the target. */
    fun fraction(habit: Habit, log: HabitLog, date: LocalDate): Double =
        (amount(habit, log, date).toDouble() / habit.target).coerceIn(0.0, 1.0)

    // ---- Due ----

    /**
     * Whether the habit is due on [date]. Nothing is due before the start date or once archived. A times-a-week habit
     * stays due until the week has its count, and a day that was ticked anyway stays due so it keeps showing as done.
     */
    fun isDue(habit: Habit, log: HabitLog, date: LocalDate): Boolean {
        if (habit.archived || date < habit.startDate) return false
        return when (val s = habit.schedule) {
            HabitSchedule.Daily -> true
            is HabitSchedule.Weekdays -> date.dayOfWeek in s.days
            is HabitSchedule.TimesPerWeek ->
                isDone(habit, log, date) || doneDaysBefore(habit, log, date) < s.times
        }
    }

    /** Days done earlier in the same week as [date]. */
    private fun doneDaysBefore(habit: Habit, log: HabitLog, date: LocalDate): Int =
        generateSequence(weekStart(date)) { it.plusDays(1) }.takeWhile { it < date }.count { isDone(habit, log, it) }

    fun dayState(habit: Habit, log: HabitLog, date: LocalDate): DayState = when {
        isDone(habit, log, date) -> DayState.DONE
        amount(habit, log, date) > 0 -> DayState.PARTIAL
        isDue(habit, log, date) -> DayState.OPEN
        else -> DayState.NOT_DUE
    }

    // ---- Progress ----

    /**
     * The week containing [date]: days done against the target for the week. The target is the scheduled days from
     * the start date on, or the count for a times-a-week habit (no more than the days left from the start date).
     */
    fun weekProgress(habit: Habit, log: HabitLog, date: LocalDate): WeekProgress {
        val first = weekStart(date)
        val days = (0L..6L).map { first.plusDays(it) }
        val done = days.count { isDone(habit, log, it) }
        val target = when (val s = habit.schedule) {
            HabitSchedule.Daily -> days.count { it >= habit.startDate }
            is HabitSchedule.Weekdays -> days.count { it >= habit.startDate && it.dayOfWeek in s.days }
            is HabitSchedule.TimesPerWeek -> minOf(s.times, days.count { it >= habit.startDate })
        }
        return WeekProgress(done, target)
    }

    /** Days done in the inclusive range, however they were scheduled (for "this month" and "all time"). */
    fun doneDays(habit: Habit, log: HabitLog, from: LocalDate, to: LocalDate): Int =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.count { isDone(habit, log, it) }

    /**
     * Done against due from the start date (or [from]) to [today]. Fixed schedules count the days that were due and
     * over (today counts only if done). A times-a-week habit counts N for each finished week and, for the week in
     * progress, only what is already done, so it is never behind before the week ends. [until] stops the count earlier,
     * for a period that has already ended (a past week), whose last day is then counted like any other.
     */
    fun stats(habit: Habit, log: HabitLog, from: LocalDate, today: LocalDate, until: LocalDate = today): HabitStats {
        val start = maxOf(from, habit.startDate)
        // Only days up to [until] are counted; days before [today] count as over, and [today] only if done.
        val end = minOf(until, today)
        if (start > end) return HabitStats(0, 0)
        val schedule = habit.schedule
        if (schedule !is HabitSchedule.TimesPerWeek) {
            var done = 0
            var due = 0
            var day = start
            while (day <= end) {
                val doneToday = isDone(habit, log, day)
                if (doneToday) done++
                // A day done on a non-scheduled day still counts as due, so done never passes due.
                if ((isDueBySchedule(habit, day) && day < today) || doneToday) due++
                day = day.plusDays(1)
            }
            return HabitStats(done, due)
        }
        var done = 0
        var due = 0
        var week = weekStart(start)
        while (week <= end) {
            val days = (0L..6L).map { week.plusDays(it) }.filter { it >= start && it <= end }
            val inWeek = days.count { isDone(habit, log, it) }
            val counted = minOf(inWeek, schedule.times)
            val last = week.plusDays(6)
            val sinceStart = (0L..6L).map { week.plusDays(it) }.count { it >= start && it <= end }
            done += counted
            due += if (last < today) minOf(schedule.times, sinceStart) else counted
            week = week.plusDays(7)
        }
        return HabitStats(done, due)
    }

    /**
     * The streaks up to [today]. A fixed-schedule habit counts the days it was done in a row: a scheduled day that has
     * passed undone resets it, while a day it is not scheduled, and today until it is done, change nothing. A
     * times-a-week habit counts the weeks in a row that reached the count; the week in progress adds one once it
     * does, and never breaks. Nothing counts before the start date.
     */
    fun streaks(habit: Habit, log: HabitLog, today: LocalDate): Streaks {
        val schedule = habit.schedule
        val weekly = schedule is HabitSchedule.TimesPerWeek
        if (habit.startDate.isAfter(today)) return Streaks(0, 0, weekly)
        var run = 0
        var best = 0
        if (schedule is HabitSchedule.TimesPerWeek) {
            var week = weekStart(habit.startDate)
            while (!week.isAfter(today)) {
                val days = (0L..6L).map { week.plusDays(it) }
                val counted = days.filter { it >= habit.startDate }
                val target = minOf(schedule.times, counted.size)
                val done = counted.count { isDone(habit, log, it) }
                val finished = week.plusDays(6) < today
                when {
                    done >= target -> { run++; best = maxOf(best, run) }
                    finished -> run = 0
                }
                week = week.plusDays(7)
            }
        } else {
            var day = habit.startDate
            while (!day.isAfter(today)) {
                when {
                    isDone(habit, log, day) -> { run++; best = maxOf(best, run) }
                    isDueBySchedule(habit, day) && day < today -> run = 0
                }
                day = day.plusDays(1)
            }
        }
        return Streaks(run, best, weekly)
    }

    /**
     * Whether to send the reminder on [today]: the habit has a reminder, is not archived, is due and is not already done. A
     * habit done earlier in the day, or not due (a rest day, or a times-a-week habit already met), stays quiet.
     */
    fun shouldRemind(habit: Habit, log: HabitLog, today: LocalDate): Boolean =
        habit.reminders.isNotEmpty() && !habit.archived && isDue(habit, log, today) && !isDone(habit, log, today)

    /**
     * The next moment to ring a reminder at [time] that is after [after]: today at [time] if that is still ahead,
     * otherwise tomorrow. Whether to actually ring is decided then, by [shouldRemind].
     */
    fun nextReminder(time: LocalTime, after: LocalDateTime): LocalDateTime {
        val today = after.toLocalDate().atTime(time)
        return if (today.isAfter(after)) today else today.plusDays(1)
    }

    const val MAX_REMINDERS = 12

    /** Sorted, with repeats dropped and no more than [MAX_REMINDERS]: the times as they are stored. */
    fun tidyReminders(times: Collection<LocalTime>): List<LocalTime> = times.toSortedSet().take(MAX_REMINDERS)

    /**
     * Times from [from] up to and including [to], every [everyMinutes] ("every 2 hours, 8 am to 8 pm"). Stops at the
     * most reminders a habit can have, and never wraps past midnight.
     */
    fun evenly(from: LocalTime, to: LocalTime, everyMinutes: Int): List<LocalTime> {
        require(everyMinutes >= 1) { "the gap must be at least a minute" }
        val out = mutableListOf<LocalTime>()
        var minute = from.hour * 60 + from.minute
        val last = to.hour * 60 + to.minute
        while (minute <= last && out.size < MAX_REMINDERS) {
            out += LocalTime.of(minute / 60, minute % 60)
            minute += everyMinutes
        }
        return out
    }

    /** Fixed-schedule due check that ignores what was logged (used for counting past days). */
    private fun isDueBySchedule(habit: Habit, date: LocalDate): Boolean = when (val s = habit.schedule) {
        HabitSchedule.Daily -> true
        is HabitSchedule.Weekdays -> date.dayOfWeek in s.days
        is HabitSchedule.TimesPerWeek -> true
    }

    // ---- Starting suggestions ----

    /** A habit to start from. [icon] is a key the app draws (see its icon catalogue). */
    data class Suggestion(
        val name: String,
        val kind: HabitKind,
        val target: Int,
        val auto: AutoSource = AutoSource.NONE,
        val icon: String = "check",
    )

    private fun done(name: String, icon: String, auto: AutoSource = AutoSource.NONE) = Suggestion(name, HabitKind.DONE, 1, auto, icon)
    private fun count(name: String, icon: String, n: Int) = Suggestion(name, HabitKind.COUNT, n, icon = icon)
    private fun minutes(name: String, icon: String, n: Int) = Suggestion(name, HabitKind.TIME, n, icon = icon)

    /**
     * Things people do, most common first (specs/HABITS_PLANNER_SPEC.md; the user picks any, edits it or ignores them
     * all). Four tick themselves from the app's own data.
     */
    val suggestions: List<Suggestion> = listOf(
        done("Workout", "dumbbell", AutoSource.WORKOUT),
        done("Morning routine", "sun", AutoSource.MORNING_ROUTINE),
        done("Night routine", "moon", AutoSource.NIGHT_ROUTINE),
        done("Journal", "pen", AutoSource.JOURNAL),
        count("Water", "drop", 8),
        minutes("Sleep", "sleep", 7 * 60),
        minutes("Reading", "book", 20),
        minutes("Meditation", "lotus", 10),
        minutes("Yoga", "yoga", 20),
        minutes("Walking", "walk", 30),
        // body and health
        done("Take vitamins", "pill"),
        done("Take medicine", "pill"),
        done("Brush teeth", "tooth"),
        done("Floss", "floss"),
        done("Cold shower", "shower"),
        done("Skincare routine", "lotion"),
        done("Wear sunscreen", "lotion"),
        done("Wash hands", "soap"),
        done("Check blood pressure", "stethoscope"),
        done("Track blood sugar", "stethoscope"),
        done("Stretch", "stretch"),
        done("Posture check", "posture"),
        done("Rest your eyes", "eye"),
        done("Go to bed on time", "bed"),
        done("Wake up early", "alarm"),
        done("Make the bed", "bed"),
        // movement
        done("Take the stairs", "steps"),
        minutes("Running", "run", 30),
        minutes("Cycling", "bike", 30),
        minutes("Swimming", "swim", 30),
        count("Push-ups", "pushup", 20),
        count("Sit-ups", "pushup", 20),
        count("Squats", "dumbbell", 30),
        minutes("Plank", "stopwatch", 2),
        minutes("Hiking", "hike", 60),
        minutes("Dancing", "dance", 20),
        minutes("Skipping rope", "stopwatch", 10),
        minutes("Boxing", "boxing", 30),
        minutes("Martial arts", "martial", 45),
        minutes("Climbing", "climb", 60),
        minutes("Football", "soccer", 60),
        minutes("Basketball", "basketball", 45),
        minutes("Tennis", "tennis", 60),
        minutes("Badminton", "badminton", 45),
        minutes("Cricket", "cricket", 60),
        minutes("Golf", "golf", 90),
        minutes("Rowing", "rowing", 20),
        minutes("Surfing", "surf", 60),
        minutes("Skating", "skate", 30),
        minutes("Take a break and move", "steps", 5),
        // food and drink
        done("Eat a healthy breakfast", "meal"),
        done("Eat a salad", "salad"),
        count("Fruit or veg", "carrot", 5),
        done("Eat fruit", "fruit"),
        done("Cook at home", "cook"),
        done("Pack lunch", "lunchbox"),
        done("No sugar", "candy"),
        done("No soda", "soda"),
        done("No junk food", "burger"),
        done("No alcohol", "wine"),
        count("Cups of tea", "tea", 2),
        count("Cups of coffee", "coffee", 2),
        count("Glasses of milk", "milk", 1),
        done("Eat protein", "egg"),
        done("Eat slowly", "meal"),
        done("Intermittent fasting", "fasting"),
        done("Track calories", "list"),
        // mind
        minutes("Breathing exercise", "breath", 5),
        done("Gratitude", "gratitude"),
        done("Pray", "pray"),
        done("Smile", "smile"),
        minutes("Learn a language", "language", 15),
        minutes("Study", "graduation", 60),
        minutes("Practice coding", "code", 30),
        minutes("Online course", "laptop", 30),
        done("Review flashcards", "flashcards"),
        minutes("Puzzles", "puzzle", 15),
        minutes("Chess", "chess", 20),
        minutes("Listen to a podcast", "podcast", 20),
        done("Read the news", "news"),
        count("Pages read", "books", 10),
        done("Write ideas", "idea"),
        done("Brain dump", "brain"),
        done("Say no to a distraction", "target"),
        done("Therapy or counselling", "couch"),
        // creative
        minutes("Play an instrument", "guitar", 20),
        minutes("Practice piano", "piano", 20),
        minutes("Practice singing", "mic", 15),
        minutes("Draw or sketch", "draw", 15),
        minutes("Paint", "art", 30),
        minutes("Write", "pen", 20),
        done("Take a photo", "camera"),
        minutes("Listen to music", "music", 20),
        // home
        done("Tidy up", "broom"),
        done("Do the dishes", "sponge"),
        done("Do laundry", "laundry"),
        done("Take out the trash", "trash"),
        done("Water the plants", "plant"),
        done("Feed the pet", "dog"),
        minutes("Walk the dog", "dog", 30),
        done("Clean the litter", "cat"),
        done("Plan meals", "list"),
        done("Grocery shopping", "cart"),
        done("Declutter", "box"),
        // work and money
        done("Plan the day", "calendar"),
        done("Clear your inbox", "inbox"),
        minutes("Deep work", "dnd", 120),
        done("Review goals", "target"),
        done("Track expenses", "receipt"),
        done("Save money", "money"),
        done("Invest", "chart"),
        done("Check the budget", "bank"),
        done("Pay bills", "card"),
        done("Learn something new", "idea"),
        // screens and habits to drop
        done("No phone before bed", "phone"),
        done("No social media", "social"),
        done("No smoking", "nosmoke"),
        done("Limit TV", "tv"),
        done("Limit games", "game"),
        done("No snoozing", "alarm"),
        // people
        done("Call family", "call"),
        done("Message a friend", "letter"),
        done("Spend time with family", "family"),
        done("Date night", "couple"),
        done("Play with the kids", "kid"),
        done("Do something kind", "kindness"),
        done("Volunteer", "volunteer"),
        done("Donate", "donate"),
        done("Give a compliment", "hug"),
        // outdoors
        minutes("Get sunlight", "sunlight", 15),
        minutes("Time in nature", "tree", 30),
        minutes("Gardening", "flower", 30),
        done("Watch the sunrise", "sunrise"),
        done("Stargaze", "stars"),
        done("Go fishing", "fishing"),
        done("Go camping", "camp"),
        done("Recycle", "recycle"),
    )

    /**
     * Suggestions to show while a name is being typed: names that start with [query] first, then names that contain it
     * (case and spaces around it ignored). Anything in [skip] (names the person already has) is left out. With nothing
     * typed it gives the first [limit], the most common.
     */
    fun suggest(query: String, skip: Collection<String> = emptyList(), limit: Int = 6): List<Suggestion> {
        val q = query.trim().lowercase()
        val free = suggestions.filter { s -> skip.none { it.trim().equals(s.name, ignoreCase = true) } }
        if (q.isEmpty()) return free.take(limit)
        val (starts, rest) = free.partition { it.name.lowercase().startsWith(q) }
        return (starts + rest.filter { r -> r.name.lowercase().contains(q) || r.name.lowercase().split(' ').any { it.startsWith(q) } }).take(limit)
    }
}
