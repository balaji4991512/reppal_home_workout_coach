package com.homeworkoutcoach.app.data.room

import com.homeworkoutcoach.app.data.HabitRepository
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.Habit
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitSchedule
import com.homeworkoutcoach.engine.Habits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

class RoomHabitRepository(private val db: TrainingDatabase) : HabitRepository {
    private val dao get() = db.habits()

    override fun habits(): Flow<List<StoredHabit>> = dao.observeHabits().map { rows -> rows.map(::toStored) }

    override fun entries(): Flow<Map<String, Map<LocalDate, Int>>> = dao.observeEntries().map { rows ->
        rows.groupBy({ it.habitId }) { LocalDate.ofEpochDay(it.epochDay) to it.amount }.mapValues { it.value.toMap() }
    }

    // An upsert on the habit row updates it in place, so its entries are not cascade-deleted.
    override suspend fun save(habit: StoredHabit) = dao.upsert(toEntity(habit))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun setAmount(habitId: String, date: LocalDate, amount: Int) =
        dao.upsertEntry(HabitEntryEntity(habitId, date.toEpochDay(), amount.coerceAtLeast(0)))

    override suspend fun clearAmount(habitId: String, date: LocalDate) = dao.deleteEntry(habitId, date.toEpochDay())

    override suspend fun wipe() {
        dao.clearEntries()
        dao.clearHabits()
    }

    private fun toEntity(s: StoredHabit): HabitEntity {
        val h = s.habit
        val (type, value) = when (val sc = h.schedule) {
            HabitSchedule.Daily -> DAILY to 0
            is HabitSchedule.Weekdays -> WEEKDAYS to sc.days.fold(0) { mask, d -> mask or (1 shl (d.value - 1)) }
            is HabitSchedule.TimesPerWeek -> TIMES to sc.times
        }
        return HabitEntity(
            id = h.id,
            name = h.name,
            kind = h.kind.name.lowercase(),
            target = h.target,
            scheduleType = type,
            scheduleValue = value,
            startEpochDay = h.startDate.toEpochDay(),
            auto = h.auto.name.lowercase(),
            archived = h.archived,
            icon = s.icon,
            color = s.color,
            position = s.position,
            reminderMinuteOfDay = null,
            reminderMinutes = h.reminders.joinToString(",") { (it.hour * 60 + it.minute).toString() },
        )
    }

    private fun toStored(e: HabitEntity): StoredHabit {
        val schedule = when (e.scheduleType) {
            WEEKDAYS -> HabitSchedule.Weekdays(DayOfWeek.entries.filter { e.scheduleValue and (1 shl (it.value - 1)) != 0 }.toSet())
            TIMES -> HabitSchedule.TimesPerWeek(e.scheduleValue)
            else -> HabitSchedule.Daily
        }
        return StoredHabit(
            habit = Habit(
                id = e.id,
                name = e.name,
                kind = HabitKind.entries.firstOrNull { it.name.lowercase() == e.kind } ?: HabitKind.DONE,
                target = e.target,
                schedule = schedule,
                startDate = LocalDate.ofEpochDay(e.startEpochDay),
                auto = AutoSource.entries.firstOrNull { it.name.lowercase() == e.auto } ?: AutoSource.NONE,
                archived = e.archived,
                reminders = remindersOf(e),
            ),
            icon = e.icon,
            color = e.color,
            position = e.position,
        )
    }

    /** The times saved with the habit; a habit saved before there were many has its one in the older column. */
    private fun remindersOf(e: HabitEntity): List<java.time.LocalTime> {
        val minutes = e.reminderMinutes?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: listOfNotNull(e.reminderMinuteOfDay)
        return Habits.tidyReminders(minutes.filter { it in 0 until 24 * 60 }.map { java.time.LocalTime.of(it / 60, it % 60) })
    }

    private companion object {
        // Stored values; keep them stable.
        const val DAILY = "daily"
        const val WEEKDAYS = "weekdays"
        const val TIMES = "times"
    }
}
