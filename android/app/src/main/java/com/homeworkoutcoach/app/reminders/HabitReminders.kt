package com.homeworkoutcoach.app.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.homeworkoutcoach.app.HomeWorkoutCoachApp
import com.homeworkoutcoach.app.MainActivity
import com.homeworkoutcoach.app.R
import com.homeworkoutcoach.app.data.HabitLogs
import com.homeworkoutcoach.app.data.StoredHabit
import com.homeworkoutcoach.app.ui.habits.autoInputs
import com.homeworkoutcoach.engine.HabitKind
import com.homeworkoutcoach.engine.HabitLog
import com.homeworkoutcoach.engine.Habits
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm for each reminder time of each habit. Each alarm rings once, for the next time that reminder comes up; when
 * it rings the receiver reschedules it for the day after. [sync] makes the alarms match the habits: it adds, moves and
 * cancels, so it is safe to call whenever habits change, at app start and after a reboot.
 */
class HabitReminders(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = context.getSharedPreferences("habit_reminders", Context.MODE_PRIVATE)

    private fun minute(time: LocalTime) = time.hour * 60 + time.minute

    private fun intent(habitId: String, minute: Int): PendingIntent = PendingIntent.getBroadcast(
        context, "$habitId@$minute".hashCode(),
        Intent(context, HabitReminderReceiver::class.java).setData(android.net.Uri.parse("wick://habit/$habitId/$minute"))
            .putExtra(HabitReminderReceiver.HABIT, habitId).putExtra(HabitReminderReceiver.MINUTE, minute),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The alarm the first version set, one per habit; cancelled so it does not ring on top of the new ones. */
    private fun legacyIntent(habitId: String): PendingIntent = PendingIntent.getBroadcast(
        context, habitId.hashCode(),
        Intent(context, HabitReminderReceiver::class.java).setData(android.net.Uri.parse("wick://habit/$habitId"))
            .putExtra(HabitReminderReceiver.HABIT, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun sync(habits: List<StoredHabit>) {
        val wanted = habits.filter { !it.habit.archived }.flatMap { h -> h.habit.reminders.map { h to it } }
        val keys = wanted.map { (h, t) -> "${h.habit.id}@${minute(t)}" }.toSet()
        val before = prefs.getStringSet(SCHEDULED, emptySet()).orEmpty()
        (before - keys).forEach { key ->
            val id = key.substringBeforeLast('@')
            if (key.contains('@')) alarms.cancel(intent(id, key.substringAfterLast('@').toInt())) else alarms.cancel(legacyIntent(key))
        }
        wanted.forEach { (h, t) -> scheduleNext(h, t) }
        prefs.edit().putStringSet(SCHEDULED, keys).apply()
    }

    /** The alarm for the habit's reminder at [time], next after [after] (now by default). */
    fun scheduleNext(habit: StoredHabit, time: LocalTime, after: LocalDateTime = LocalDateTime.now(zone)) {
        val at = Habits.nextReminder(time, after).atZone(zone).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(habit.habit.id, minute(time)))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(habit.habit.id, minute(time)))
    }

    private companion object {
        const val SCHEDULED = "scheduled"
    }
}

/** Rings at one of a habit's reminder times: tells you if it is due and not done, then sets the next day's alarm. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getStringExtra(HABIT) ?: return pending.finish()
        val minute = intent.getIntExtra(MINUTE, -1)
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zone = ZoneId.systemDefault()
                val stored = container.habits.habits().first().firstOrNull { it.habit.id == id } ?: return@launch
                val now = LocalDateTime.now(zone)
                val auto = autoInputs(container.training, container.routines, container.journal, zone).first()
                val entries = container.habits.entries().first()[id].orEmpty()
                val log = HabitLogs.log(stored.habit, entries, auto.workoutDays, auto.sourceDays)
                if (Habits.shouldRemind(stored.habit, log, now.toLocalDate())) HabitNotifications.remind(context, stored, log)
                // a minute on, so an alarm that rings a little early still moves to the next day
                if (minute >= 0) HabitReminders(context, zone).scheduleNext(stored, LocalTime.of(minute / 60, minute % 60), now.plusMinutes(1))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val HABIT = "habit"
        const val MINUTE = "minute"
    }
}

/** After a reboot all alarms are gone: set them again. */
class HabitRemindersBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HabitReminders(context).sync(container.habits.habits().first())
                TaskReminders(context).sync(container.tasks.tasks().first())
            } finally {
                pending.finish()
            }
        }
    }
}

object HabitNotifications {
    private const val CHANNEL = "habits"

    fun remind(context: Context, habit: StoredHabit, log: HabitLog) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminds you at the time you set for a habit."
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // a number or minutes habit says how far it has got ("3 of 8")
        val so = log.entries[LocalDate.now()] ?: 0
        val text = if (habit.habit.kind == HabitKind.DONE || so == 0) "Time to start it." else "$so of ${habit.habit.target} so far."
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.habit.name)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this is quietly dropped.
        runCatching { manager.notify(habit.habit.id.hashCode(), notification) }
    }
}
