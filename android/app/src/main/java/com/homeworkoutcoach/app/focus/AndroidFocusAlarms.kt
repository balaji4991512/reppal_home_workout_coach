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
import kotlinx.coroutines.launch

/**
 * The alarm for the end of a focus session. It is not an exact alarm (those need a special permission), so the phone may
 * deliver it a little late when it is dozing; the countdown on screen is always exact.
 */
class AndroidFocusAlarms(private val context: Context) : FocusAlarms {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun intent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun schedule(endMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, intent())
    }

    override fun cancel() {
        alarms.cancel(intent())
    }

    private companion object {
        const val REQUEST = 4101
    }
}

/** Runs when the alarm goes off: settles the session (saving it) and tells the user it is finished. */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as HomeWorkoutCoachApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.focusSessions.settleIfFinished()?.let { FocusNotifications.finished(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}

object FocusNotifications {
    private const val CHANNEL = "focus"
    private const val ID = 4102

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
