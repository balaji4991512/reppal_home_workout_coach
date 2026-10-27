package com.homeworkoutcoach.app.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import com.homeworkoutcoach.app.ui.components.Chip

/** "Mic" chip beside the note box. Asks for the microphone the first time; tap again to stop. */
@Composable
fun MicButton(
    listening: Boolean,
    onListening: () -> Unit,
    onPartial: (String) -> Unit,
    onResult: (String) -> Unit,
    onError: (VoiceError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val speech = remember { SpeechInput(context.applicationContext) }
    DisposableEffect(speech) { onDispose { speech.destroy() } }

    val listeningCb by rememberUpdatedState(onListening)
    val partialCb by rememberUpdatedState(onPartial)
    val resultCb by rememberUpdatedState(onResult)
    val errorCb by rememberUpdatedState(onError)
    val callbacks = remember {
        object : SpeechCallbacks {
            override fun onListening() = listeningCb()
            override fun onPartial(text: String) = partialCb(text)
            override fun onResult(text: String) = resultCb(text)
            override fun onError(error: VoiceError) = errorCb(error)
        }
    }
    // Show "Listening…" and Stop straight away; the recognizer can take a moment to open the mic.
    fun begin() {
        listeningCb()
        speech.start(callbacks)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else errorCb(VoiceError.NO_PERMISSION)
    }

    Chip(
        text = if (listening) "Stop" else "Mic",
        selected = listening,
        onClick = {
            when {
                listening -> speech.stop()
                !speech.available -> errorCb(VoiceError.NOT_AVAILABLE)
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> begin()
                else -> askPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        modifier = modifier.semantics { contentDescription = if (listening) "Stop listening" else "Use microphone" },
    )
}
