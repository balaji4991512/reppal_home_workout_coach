package com.homeworkoutcoach.app.focus

import android.app.AlarmManager
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
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The alarm for the end of a focus session. It is an exact alarm that also goes off while the phone dozes, so a timer
 * set for 25 minutes rings at 25 minutes. Android 12 and up only allow that with a permission (USE_EXACT_ALARM, which
 * timer apps get); without it the alarm falls back to an inexact one that the phone may deliver a little late.
 */
class AndroidFocusAlarms(private val context: Context) : FocusAlarms {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun intent(startMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, FocusAlarmReceiver::class.java).putExtra(FocusAlarmReceiver.START, startMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun schedule(startMillis: Long, endMillis: Long) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, intent(startMillis))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, intent(startMillis))
        }
    }

    override fun cancel() {
        // Cancelling matches on the intent, not its extras, so any start time will do.
        alarms.cancel(intent(0))
    }

    private companion object {
        const val REQUEST = 4101
    }
}

/**
 * Runs when the alarm goes off: settles the session (saving it) if nobody has yet, and tells the user it is finished
 * unless they are looking at the timer. The app's own countdown may well have settled it first, so the session is
 * looked up by its start time rather than taken from settling.
 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val start = intent.getLongExtra(START, 0)
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.focusSessions.settleIfFinished()
                val session = container.focus.sessions().first().firstOrNull { it.id == FocusSessions.idFor(start) }
                if (session != null && !FocusNotifications.screenVisible) FocusNotifications.finished(context, session)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val START = "start"
    }
}

object FocusNotifications {
    private const val CHANNEL = "focus"
    private const val ID = 4102

    /** True while the Focus screen is on screen: the finished card is enough then, no notification. */
    @Volatile
    var screenVisible = false

    fun finished(context: Context, session: FocusSession) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Focus timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Tells you when a focus session is finished."
                    enableVibration(true)
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = listOfNotNull(session.taskTitle, "${session.actualMinutes} minutes").joinToString(" · ")
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus session finished")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this is quietly dropped; the finished session is still saved.
        runCatching { manager.notify(ID, notification) }
    }
}
