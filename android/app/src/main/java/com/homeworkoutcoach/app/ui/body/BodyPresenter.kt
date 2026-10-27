package com.homeworkoutcoach.app.ui.body

import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.ui.progress.ChartPoint
import com.homeworkoutcoach.app.ui.progress.ProgressPresenter.Companion.DAY_MS
import com.homeworkoutcoach.app.ui.progress.Range
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BodyUi(
    /** Null before the first weigh-in. */
    val latest: LatestUi?,
    val stats: List<StatUi>,
    val range: Range,
    val points: List<ChartPoint>,
    /** "Lowest 83.1 kg, highest 84.6 kg" in the chart range. */
    val rangeNote: String?,
    val history: List<HistoryRowUi>,
)

data class LatestUi(val weight: String, val whenText: String)

data class StatUi(val label: String, val value: String, val note: String? = null)

data class HistoryRowUi(val id: Long, val date: String, val weight: String, val detail: String)

/** Weight history and trends for the Body screen. Pure, so it is unit-tested directly. */
class BodyPresenter(private val zone: ZoneId = ZoneId.systemDefault()) {
    private val time = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale.forLanguageTag("en-IN"))

    fun present(weights: List<BodyWeight>, heightCm: Int?, range: Range, nowMillis: Long): BodyUi {
        val all = weights.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val last = all.lastOrNull()
        val cut = if (range.days > 0) nowMillis - range.days * DAY_MS else Long.MIN_VALUE
        val inRange = all.filter { it.dateMillis >= cut }

        return BodyUi(
            latest = last?.let { LatestUi("${weight(it.weightKg)} kg", time.format(Instant.ofEpochMilli(it.dateMillis).atZone(zone))) },
            stats = if (last == null) emptyList() else stats(all, last, heightCm, nowMillis),
            range = range,
            points = inRange.map { ChartPoint(it.dateMillis, it.weightKg) },
            rangeNote = if (inRange.size >= 2) {
                "Lowest ${weight(inRange.minOf { it.weightKg })} kg, highest ${weight(inRange.maxOf { it.weightKg })} kg"
            } else null,
            history = all.indices.reversed().map { i ->
                val w = all[i]
                val prev = all.getOrNull(i - 1)
                HistoryRowUi(
                    id = w.id,
                    date = time.format(Instant.ofEpochMilli(w.dateMillis).atZone(zone)),
                    weight = "${weight(w.weightKg)} kg",
                    detail = listOfNotNull(
                        if (w.source == WeightSource.SCALE) "Scale" else "Entered",
                        prev?.let { signed(w.weightKg - it.weightKg) + " kg" },
                    ).joinToString(" · "),
                )
            },
        )
    }

    private fun stats(all: List<BodyWeight>, last: BodyWeight, heightCm: Int?, now: Long): List<StatUi> {
        fun mean(from: Long, to: Long) = all.filter { it.dateMillis in (from + 1)..to }.map { it.weightKg }
            .takeIf { it.isNotEmpty() }?.average()
        val week = mean(now - 7 * DAY_MS, now)
        val prevWeek = mean(now - 14 * DAY_MS, now - 7 * DAY_MS)
        val out = mutableListOf<StatUi>()
        out += StatUi("7-day average", week?.let { "${weight(it)} kg" } ?: "No weigh-in this week")
        out += StatUi(
            "Change vs last week",
            if (week != null && prevWeek != null) "${signed(week - prevWeek)} kg" else "Not enough data",
            "Compares 7-day averages, which smooth out day-to-day swings.",
        )
        out += StatUi("4-week trend", trendPerWeek(all, now)?.let { "${signed(it)} kg a week" } ?: "Needs 3 weigh-ins over a week")
        out += if (heightCm != null && heightCm > 0) {
            val bmi = last.weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
            StatUi("BMI", round(bmi, 1).toPlainString(), "${bmiBand(bmi)}. BMI is a rough guide; it can't tell muscle from fat.")
        } else {
            StatUi("BMI", "Add your height", null)
        }
        out += StatUi("Since first weigh-in", "${signed(last.weightKg - all.first().weightKg)} kg", null)
        return out
    }

    /** Least-squares slope over the last 28 days, in kg per week. Needs 3 points spanning at least a week. */
    fun trendPerWeek(all: List<BodyWeight>, now: Long): Double? {
        val pts = all.filter { it.dateMillis > now - 28 * DAY_MS }
        if (pts.size < 3 || pts.last().dateMillis - pts.first().dateMillis < 7 * DAY_MS) return null
        val xs = pts.map { it.dateMillis / DAY_MS.toDouble() }
        val ys = pts.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den * 7
    }

    companion object {
        /** WHO adult bands. */
        fun bmiBand(bmi: Double) = when {
            bmi < 18.5 -> "Below the healthy range"
            bmi < 25 -> "In the healthy range"
            bmi < 30 -> "Above the healthy range"
            else -> "Well above the healthy range"
        }

        // valueOf goes through the decimal string, so 83.55 stays 83.55 rather than 83.5499...
        private fun round(v: Double, places: Int) = BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP)

        /** "83.5", "84.05", "84" */
        fun weight(kg: Double): String = round(kg, 2).stripTrailingZeros().toPlainString()

        /** "+0.6", "−1.2", "0" with one decimal. */
        fun signed(kg: Double): String {
            val r = round(kg, 1)
            return when {
                r.signum() > 0 -> "+" + r.toPlainString()
                r.signum() < 0 -> "−" + r.abs().toPlainString()
                else -> "0"
            }
        }    }
}
