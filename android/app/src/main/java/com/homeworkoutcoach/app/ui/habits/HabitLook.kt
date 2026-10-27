package com.homeworkoutcoach.app.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.theme.DarkTokens

/** How the stored icon and colour keys look. Every habit colour is a soft hue that reads in light and dark. */
object HabitLook {
    fun icon(key: String): ImageVector = when (key) {
        "drop" -> AppIcons.Drop
        "moon" -> AppIcons.Moon
        "sun" -> AppIcons.Sun
        "book" -> AppIcons.Book
        "pen" -> AppIcons.Pen
        "dumbbell" -> AppIcons.Dumbbell
        "walk" -> AppIcons.Walk
        "lotus" -> AppIcons.Lotus
        "heart" -> AppIcons.Heart
        "star" -> AppIcons.Star
        "list" -> AppIcons.List
        "bolt" -> AppIcons.Bolt
        else -> AppIcons.Check
    }

    private val light = mapOf(
        "blue" to Color(0xFF2F6FDE), "green" to Color(0xFF2E8B57), "orange" to Color(0xFFD9731A), "red" to Color(0xFFD0453B),
        "purple" to Color(0xFF7C4DDB), "teal" to Color(0xFF1C8C94), "amber" to Color(0xFFB88A00), "pink" to Color(0xFFD1478B),
    )
    private val dark = mapOf(
        "blue" to Color(0xFF7BA7F5), "green" to Color(0xFF5FCB92), "orange" to Color(0xFFF2A15C), "red" to Color(0xFFF28A80),
        "purple" to Color(0xFFB394F0), "teal" to Color(0xFF5CCBD2), "amber" to Color(0xFFE9C146), "pink" to Color(0xFFF08DBB),
    )

    @Composable
    fun color(key: String): Color {
        val isDark = AppTheme.colors.bg == DarkTokens.bg
        return (if (isDark) dark else light)[key] ?: AppTheme.colors.fg
    }
}

/**
 * The round mark for a habit: its icon inside a ring that fills as the day gets done, and a solid disc when done.
 */
@Composable
fun HabitBadge(
    icon: String,
    color: String,
    fraction: Float,
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val c = AppTheme.colors
    val tint = HabitLook.color(color)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            if (done) {
                drawCircle(tint)
            } else {
                drawCircle(c.bd, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(
                        tint, startAngle = -90f, sweepAngle = 360f * fraction.coerceAtMost(1f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        Icon(HabitLook.icon(icon), null, Modifier.padding(size / 4).size(size / 2), tint = if (done) c.on else tint)
    }
}
