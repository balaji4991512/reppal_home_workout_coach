package com.homeworkoutcoach.app.voice

import com.homeworkoutcoach.app.data.memory.InMemoryFocusRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.focus.FocusAlarms
import com.homeworkoutcoach.app.focus.FocusSessions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceActionsTest {
    private val settings = InMemorySettingsRepository()
    private val repo = InMemoryFocusRepository()
    private var clock = 1_000_000L
    private val scheduled = mutableListOf<Long>()
    private val alarms = object : FocusAlarms {
        override fun schedule(startMillis: Long, endMillis: Long) {
            scheduled += endMillis
        }

        override fun cancel() = Unit
    }
    private val actions = VoiceActions(settings, repo, FocusSessions(settings, repo, alarms) { clock }) { clock }

    private suspend fun running() = settings.settings.first().activeFocus

    @Test
    fun theAskedForSentenceStartsStudyForTwentyFiveMinutesWithItsAlarm() = runTest {
        assertEquals(VoiceOutcome.DONE, actions.heard("wick set a timer for twenty five minutes for study"))
        val run = running()!!
        assertEquals(25, run.plannedMinutes)
        assertEquals("Study", run.tag)
        assertEquals(listOf(clock + 25 * 60_000L), scheduled)
    }

    @Test
    fun speechNotForWickDoesNothing() = runTest {
        assertEquals(VoiceOutcome.IGNORED, actions.heard("the candle wick"))
        assertEquals(VoiceOutcome.IGNORED, actions.heard(""))
        assertNull(running())
    }

    @Test
    fun startingWhileOneRunsIsNotDone() = runTest {
        actions.heard("wick focus fifteen")
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick focus forty five"))
        assertEquals(15, running()!!.plannedMinutes)
    }

    @Test
    fun pauseResumeAndEnd() = runTest {
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick pause"))
        actions.heard("wick reading twenty minutes")
        clock += 5 * 60_000L
        assertEquals(VoiceOutcome.DONE, actions.heard("wick pause"))
        assertNotNull(running()!!.pausedAtMillis)
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick pause"))
        clock += 60_000L
        assertEquals(VoiceOutcome.DONE, actions.heard("wick resume"))
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick resume"))
        clock += 2 * 60_000L
        assertEquals(VoiceOutcome.DONE, actions.heard("wick stop"))
        assertNull(running())
        val saved = repo.sessions().first().single()
        assertEquals(7, saved.actualMinutes)
        assertEquals("Reading", saved.tag)
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick stop"))
    }

    @Test
    fun wickAloneWaitsFiveSecondsForTheCommand() = runTest {
        assertEquals(VoiceOutcome.LISTENING, actions.heard("wick"))
        clock += 3_000
        assertEquals(VoiceOutcome.DONE, actions.heard("study for an hour"))
        assertEquals(60, running()!!.plannedMinutes)
        // The window is used up.
        assertEquals(VoiceOutcome.IGNORED, actions.heard("pause"))
    }

    @Test
    fun theWindowCloses() = runTest {
        actions.heard("wick")
        clock += 6_000
        assertEquals(VoiceOutcome.IGNORED, actions.heard("focus forty five"))
        assertNull(running())
    }

    @Test
    fun notUnderstoodIsNotDone() = runTest {
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick hello"))
        assertEquals(VoiceOutcome.NOT_DONE, actions.heard("wick focus four hours"))
    }
}
