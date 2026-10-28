package com.homeworkoutcoach.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.PillTabs
import com.homeworkoutcoach.app.ui.components.ProgressRing
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.OverviewRange

private val TABS = listOf(
    OverviewRange.WEEK to "Week",
    OverviewRange.MONTH to "Month",
    OverviewRange.TWELVE_WEEKS to "12W",
    OverviewRange.YEAR to "Year",
)

/** Range tabs, the habit completion ring, the bars, and the workout and focus counts. */
@Composable
fun OverviewSection(ui: OverviewUi, onRange: (OverviewRange) -> Unit) {
    PillTabs(TABS.map { it.second }, TABS.indexOfFirst { it.first == ui.range }) { onRange(TABS[it].first) }
    CompletionCard(ui)
    BarsCard(ui)
    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CountCard("WORKOUTS", "${ui.workouts}", ui.workoutsBefore, Modifier.weight(1f))
        CountCard("FOCUS TIME", ui.focusTime, ui.focusBefore, Modifier.weight(1f))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppTheme.colors.soft).padding(16.dp)) { content() }
}

@Composable
private fun CompletionCard(ui: OverviewUi) {
    val c = AppTheme.colors
    Card(Modifier.padding(top = 14.dp)) {
        if (!ui.hasHabits) {
            Body("Habits", strong = true)
            Small("Add a habit and how your days go shows here.")
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(ui.fraction, size = 96.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${ui.percent}%", color = c.fg, style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)))
                    Text("COMPLETE", color = c.mut, style = AppTheme.type.small.copy(fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.06.em))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("HABIT DAYS DONE", color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.06.em))
                Text("${ui.habitsDone}", color = c.fg, style = AppTheme.type.tabular(AppTheme.type.h1))
                ui.trend?.let { Text(it, color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.Medium)) }
                Small(ui.habitsOutOf)
            }
        }
    }
}

@Composable
private fun BarsCard(ui: OverviewUi) {
    val c = AppTheme.colors
    Card(Modifier.padding(top = 12.dp)) {
        Text(ui.barsTitle, color = c.fg, style = AppTheme.type.h2)
        if (!ui.hasHabits) {
            Small("Nothing to show yet.", Modifier.padding(top = 4.dp))
            return@Card
        }
        val bars = ui.bars
        Row(
            Modifier.padding(top = 18.dp).fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(if (bars.size > 7) 5.dp else 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, bar ->
                val p = bar.percent
                val showLabels = bars.size <= 7 || bar.current
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${bar.label}: ${p?.let { "$it percent" } ?: "nothing due"}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (p != null && showLabels) {
                        Text("$p%", color = if (bar.current) c.fg else c.mut, style = AppTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp), maxLines = 1, softWrap = false)
                    }
                    // A bar is at least a sliver, so a quiet day is still seen as a day.
                    val h = if (p == null) 3.dp else (4 + p * 92 / 100).dp
                    Box(
                        Modifier.padding(top = 4.dp).fillMaxWidth().height(h).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                when {
                                    p == null || p == 0 -> c.bd
                                    bar.current -> c.accent
                                    else -> c.accentSoft
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 7) 5.dp else 10.dp)) {
            bars.forEachIndexed { i, bar ->
                val show = bars.size <= 7 || bar.label.length <= 1 || (bars.size - 1 - i) % 3 == 0
                // A label may be wider than its bar, so it spills over evenly instead of being cut.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (show) bar.label else "",
                        Modifier.wrapContentWidth(unbounded = true), color = if (bar.current) c.fg else c.mut, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                        style = AppTheme.type.small.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (bar.current) FontWeight.SemiBold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

@Composable
private fun CountCard(label: String, value: String, before: String, modifier: Modifier) {
    val c = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(c.soft).padding(16.dp)) {
        Text(label, color = c.mut, style = AppTheme.type.small.copy(fontSize = 11.sp, letterSpacing = 0.06.em))
        Text(
            buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value) } },
            Modifier.padding(top = 4.dp), color = c.fg, style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 22.sp, lineHeight = 28.sp)),
        )
        Small(before)
    }
}
