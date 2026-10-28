package com.homeworkoutcoach.app.focus

import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.engine.Focus
import com.homeworkoutcoach.engine.FocusSession
import kotlinx.coroutines.flow.first

/** Sets and cancels the alarm that goes off when a focus session ends. */
interface FocusAlarms {
    fun schedule(endMillis: Long)

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
    private fun idOf(run: ActiveFocus) = "focus-${run.startMillis}"

    /** Starts a session. Returns false if one is already running or the length is not allowed. */
    suspend fun start(minutes: Int, taskTitle: String?): Boolean {
        if (Focus.problem(minutes) != null) return false
        if (settings.settings.first().activeFocus != null) return false
        val run = ActiveFocus(now(), minutes, taskTitle?.trim()?.takeIf { it.isNotEmpty() })
        settings.update { it.copy(activeFocus = run) }
        alarms.schedule(run.toRun().endMillis)
        return true
    }

    /** Ends the running session now. Returns what was saved, or null if nothing was running or it was too short. */
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
}
