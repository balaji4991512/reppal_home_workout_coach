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
import com.homeworkoutcoach.engine.Habits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm per habit that has a reminder time. Each alarm rings once, for the next time the habit's reminder comes up;
 * when it rings the receiver reschedules it for the day after. [sync] makes the alarms match the habits: it adds,
 * moves and cancels, so it is safe to call whenever habits change, at app start and after a reboot.
 */
class HabitReminders(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = context.getSharedPreferences("habit_reminders", Context.MODE_PRIVATE)

    private fun intent(habitId: String): PendingIntent = PendingIntent.getBroadcast(
        context, habitId.hashCode(),
        Intent(context, HabitReminderReceiver::class.java).setData(android.net.Uri.parse("wick://habit/$habitId"))
            .putExtra(HabitReminderReceiver.HABIT, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun sync(habits: List<StoredHabit>) {
        val wanted = habits.filter { it.habit.reminder != null && !it.habit.archived }
        val before = prefs.getStringSet(SCHEDULED, emptySet()).orEmpty()
        (before - wanted.map { it.habit.id }.toSet()).forEach { alarms.cancel(intent(it)) }
        wanted.forEach { scheduleNext(it) }
        prefs.edit().putStringSet(SCHEDULED, wanted.map { it.habit.id }.toSet()).apply()
    }

    /** The alarm for the habit's next reminder after [after] (now by default). */
    fun scheduleNext(habit: StoredHabit, after: LocalDateTime = LocalDateTime.now(zone)) {
        val time = habit.habit.reminder ?: return
        val at = Habits.nextReminder(time, after).atZone(zone).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(habit.habit.id))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(habit.habit.id))
    }

    private companion object {
        const val SCHEDULED = "scheduled"
    }
}

/** Rings at a habit's reminder time: tells you if it is due and not done, then sets the next day's alarm. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getStringExtra(HABIT) ?: return pending.finish()
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zone = ZoneId.systemDefault()
                val stored = container.habits.habits().first().firstOrNull { it.habit.id == id } ?: return@launch
                val now = LocalDateTime.now(zone)
                val auto = autoInputs(container.training, container.routines, container.journal, zone).first()
                val entries = container.habits.entries().first()[id].orEmpty()
                val log = HabitLogs.log(stored.habit, entries, auto.workoutDays, auto.sourceDays)
                if (Habits.shouldRemind(stored.habit, log, now.toLocalDate())) HabitNotifications.remind(context, stored)
                // a minute on, so a alarm that rings a little early still moves to the next day
                HabitReminders(context, zone).scheduleNext(stored, now.plusMinutes(1))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val HABIT = "habit"
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
            } finally {
                pending.finish()
            }
        }
    }
}

object HabitNotifications {
    private const val CHANNEL = "habits"

    fun remind(context: Context, habit: StoredHabit) {
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
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.habit.name)
            .setContentText("Time to start it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this is quietly dropped.
        runCatching { manager.notify(habit.habit.id.hashCode(), notification) }
    }
}
