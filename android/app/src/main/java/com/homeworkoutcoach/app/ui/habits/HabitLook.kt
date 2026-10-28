package com.homeworkoutcoach.app.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
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
    /** The emoji shown for a stored icon key. Keys never change, so saved habits keep their look. */
    fun emoji(key: String): String = emojis[key] ?: "✅"

    private val emojis = mapOf(
        "check" to "✅", "drop" to "💧", "moon" to "🌙", "sun" to "☀️", "book" to "📖", "pen" to "✍️",
        "dumbbell" to "🏋️", "walk" to "🚶", "lotus" to "🧘", "heart" to "❤️", "star" to "⭐", "list" to "📋", "bolt" to "⚡",
        "shower" to "🚿", "run" to "🏃", "apple" to "🍎", "sleep" to "😴", "tooth" to "🪥", "pill" to "💊",
        "broom" to "🧹", "brain" to "🧠", "money" to "💰", "music" to "🎵", "plant" to "🌱", "phone" to "📵",
    )

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

/** The habit's emoji on a soft disc of its colour. It is only a picture: the tick circle beside it is what is tapped. */
@Composable
fun HabitBadge(
    icon: String,
    color: String,
    fraction: Float,
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val tint = HabitLook.color(color)
    Box(modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(HabitLook.emoji(icon), fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

/**
 * The circle you tap to mark a habit done: empty to start, a green check when done, and for a number or time habit a ring
 * that fills with a "+" that opens the amount box. [partial] is how far a number or time habit has got (0 to 1).
 */
@Composable
fun HabitTick(done: Boolean, partial: Float, amountHabit: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val fill by animateFloatAsState(
        if (done) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "habitTick",
    )
    // Touch target of 52dp around a 34dp circle, so it is easy to hit and easy to see.
    Box(
        modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(34.dp)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawCircle(c.mut.copy(alpha = 0.55f), style = Stroke(stroke), radius = this.size.width / 2 - inset)
            if (!done && partial > 0f) {
                drawArc(c.ok, -90f, 360f * partial.coerceAtMost(1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (fill > 0f) drawCircle(c.ok, radius = this.size.width / 2 * fill)
        }
        when {
            fill > 0.15f -> Icon(
                AppIcons.Check, null,
                Modifier.size(20.dp).graphicsLayer { scaleX = fill; scaleY = fill; alpha = fill.coerceIn(0f, 1f) },
                tint = c.bg,
            )
            amountHabit -> Icon(AppIcons.Plus, null, Modifier.size(16.dp), tint = c.mut)
        }
    }
}
