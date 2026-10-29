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
import com.homeworkoutcoach.app.ui.habits.HabitFormat
import com.homeworkoutcoach.engine.Task
import com.homeworkoutcoach.engine.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm for each of the next few timed tasks: at its start, or at its deadline when it has no start. [sync] makes the
 * alarms match the tasks (adds, moves, cancels), so it is safe to call whenever tasks change, at app start and after a reboot.
 */
class TaskReminders(private val context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val prefs get() = context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE)

    private fun intent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context, "task:$taskId".hashCode(),
        Intent(context, TaskReminderReceiver::class.java).setData(android.net.Uri.parse("wick://task/$taskId"))
            .putExtra(TaskReminderReceiver.TASK, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun sync(tasks: List<Task>) {
        val upcoming = Tasks.upcomingReminders(tasks, LocalDateTime.now(zone))
        val before = prefs.getStringSet(SCHEDULED, emptySet()).orEmpty()
        (before - upcoming.map { it.first.id }.toSet()).forEach { alarms.cancel(intent(it)) }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        upcoming.forEach { (task, at) ->
            val millis = at.atZone(zone).toInstant().toEpochMilli()
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(task.id))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(task.id))
        }
        prefs.edit().putStringSet(SCHEDULED, upcoming.map { it.first.id }.toSet()).apply()
    }

    private companion object {
        const val SCHEDULED = "scheduled"
    }
}

/** Rings for a task: tells you unless it has been done or removed since, then tops up the next alarms. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getStringExtra(TASK) ?: return pending.finish()
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = container.tasks.tasks().first()
                val task = all.firstOrNull { it.id == id }
                if (task != null && !task.done) TaskNotifications.remind(context, task)
                // alarms for tasks further out are set a few at a time, so refill now that one has gone
                TaskReminders(context).sync(all.filter { it.id != id })
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TASK = "task"
    }
}

object TaskNotifications {
    private const val CHANNEL = "tasks"

    fun remind(context: Context, task: Task) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminds you when a task with a time is due to start."
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            task.time != null && task.endTime != null -> "Time to start, until ${HabitFormat.clock(task.endTime!!)}."
            task.time != null -> "Time to start it."
            else -> "Due now."
        }
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this is quietly dropped.
        runCatching { manager.notify("task:${task.id}".hashCode(), notification) }
    }
}
