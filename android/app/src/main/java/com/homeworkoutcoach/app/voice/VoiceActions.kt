package com.homeworkoutcoach.app.voice

import com.homeworkoutcoach.app.data.FocusRepository
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.focus.FocusSessions
import com.homeworkoutcoach.engine.Focus
import com.homeworkoutcoach.engine.VoiceCommand
import com.homeworkoutcoach.engine.VoiceCommands
import kotlinx.coroutines.flow.first

/** What the listener plays back after a sentence. */
enum class VoiceOutcome {
    /** Not meant for Wick: nothing plays. */
    IGNORED,

    /** "Wick" alone: a beep, then the command may follow. */
    LISTENING,
    DONE,
    NOT_DONE,
}

/**
 * Acts on what the listener heard, through the same focus sessions the Focus screen uses, so the alarm, the saved
 * history and the screen all follow. A bare "Wick" opens a short window in which the next sentence counts as the command.
 */
class VoiceActions(
    private val settings: SettingsRepository,
    private val focus: FocusRepository,
    private val sessions: FocusSessions,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var waitingUntil = 0L

    /** The focus tags that can be named. */
    suspend fun tags(): List<String> = Focus.tags(focus.sessions().first())

    suspend fun heard(text: String): VoiceOutcome {
        val waiting = now() < waitingUntil
        val command = VoiceCommands.parse(text, tags())
            ?: if (waiting && text.isNotBlank()) VoiceCommands.parse("${VoiceCommands.WAKE} $text", tags()) else null
        if (command == null) return VoiceOutcome.IGNORED
        waitingUntil = 0
        if (command == VoiceCommand.WakeOnly) {
            waitingUntil = now() + WAIT_MS
            return VoiceOutcome.LISTENING
        }
        return if (act(command)) VoiceOutcome.DONE else VoiceOutcome.NOT_DONE
    }

    private suspend fun act(command: VoiceCommand): Boolean = when (command) {
        is VoiceCommand.StartFocus -> sessions.start(command.minutes, null, command.tag)
        VoiceCommand.Pause -> running()?.let { it.pausedAtMillis == null && sessions.pause() } ?: false
        VoiceCommand.Resume -> sessions.resume()
        VoiceCommand.End -> running()?.let { sessions.stop(); true } ?: false
        VoiceCommand.WakeOnly, VoiceCommand.Unclear -> false
    }

    private suspend fun running() = settings.settings.first().activeFocus

    companion object {
        const val WAIT_MS = 5_000L
    }
}
