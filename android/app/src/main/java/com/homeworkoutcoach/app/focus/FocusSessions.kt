package com.homeworkoutcoach.app.focus

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.engine.Focus
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.flow.first

/** Sets and cancels the alarm that goes off when a focus session ends. */
interface FocusAlarms {
    /** [startMillis] identifies the session, so whoever the alarm wakes can find it again. */
    fun schedule(startMillis: Long, endMillis: Long)

    fun cancel()
}

/**
 * Starting, stopping and settling the focus timer. The running timer lives in the settings, so it counts on with the app
 * closed; whoever notices it has run out (the alarm, or the screen on next opening) settles it, and a session's id comes
 * from its start time, so settling twice saves it once.
 */
class FocusSessions(
    private val settings: SettingsRepository,
    private val sessions: FocusRepository,
    private val alarms: FocusAlarms,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun idOf(run: ActiveFocus) = idFor(run.startMillis)

    /** Starts a session. Returns false if one is already running or the length is not allowed. */
    suspend fun start(minutes: Int, taskTitle: String?, tag: String? = null): Boolean {
        if (Focus.problem(minutes) != null) return false
        if (settings.settings.first().activeFocus != null) return false
        val known = Focus.tags(sessions.sessions().first())
        val run = ActiveFocus(now(), minutes, taskTitle?.trim()?.takeIf { it.isNotEmpty() }, Focus.tagFor(tag, known))
        settings.update { it.copy(activeFocus = run) }
        alarms.schedule(run.startMillis, run.toRun().endMillis)
        return true
    }

    /** Pauses the running session: the countdown stands still and the alarm is called off. False if nothing is running. */
    suspend fun pause(): Boolean {
        val run = settings.settings.first().activeFocus ?: return false
        if (run.pausedAtMillis != null) return true
        // A session that ran out before the pause is over, not paused.
        if (Focus.isFinished(run.toRun(), now())) return false
        val paused = ActiveFocus.of(Focus.pause(run.toRun(), now()))
        settings.update { if (it.activeFocus == run) it.copy(activeFocus = paused) else it }
        alarms.cancel()
        return true
    }

    /** Lets a paused session count down again, its end (and the alarm) pushed back by the pause. False if nothing is paused. */
    suspend fun resume(): Boolean {
        val run = settings.settings.first().activeFocus ?: return false
        if (run.pausedAtMillis == null) return false
        val going = Focus.resume(run.toRun(), now())
        settings.update { if (it.activeFocus == run) it.copy(activeFocus = ActiveFocus.of(going)) else it }
        alarms.schedule(going.startMillis, going.endMillis)
        return true
    }

    /** Ends the running (or paused) session now. Returns what was saved, or null if nothing was running or it was too short. */
    suspend fun stop(): FocusSession? = end(requireFinished = false)

    /** If the running session has run out, saves it and clears the timer. Returns what was saved. */
    suspend fun settleIfFinished(): FocusSession? = end(requireFinished = true)

    private suspend fun end(requireFinished: Boolean): FocusSession? {
        val run = settings.settings.first().activeFocus ?: return null
        val at = now()
        if (requireFinished && !Focus.isFinished(run.toRun(), at)) return null
        val session = Focus.finish(run.toRun(), at, idOf(run))
        session?.let { sessions.save(it) }
        settings.update { if (it.activeFocus == run) it.copy(activeFocus = null) else it }
        alarms.cancel()
        return session
    }

    companion object {
        /** The id of the session that started at [startMillis]. */
        fun idFor(startMillis: Long) = "focus-$startMillis"
    }
}
