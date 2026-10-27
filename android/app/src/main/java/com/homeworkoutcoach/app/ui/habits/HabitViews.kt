package com.homeworkoutcoach.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.HabitKind

/**
 * The small box for changing one day's amount: Done / Not done for a tick habit, steps and a reset for a number or
 * a time. [onSet] gets the new amount for the day.
 */
@Composable
fun AmountPanel(
    kind: HabitKind,
    target: Int,
    amount: Int,
    onSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(12.dp)) {
        if (kind == HabitKind.DONE) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Chip("Done", amount >= 1, { onSet(1) })
                Chip("Not done", amount < 1, { onSet(0) })
            }
            return@Column
        }
        val steps = if (kind == HabitKind.COUNT) listOf(1, 2, 5) else listOf(5, 10, 15, 30)
        val unit = if (kind == HabitKind.TIME) " min" else ""
        Small(HabitFormat.progress(kind, amount, target))
        Chips {
            steps.forEach { Chip("+$it$unit", false, { onSet(amount + it) }, small = true) }
            Chip("Reached the target", amount >= target, { onSet(target) }, small = true)
            Chip("−${steps.first()}$unit", false, { onSet((amount - steps.first()).coerceAtLeast(0)) }, small = true)
            Chip("Reset", false, { onSet(0) }, small = true)
        }
    }
}
