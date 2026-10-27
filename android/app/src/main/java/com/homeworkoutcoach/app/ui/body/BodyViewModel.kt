package com.homeworkoutcoach.app.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeworkoutcoach.app.data.BodyRepository
import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.scale.ScaleSource
import com.homeworkoutcoach.app.scale.ScanProblem
import com.homeworkoutcoach.app.scale.WeighInState
import com.homeworkoutcoach.app.scale.WeighInTracker
import com.homeworkoutcoach.app.ui.progress.Range
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The weigh-in card. */
sealed interface WeighInUi {
    data object Idle : WeighInUi

    /** Listening for the scale. [weightKg] is the live number once someone is on it. */
    data class Listening(val weightKg: Double?) : WeighInUi

    data class Saved(val weightKg: Double, val id: Long) : WeighInUi

    data class Problem(val message: String, val bluetoothOff: Boolean = false) : WeighInUi
}

class BodyViewModel(
    private val body: BodyRepository,
    private val settings: SettingsRepository,
    private val presenter: BodyPresenter,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 90_000,
) : ViewModel() {
    private val range = MutableStateFlow(Range.QUARTER)

    val ui: StateFlow<BodyUi?> = combine(body.weights(), settings.settings, range) { w, s, r ->
        presenter.present(w, s.heightCm, r, now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val heightCm: StateFlow<Int?> =
        settings.settings.map { it.heightCm }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _weighIn = MutableStateFlow<WeighInUi>(WeighInUi.Idle)
    val weighIn: StateFlow<WeighInUi> = _weighIn

    private var source: ScaleSource? = null
    private var timeout: Job? = null

    fun setRange(r: Range) {
        range.value = r
    }

    /** Start listening. The screen checks permissions first. */
    fun startWeighIn(scale: ScaleSource) {
        stopListening()
        source = scale
        _weighIn.value = WeighInUi.Listening(null)
        viewModelScope.launch {
            val tracker = WeighInTracker(settings.settings.first().scaleId)
            scale.start(
                onReading = { r ->
                    when (val st = tracker.accept(r)) {
                        WeighInState.Waiting -> Unit
                        is WeighInState.Live -> _weighIn.value = WeighInUi.Listening(st.weightKg)
                        is WeighInState.Settled -> save(st)
                    }
                },
                onProblem = { problem(it) },
            )
            if (_weighIn.value !is WeighInUi.Listening) return@launch
            timeout = launch {
                delay(timeoutMs)
                if (_weighIn.value is WeighInUi.Listening) {
                    stopListening()
                    _weighIn.value = WeighInUi.Problem(
                        "No reading from the scale. Step on it barefoot and wait for the number to flash, " +
                            "with Bluetooth on, then try again.",
                    )
                }
            }
        }
    }

    private fun save(st: WeighInState.Settled) {
        stopListening()
        viewModelScope.launch {
            val id = body.add(BodyWeight(dateMillis = now(), weightKg = st.weightKg, source = WeightSource.SCALE))
            settings.update { if (it.scaleId == null) it.copy(scaleId = st.scaleId) else it }
            _weighIn.value = WeighInUi.Saved(st.weightKg, id)
        }
    }

    fun problem(p: ScanProblem) {
        stopListening()
        _weighIn.value = when (p) {
            ScanProblem.NO_BLUETOOTH -> WeighInUi.Problem("This phone can't read Bluetooth scales. Enter your weight by hand instead.")
            ScanProblem.BLUETOOTH_OFF -> WeighInUi.Problem("Bluetooth is off. Turn it on, then tap Weigh in again.", bluetoothOff = true)
            ScanProblem.NO_PERMISSION -> WeighInUi.Problem("Allow Nearby devices so the app can hear your scale. You can change this in Settings.")
            ScanProblem.FAILED -> WeighInUi.Problem("Couldn’t listen for the scale. Try again.")
        }
    }

    fun cancelWeighIn() {
        stopListening()
        _weighIn.value = WeighInUi.Idle
    }

    /** Undo a weigh-in just saved. */
    fun undo() {
        val saved = _weighIn.value as? WeighInUi.Saved ?: return
        viewModelScope.launch {
            body.delete(saved.id)
            _weighIn.value = WeighInUi.Idle
        }
    }

    /** Typed weight. Returns an error message, or null when saved. */
    fun addManual(text: String): String? {
        val kg = text.trim().replace(',', '.').toDoubleOrNull()
        if (kg == null || kg < 20 || kg > 300) return "Enter a weight between 20 and 300 kg."
        viewModelScope.launch {
            val id = body.add(BodyWeight(dateMillis = now(), weightKg = kg, source = WeightSource.MANUAL))
            _weighIn.value = WeighInUi.Saved(kg, id)
        }
        return null
    }

    /** Returns an error message, or null when saved. */
    fun setHeight(text: String): String? {
        val cm = text.trim().toIntOrNull()
        if (cm == null || cm < 100 || cm > 250) return "Enter your height in cm, between 100 and 250."
        viewModelScope.launch { settings.update { it.copy(heightCm = cm) } }
        return null
    }

    fun delete(id: Long) {
        viewModelScope.launch { body.delete(id) }
    }

    private fun stopListening() {
        timeout?.cancel()
        timeout = null
        source?.stop()
        source = null
    }

    override fun onCleared() = stopListening()
}
