package com.homeworkoutcoach.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.homeworkoutcoach.app.HomeWorkoutCoachApp
import com.homeworkoutcoach.app.MainActivity
import com.homeworkoutcoach.app.R
import com.homeworkoutcoach.app.data.ActiveFocus
import com.homeworkoutcoach.engine.VoiceCommands
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Listens for "Wick" while switched on: a microphone service, so it keeps hearing with the screen off and the phone
 * locked. Speech is recognised on the phone by Vosk, listening only for the command words and the focus tags. Its
 * notification says it is listening, and shows the focus session while one runs.
 */
class WickListener : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as HomeWorkoutCoachApp).container
    private val actions by lazy { VoiceActions(container.settings, container.focus, container.focusSessions) }
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var words: List<String> = emptyList()
    private var problem: String? = null
    private val tones by lazy { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 80) }.getOrNull() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startInForeground(lastRun)) return START_NOT_STICKY
        if (model == null && problem == null) load()
        if (scopeStarted) return START_STICKY
        scopeStarted = true
        // Off in the settings stops the service; a running session shows in the notification.
        scope.launch {
            container.settings.settings.map { it.listenForWick to it.activeFocus }.distinctUntilChanged().collect { (on, run) ->
                if (!on) stopSelf() else notify(run)
            }
        }
        // A new focus tag is listened for as soon as it is used.
        scope.launch {
            container.focus.sessions().map { actions.tags() }.distinctUntilChanged().collect { if (model != null) listen() }
        }
        return START_STICKY
    }

    private var scopeStarted = false
    private var lastRun: ActiveFocus? = null

    /** Android 14 and up refuse a microphone service the app did not start while open (after a restart, say). */
    private fun startInForeground(run: ActiveFocus?): Boolean = try {
        val n = notification(run)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(ID, n)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not listen from the background", e)
        WickNotice.openToListen(this)
        stopSelf()
        false
    }

    private fun load() {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            this, MODEL_ASSET, "model",
            { m ->
                model = m
                listen()
            },
            { e ->
                Log.w(TAG, "No voice model", e)
                problem = "The voice model is missing from this build."
                notify(lastRun)
            },
        )
    }

    /** (Re)starts listening with the words and the current tags. */
    private fun listen() {
        val m = model ?: return
        scope.launch {
            val tagWords = actions.tags().flatMap { it.lowercase().split(Regex("[^a-z]+")) }.filter { it.isNotEmpty() }
            val next = (VoiceCommands.words + tagWords).distinct()
            if (next == words && speech != null) return@launch
            words = next
            speech?.let { it.stop(); it.shutdown() }
            speech = try {
                val grammar = JSONArray(words + "[unk]").toString()
                SpeechService(Recognizer(m, RATE, grammar), RATE).also { it.startListening(listener) }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start listening", e)
                problem = "The microphone could not be opened."
                null
            }
            notify(lastRun)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResult(hypothesis: String?) = heard(hypothesis)
        override fun onFinalResult(hypothesis: String?) = heard(hypothesis)
        override fun onPartialResult(hypothesis: String?) = Unit
        override fun onError(exception: Exception?) {
            Log.w(TAG, "Listening failed", exception)
        }

        override fun onTimeout() = Unit
    }

    private fun heard(hypothesis: String?) {
        val text = hypothesis?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }.orEmpty()
        if (text.isBlank()) return
        // Only in the phone's own log, for checking what was heard; nothing is kept.
        Log.d(TAG, "Heard: $text")
        scope.launch {
            when (actions.heard(text)) {
                VoiceOutcome.IGNORED -> Unit
                VoiceOutcome.LISTENING -> tone(ToneGenerator.TONE_PROP_BEEP, 150)
                VoiceOutcome.DONE -> tone(ToneGenerator.TONE_PROP_ACK, 300)
                VoiceOutcome.NOT_DONE -> tone(ToneGenerator.TONE_PROP_NACK, 400)
            }
        }
    }

    private fun tone(type: Int, ms: Int) {
        runCatching { tones?.startTone(type, ms) }
    }

    private fun notify(run: ActiveFocus?) {
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(ID, notification(run)) }
    }

    private fun notification(run: ActiveFocus?): Notification {
        lastRun = run
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = problem ?: if (run == null) "Listening for \"Wick\"" else focusLine(run)
        val text = if (problem != null) "Voice commands are off." else "Say \"Wick, focus 25 minutes for study\""
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun focusLine(run: ActiveFocus): String {
        val what = listOfNotNull("Focus", run.tag).joinToString(" · ")
        if (run.pausedAtMillis != null) return "$what · paused"
        val end = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(run.toRun().endMillis))
        return "$what · ends $end"
    }

    override fun onDestroy() {
        scope.cancel()
        speech?.let { it.stop(); it.shutdown() }
        speech = null
        model?.close()
        tones?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WickListener"
        private const val ID = 4201
        internal const val CHANNEL = "wick_listening"
        private const val MODEL_ASSET = "model-en-in"
        private const val RATE = 16000f

        /** Quiet: the listening notification and the notice after a restart make no sound. */
        internal fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Listening for Wick", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while Wick listens for voice commands."
                    setShowBadge(false)
                },
            )
        }

        fun allowed(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Starts listening if it is switched on and the microphone is allowed. Call while the app is open. */
        fun startIfOn(context: Context, on: Boolean) {
            if (!on || !allowed(context)) return
            runCatching { context.startForegroundService(Intent(context, WickListener::class.java)) }
                .onFailure { Log.w(TAG, "Could not start listening", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WickListener::class.java))
        }
    }
}

/** The notice after a restart: Android needs Wick opened before it may listen again. */
object WickNotice {
    private const val ID = 4202

    fun openToListen(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        WickListener.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, WickListener.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tap to listen for \"Wick\" again")
            .setContentText("Android stopped voice commands. Opening Wick starts them again.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(ID, n) }
    }
}
