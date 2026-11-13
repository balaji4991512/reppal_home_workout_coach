package com.homeworkoutcoach.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * The app's notification channels, each with the phone's notification sound and vibration. Android fixes a channel's
 * sound when the channel is first made, so these ids are new ones; the channels they replace are removed.
 */
object Channels {
    const val FOCUS = "focus_sound"
    const val HABITS = "habits_sound"
    const val TASKS = "tasks_sound"

    private val replaced = mapOf(FOCUS to "focus", HABITS to "habits", TASKS to "tasks")

    /**
     * Makes channel [id] if it is not there yet. [alarm] plays it on the alarm volume, which rings even with the phone
     * on vibrate, as a timer should; the others follow the notification volume and the phone's sound mode.
     */
    fun ensure(context: Context, id: String, name: String, description: String, alarm: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)
        replaced[id]?.let { old -> if (manager.getNotificationChannel(old) != null) manager.deleteNotificationChannel(old) }
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attributes(alarm))
                enableVibration(true)
            },
        )
    }

    fun attributes(alarm: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}

/** The phone's notification sound played once, for when something finishes with the app on screen. */
object Chime {
    fun play(context: Context) {
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.apply {
                audioAttributes = Channels.attributes(alarm = true)
                play()
            }
        }
    }
}
