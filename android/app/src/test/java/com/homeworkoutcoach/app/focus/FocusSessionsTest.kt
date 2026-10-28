package com.homeworkoutcoach.app.focus

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAlarms : FocusAlarms {
    var scheduledAt: Long? = null
    var cancels = 0

    override fun schedule(endMillis: Long) {
        scheduledAt = endMillis
    }

    override fun cancel() {
        scheduledAt = null
        cancels++
    }
}

class FocusSessionsTest {
    private val min = 60_000L
    private val t0 = 1_793_000_000_000L
    private var clock = t0
    private val settings = InMemorySettingsRepository()
    private val repo = InMemoryFocusRepository()
    private val alarms = FakeAlarms()
    private val service = FocusSessions(settings, repo, alarms, now = { clock })

    private suspend fun active() = settings.settings.first().activeFocus

    @Test
    fun startingSavesTheTimerAndSetsTheAlarmForTheEnd() = runTest {
        assertTrue(service.start(25, "  Write the report "))
        assertEquals(ActiveFocus(t0, 25, "Write the report"), active())
        assertEquals(t0 + 25 * min, alarms.scheduledAt)
    }

    @Test
    fun aBlankTaskIsNoTask() = runTest {
        service.start(10, "   ")
        assertNull(active()!!.taskTitle)
    }

    @Test
    fun onlyOneTimerRunsAtATimeAndTheLengthIsChecked() = runTest {
        assertFalse(service.start(0, null))
        assertFalse(service.start(181, null))
        assertNull(active())
        assertNull(alarms.scheduledAt)
        assertTrue(service.start(25, null))
        clock += min
        assertFalse(service.start(45, null)) // already running
        assertEquals(25, active()!!.plannedMinutes)
        assertEquals(t0 + 25 * min, alarms.scheduledAt) // the alarm is not moved
    }

    @Test
    fun stoppingEarlySavesTheWholeMinutesAndClearsTheTimer() = runTest {
        service.start(25, "Task")
        clock += 7 * min + 40_000
        val s = service.stop()!!
        assertEquals(7, s.actualMinutes)
        assertFalse(s.completed)
        assertEquals("Task", s.taskTitle)
        assertEquals(listOf(s), repo.sessions().first())
        assertNull(active())
        assertNull(alarms.scheduledAt)
    }

    @Test
    fun stoppingWithinTheFirstMinuteSavesNothingButStillClears() = runTest {
        service.start(25, null)
        clock += 30_000
        assertNull(service.stop())
        assertTrue(repo.sessions().first().isEmpty())
        assertNull(active())
        assertNull(alarms.scheduledAt)
    }

    @Test
    fun settlingBeforeTheEndDoesNothing() = runTest {
        service.start(25, null)
        clock += 24 * min + 59_000
        assertNull(service.settleIfFinished())
        assertEquals(25, active()!!.plannedMinutes) // still running
        assertEquals(t0 + 25 * min, alarms.scheduledAt)
    }

    @Test
    fun settlingAfterTheEndSavesTheFullSessionOnce() = runTest {
        service.start(25, "Task")
        clock += 25 * min
        val s = service.settleIfFinished()!!
        assertEquals(25, s.actualMinutes)
        assertTrue(s.completed)
        assertNull(active())
        // the alarm and the screen can both notice: the second one finds nothing to do
        assertNull(service.settleIfFinished())
        assertEquals(1, repo.sessions().first().size)
    }

    @Test
    fun settlingLateStillCountsOnlyThePlannedTime() = runTest {
        service.start(25, null)
        clock += 10 * 60 * min // the phone was off all day
        assertEquals(25, service.settleIfFinished()!!.actualMinutes)
    }

    @Test
    fun twoSettlesOfTheSameRunSaveOneRowEvenIfRacing() = runTest {
        service.start(25, null)
        clock += 25 * min
        // simulate the other side having already saved it but not yet cleared the timer
        repo.save(com.homeworkoutcoach.engine.FocusSession("focus-$t0", t0, 25, 25, true))
        service.settleIfFinished()
        assertEquals(1, repo.sessions().first().size)
    }

    @Test
    fun withNothingRunningStopAndSettleDoNothing() = runTest {
        assertNull(service.stop())
        assertNull(service.settleIfFinished())
        assertTrue(repo.sessions().first().isEmpty())
    }
}
