package com.homeworkoutcoach.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.ExerciseOutcome

/** Percent of planned volume, totals, then one row per exercise [sessCard]. Also used by Workout done and History. */
@Composable
fun SessionCard(card: SessionCardUi, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${card.percent}%",
                color = c.fg,
                style = AppTheme.type.tabular(TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                if (card.reached) "Target reached" else "Below target",
                color = if (card.reached) c.ok else c.mut,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.alignByBaseline(),
            )
        }
        Box(Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd)) {
            Box(
                Modifier.fillMaxWidth(card.percent.coerceIn(0, 100) / 100f).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(if (card.reached) c.ok else c.accent),
            )
        }
        Small(card.totals, Modifier.padding(bottom = 6.dp))
        card.rows.forEach { r ->
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = c.fg, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium))
                    Small(r.detail)
                }
                Text(
                    when (r.outcome) {
                        ExerciseOutcome.HIT -> "Hit"
                        ExerciseOutcome.SHORT -> "Short"
                        ExerciseOutcome.STOPPED -> "Stopped"
                    },
                    color = when (r.outcome) {
                        ExerciseOutcome.HIT -> c.ok
                        ExerciseOutcome.SHORT -> c.mut
                        ExerciseOutcome.STOPPED -> c.bad
                    },
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
