package com.homeworkoutcoach.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why listening stopped without a usable phrase. */
enum class VoiceError {
    /** No speech recognition service on this phone. */
    NOT_AVAILABLE,

    /** Microphone permission refused. */
    NO_PERMISSION,

    /** Nothing heard, or nothing that matched speech. */
    NOT_HEARD,

    /** Recognizer, network or audio failure. */
    FAILED,
}

/** Callbacks for one listening session. Called on the main thread. */
interface SpeechCallbacks {
    fun onListening()
    fun onPartial(text: String)
    fun onResult(text: String)
    fun onError(error: VoiceError)
}

/**
 * Android speech recognition for set notes, in en-IN. Uses the on-device recognizer when the phone has one
 * (Android 12+), and falls back to the phone's default recognizer, asking it to stay offline where it can.
 * Create, use and destroy on the main thread.
 */
class SpeechInput(private val context: Context, private val language: String = "en-IN") {
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) || onDeviceAvailable()

    private fun onDeviceAvailable() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(callbacks: SpeechCallbacks) {
        if (!available) return callbacks.onError(VoiceError.NOT_AVAILABLE)
        startWith(preferOnDevice = onDeviceAvailable(), callbacks)
    }

    private fun startWith(preferOnDevice: Boolean, callbacks: SpeechCallbacks) {
        destroy()
        onDevice = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val r = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = callbacks.onListening()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let(callbacks::onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = best(results)
                if (text.isNullOrBlank()) callbacks.onError(VoiceError.NOT_HEARD) else callbacks.onResult(text)
            }

            override fun onError(error: Int) {
                // The on-device model may not have en-IN installed: retry with the default recognizer.
                if (onDevice && error in LANGUAGE_ERRORS && SpeechRecognizer.isRecognitionAvailable(context)) {
                    startWith(preferOnDevice = false, callbacks)
                    return
                }
                callbacks.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NOT_HEARD
                        else -> VoiceError.FAILED
                    },
                )
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            },
        )
    }

    /** Stop listening; whatever was heard so far still arrives as a result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private companion object {
        // Only reported by Android 12+ recognizers.
        val LANGUAGE_ERRORS = setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
    }
}
