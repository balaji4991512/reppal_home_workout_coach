package com.homeworkoutcoach.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private var generation = 0
    /** The recognizer has opened the microphone (or sent words). */
    private var ready = false
    private var active: SpeechCallbacks? = null
    private val handler = Handler(Looper.getMainLooper())

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
        // Callbacks from a recognizer that has since been replaced are ignored.
        val mine = ++generation
        fun current() = mine == generation
        ready = false
        active = callbacks
        log("start onDevice=$onDevice")

        fun fallBackOrFail(error: VoiceError) {
            if (onDevice && SpeechRecognizer.isRecognitionAvailable(context)) {
                log("falling back to the default recognizer")
                startWith(preferOnDevice = false, callbacks)
            } else {
                destroy()
                callbacks.onError(error)
            }
        }

        // Some on-device recognizers never answer (for example without an en-IN model): don't wait forever.
        handler.postDelayed({
            if (current() && !ready) {
                log("no response within ${START_TIMEOUT_MS} ms")
                fallBackOrFail(VoiceError.FAILED)
            }
        }, START_TIMEOUT_MS)

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!current()) return
                ready = true
                log("ready")
                callbacks.onListening()
            }
            override fun onBeginningOfSpeech() = log("speech began")
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = log("speech ended")
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                if (!current()) return
                ready = true
                best(partialResults)?.let(callbacks::onPartial)
            }

            override fun onResults(results: Bundle?) {
                if (!current()) return
                val text = best(results)
                log("result: ${text?.length ?: 0} chars")
                destroy()
                if (text.isNullOrBlank()) callbacks.onError(VoiceError.NOT_HEARD) else callbacks.onResult(text)
            }

            override fun onError(error: Int) {
                if (!current()) return
                log("error $error (onDevice=$onDevice, ready=$ready)")
                // The on-device recognizer may lack the en-IN pack, or fail before listening: ask for the pack
                // (so next time works offline) and use the default recognizer online for now.
                if (onDevice && (error in LANGUAGE_ERRORS || !ready)) {
                    if (error in LANGUAGE_ERRORS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        runCatching { r.triggerModelDownload(intent(preferOffline = true)) }
                            .onSuccess { log("asked for the $language pack") }
                    }
                    fallBackOrFail(VoiceError.FAILED)
                    return
                }
                // Anything else ends this attempt; close the recognizer so a late "mic open" can't leave it hanging.
                destroy()
                callbacks.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NO_PERMISSION
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NOT_HEARD
                        else -> VoiceError.FAILED
                    },
                )
            }
        })
        // The default recognizer must not be told to stay offline: without the en-IN pack it would fail too.
        r.startListening(intent(preferOffline = onDevice))
    }

    private fun intent(preferOffline: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Give people time to finish a phrase like "ten reps, hard, wrist felt tight".
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    /** Stop listening; whatever was heard so far still arrives as a result. Before the mic opened, just cancel. */
    fun stop() {
        if (ready) {
            recognizer?.stopListening()
        } else {
            val cb = active
            destroy()
            cb?.onError(VoiceError.NOT_HEARD)
        }
    }

    fun destroy() {
        generation++
        recognizer?.destroy()
        recognizer = null
    }

    // Off unless enabled with: adb shell setprop log.tag.Voice DEBUG. Never logs what was said.
    private fun log(msg: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, msg)
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private companion object {
        const val TAG = "Voice"
        const val START_TIMEOUT_MS = 5_000L

        // Only reported by Android 12+ recognizers.
        val LANGUAGE_ERRORS = setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
    }
}
