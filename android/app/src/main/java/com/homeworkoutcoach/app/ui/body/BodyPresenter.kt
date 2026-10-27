package com.homeworkoutcoach.app.ui.body

import com.homeworkoutcoach.app.data.BodyWeight
import com.homeworkoutcoach.app.data.WeightSource
import com.homeworkoutcoach.app.ui.Copy
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

data class LatestUi(
    val weight: String,
    val whenText: String,
    /** "−0.4 kg since last weigh-in", or null for the first one. */
    val sinceLast: String?,
    /** When the latest day has several weigh-ins: which one the trends use. */
    val dayNote: String? = null,
)

data class StatUi(val label: String, val value: String, val note: String? = null)

data class HistoryRowUi(val id: Long, val date: String, val weight: String, val detail: String)

/** Weight history and trends for the Body screen. Pure, so it is unit-tested directly. */
class BodyPresenter(private val zone: ZoneId = ZoneId.systemDefault()) {
    private val time = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale.forLanguageTag("en-IN"))
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("en-IN"))

    fun present(weights: List<BodyWeight>, heightCm: Int?, range: Range, nowMillis: Long): BodyUi {
        val all = weights.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val last = all.lastOrNull()
        // Every weigh-in is kept, but the chart and trends use one per day: the first, as weight drifts
        // 1-2 kg over a day with food, water and exercise, and the same time of day compares best.
        val byDay = all.groupBy { localDate(it.dateMillis) }
        val daily = byDay.values.map { it.first() }
        val counted = byDay.filterValues { it.size > 1 }.values.map { it.first().id }.toSet()
        val cut = if (range.days > 0) nowMillis - range.days * DAY_MS else Long.MIN_VALUE
        val inRange = daily.filter { it.dateMillis >= cut }

        return BodyUi(
            latest = last?.let {
                val sameDay = byDay.getValue(localDate(it.dateMillis))
                val first = sameDay.first()
                val dayWord = if (localDate(it.dateMillis) == localDate(nowMillis)) "today" else "that day"
                LatestUi(
                    weight = "${weight(it.weightKg)} kg",
                    whenText = time.format(Instant.ofEpochMilli(it.dateMillis).atZone(zone)),
                    sinceLast = all.getOrNull(all.size - 2)?.let { prev -> "${signed(it.weightKg - prev.weightKg)} kg since last weigh-in" },
                    dayNote = if (sameDay.size > 1) {
                        "${Copy.count(sameDay.size, "weigh-in")} $dayWord. Trends use the first: " +
                            "${weight(first.weightKg)} kg at ${clock.format(Instant.ofEpochMilli(first.dateMillis).atZone(zone))}."
                    } else null,
                )
            },
            stats = if (daily.isEmpty()) emptyList() else stats(daily, heightCm, nowMillis),
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
                        if (w.id in counted) "Used for trends" else null,
                    ).joinToString(" · "),
                )
            },
        )
    }

    private fun localDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** [daily] holds one weigh-in per day, oldest first. */
    private fun stats(daily: List<BodyWeight>, heightCm: Int?, now: Long): List<StatUi> {
        val last = daily.last()
        fun mean(from: Long, to: Long) = daily.filter { it.dateMillis in (from + 1)..to }.map { it.weightKg }
            .takeIf { it.isNotEmpty() }?.average()
        val weekDays = daily.count { it.dateMillis in (now - 7 * DAY_MS + 1)..now }
        val week = mean(now - 7 * DAY_MS, now)
        val prevWeek = mean(now - 14 * DAY_MS, now - 7 * DAY_MS)
        val trend = trendPerWeek(daily, now)
        // Four short tiles. A missing figure shows a dash and says what it needs.
        return listOf(
            StatUi(
                "7-day average",
                week?.let { "${weight(round(it, 1).toDouble())} kg" } ?: DASH,
                if (week != null) Copy.count(weekDays, "day") else "None this week",
            ),
            StatUi(
                "vs last week",
                if (week != null && prevWeek != null) "${signed(week - prevWeek)} kg" else DASH,
                if (week != null && prevWeek != null) "7-day averages" else "Needs 2 weeks",
            ),
            StatUi(
                "Trend",
                trend?.let { "${signed(it)} kg/wk" } ?: DASH,
                if (trend != null) "Last 4 weeks" else "Needs 3 days",
            ),
            if (heightCm != null && heightCm > 0) {
                val bmi = last.weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
                StatUi("BMI", round(bmi, 1).toPlainString(), bmiBand(bmi))
            } else {
                StatUi("BMI", DASH, "Add your height")
            },
        )
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
        const val DASH = "—"

        /** WHO adult bands. BMI is a rough guide: it can't tell muscle from fat. */
        fun bmiBand(bmi: Double) = when {
            bmi < 18.5 -> "Below healthy range"
            bmi < 25 -> "Healthy range"
            bmi < 30 -> "Above healthy range"
            else -> "Well above healthy range"
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
