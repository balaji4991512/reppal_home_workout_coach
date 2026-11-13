package com.homeworkoutcoach.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun everyChannelRingsAndVibratesAndTheOldOnesGo() {
        // the channels made by earlier versions, without a sound of their own
        for (old in listOf("focus", "habits", "tasks")) manager.createNotificationChannel(NotificationChannel(old, old, NotificationManager.IMPORTANCE_HIGH))
        Channels.ensure(context, Channels.FOCUS, "Focus timer", "d", alarm = true)
        Channels.ensure(context, Channels.HABITS, "Habit reminders", "d")
        Channels.ensure(context, Channels.TASKS, "Task reminders", "d")
        for (old in listOf("focus", "habits", "tasks")) assertNull(old, manager.getNotificationChannel(old))
        for (id in listOf(Channels.FOCUS, Channels.HABITS, Channels.TASKS)) {
            val ch = manager.getNotificationChannel(id)
            assertNotNull(id, ch.sound)
            assertTrue(id, ch.shouldVibrate())
            assertEquals(id, NotificationManager.IMPORTANCE_HIGH, ch.importance)
        }
        // the end of a focus session rings on the alarm volume; reminders on the notification volume
        assertEquals(AudioAttributes.USAGE_ALARM, manager.getNotificationChannel(Channels.FOCUS).audioAttributes.usage)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, manager.getNotificationChannel(Channels.HABITS).audioAttributes.usage)
    }

    @Test
    fun makingAChannelAgainKeepsWhatThePersonChanged() {
        Channels.ensure(context, Channels.HABITS, "Habit reminders", "d")
        val ch = manager.getNotificationChannel(Channels.HABITS)
        ch.importance = NotificationManager.IMPORTANCE_LOW
        manager.createNotificationChannel(ch)
        Channels.ensure(context, Channels.HABITS, "Habit reminders", "d")
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(Channels.HABITS).importance)
    }
}
