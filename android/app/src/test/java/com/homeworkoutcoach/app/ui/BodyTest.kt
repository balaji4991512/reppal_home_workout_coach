package com.homeworkoutcoach.app.ui

import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.data.memory.InMemoryBodyRepository
import com.homeworkoutcoach.app.data.memory.InMemorySettingsRepository
import com.homeworkoutcoach.app.scale.Flexscale
import com.homeworkoutcoach.app.scale.ScaleReading
import com.homeworkoutcoach.app.scale.ScaleSource
import com.homeworkoutcoach.app.scale.ScanProblem
import com.homeworkoutcoach.app.ui.body.BodyPresenter
import com.homeworkoutcoach.app.ui.body.BodyViewModel
import com.homeworkoutcoach.app.ui.body.WeighInUi
import com.homeworkoutcoach.app.ui.progress.ProgressPresenter.Companion.DAY_MS
import com.homeworkoutcoach.app.ui.progress.Range
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class BodyPresenterTest {
    private val presenter = BodyPresenter(ZoneOffset.UTC)
    private fun at(iso: String) = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = at("2026-10-27T09:00:00")

    private fun w(id: Long, iso: String, kg: Double, source: WeightSource = WeightSource.SCALE) =
        BodyWeight(id, at(iso), kg, source)

    private val weights = listOf(
        w(1, "2026-10-10T07:00:00", 85.0),
        w(2, "2026-10-14T07:00:00", 84.6),
        w(3, "2026-10-17T07:00:00", 84.4, WeightSource.MANUAL),
        w(4, "2026-10-21T07:00:00", 84.2),
        w(5, "2026-10-24T07:00:00", 83.9),
        w(6, "2026-10-27T08:47:50", 83.5),
    )

    @Test
    fun latestStatsAndHistory() {
        val ui = presenter.present(weights, heightCm = 175, range = Range.MONTH, nowMillis = now)
        assertEquals("83.5 kg", ui.latest!!.weight)
        assertEquals("Tue, 27 Oct · 08:47", ui.latest!!.whenText)
        val stats = ui.stats.associate { it.label to it.value }
        // this week: 84.2, 83.9, 83.5 -> 83.87; last week: 84.6, 84.4 -> 84.5
        assertEquals("83.87 kg", stats["7-day average"])
        assertEquals("−0.6 kg", stats["Change vs last week"])
        assertEquals("27.3", stats["BMI"])
        assertEquals("−1.5 kg", stats["Since first weigh-in"])
        assertTrue(stats.getValue("4-week trend").startsWith("−0."))

        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), ui.history.map { it.id })
        assertEquals("Scale · −0.4 kg", ui.history[0].detail)
        assertEquals("Entered · −0.2 kg", ui.history[3].detail)
        assertEquals("Scale", ui.history.last().detail)
        assertEquals("Lowest 83.5 kg, highest 85 kg", ui.rangeNote)
        assertEquals(6, ui.points.size)
        // 21, 24 and 27 Oct fall within the last 7 days
        assertEquals(3, presenter.present(weights, null, Range.WEEK, now).points.size)
    }

    @Test
    fun emptyAndNoHeight() {
        val empty = presenter.present(emptyList(), null, Range.QUARTER, now)
        assertNull(empty.latest)
        assertTrue(empty.stats.isEmpty() && empty.history.isEmpty())

        val one = presenter.present(listOf(w(1, "2026-10-27T07:00:00", 84.05)), null, Range.QUARTER, now)
        val stats = one.stats.associate { it.label to it.value }
        assertEquals("Add your height", stats["BMI"])
        assertEquals("Not enough data", stats["Change vs last week"])
        assertEquals("Needs 3 weigh-ins over a week", stats["4-week trend"])
        assertEquals("84.05 kg", one.latest!!.weight)
    }

    @Test
    fun trendIsTheFittedSlopePerWeek() {
        // exactly -0.1 kg a day over two weeks -> -0.7 kg a week
        val line = (0..14).map { d -> BodyWeight(d.toLong(), now - (14 - d) * DAY_MS, 85.0 - 0.1 * d, WeightSource.SCALE) }
        assertEquals(-0.7, presenter.trendPerWeek(line, now)!!, 1e-9)
    }

    @Test
    fun formats() {
        assertEquals("83.55", BodyPresenter.weight(83.55))
        assertEquals("80", BodyPresenter.weight(80.0))
        assertEquals("+0.6", BodyPresenter.signed(0.55))
        assertEquals("0", BodyPresenter.signed(0.04))
        assertEquals("In the healthy range", BodyPresenter.bmiBand(22.0))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyViewModelTest {
    private val body = InMemoryBodyRepository()
    private val settings = InMemorySettingsRepository(UserSettings(started = true, guest = true))

    /** Replays the real capture through the view model, like the radio would. */
    private class Replay(private val packets: List<ScaleReading>, private val problem: ScanProblem? = null) : ScaleSource {
        var stopped = false
        override fun start(onReading: (ScaleReading) -> Unit, onProblem: (ScanProblem) -> Unit) {
            problem?.let { return onProblem(it) }
            packets.forEach { if (!stopped) onReading(it) }
        }
        override fun stop() {
            stopped = true
        }
    }

    private val capture: List<ScaleReading> by lazy {
        javaClass.classLoader!!.getResource("flexscale_capture.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line -> Flexscale.parse(line.split(' ')[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()) }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BodyViewModel(body, settings, BodyPresenter(ZoneOffset.UTC), now = { 1_000 })

    @Test
    fun weighInSavesTheSettledReadingAndRemembersTheScale() = runTest {
        val vm = vm()
        val scale = Replay(capture)
        vm.startWeighIn(scale)
        assertTrue(scale.stopped)
        val saved = vm.weighIn.value as WeighInUi.Saved
        assertEquals(83.50, saved.weightKg, 0.0)
        assertEquals(listOf(BodyWeight(1, 1_000, 83.50, WeightSource.SCALE)), body.weights().first())
        assertEquals("ED:67:39:DA:91:0B", settings.settings.first().scaleId)

        vm.undo()
        assertTrue(body.weights().first().isEmpty())
        assertEquals(WeighInUi.Idle, vm.weighIn.value)
    }

    @Test
    fun problemsAndManualEntry() = runTest {
        val vm = vm()
        vm.startWeighIn(Replay(emptyList(), ScanProblem.BLUETOOTH_OFF))
        val p = vm.weighIn.value as WeighInUi.Problem
        assertTrue(p.bluetoothOff)

        assertEquals("Enter a weight between 20 and 300 kg.", vm.addManual("8"))
        assertNull(vm.addManual("84,3"))
        assertEquals(84.3, body.weights().first().single().weightKg, 0.0)
        assertEquals(WeightSource.MANUAL, body.weights().first().single().source)

        assertEquals("Enter your height in cm, between 100 and 250.", vm.setHeight("5.9"))
        assertNull(vm.setHeight("175"))
        assertEquals(175, settings.settings.first().heightCm)
    }

    @Test
    fun noReadingTimesOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = BodyViewModel(body, settings, BodyPresenter(ZoneOffset.UTC), now = { 1_000 }, timeoutMs = 60_000)
        val scale = Replay(emptyList())
        vm.startWeighIn(scale)
        advanceTimeBy(1_000)
        assertTrue(vm.weighIn.value is WeighInUi.Listening)
        advanceTimeBy(60_000)
        assertTrue(vm.weighIn.value is WeighInUi.Problem)
        assertTrue(scale.stopped)
    }
}
