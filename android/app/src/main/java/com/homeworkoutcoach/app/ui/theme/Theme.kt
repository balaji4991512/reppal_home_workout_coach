package com.homeworkoutcoach.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.data.ThemeMode

/** Warm cream and white cards, with ink for controls and one indigo accent for progress. */
@Immutable
data class Tokens(
    val bg: Color,
    val fg: Color,
    val mut: Color,
    val bd: Color,
    val acc: Color,
    val on: Color,
    val accs: Color,
    val ok: Color,
    val oks: Color,
    val bad: Color,
    val soft: Color,
    val scrim: Color,
    /** The one colour accent: progress rings and bars. */
    val accent: Color,
    val accentSoft: Color,
    /** Text and icons on [accent]. */
    val onAccent: Color,
    /** A warm second colour that goes with [accent] in sweeps, and soft tints for cards (peach, mint, rose). */
    val coral: Color,
    val tintPeach: Color,
    val tintMint: Color,
    val tintRose: Color,
)

val LightTokens = Tokens(
    bg = Color(0xFFFCF7EE), fg = Color(0xFF16150F), mut = Color(0xFF6E685B), bd = Color(0xFFEBE2D0),
    acc = Color(0xFF16150F), on = Color(0xFFFFFFFF), accs = Color(0xFFF3ECDC), ok = Color(0xFF157A55),
    oks = Color(0xFFE6F3EA), bad = Color(0xFFC4372C), soft = Color(0xFFFFFFFF), scrim = Color(0x66000000),
    accent = Color(0xFF5B50E6), accentSoft = Color(0xFFE9E6FB), onAccent = Color(0xFFFFFFFF),
    coral = Color(0xFFF0735A), tintPeach = Color(0xFFFDE8D8), tintMint = Color(0xFFDDF3E6), tintRose = Color(0xFFFBE0E8),
)

val DarkTokens = Tokens(
    bg = Color(0xFF0F0F10), fg = Color(0xFFF2F2F0), mut = Color(0xFF9A9A96), bd = Color(0xFF2A2A2C),
    acc = Color(0xFFF2F2F0), on = Color(0xFF0F0F10), accs = Color(0xFF1C1C1E), ok = Color(0xFF4CC79A),
    oks = Color(0xFF14291F), bad = Color(0xFFFF8A7D), soft = Color(0xFF1A1A1C), scrim = Color(0x99000000),
    accent = Color(0xFF9C94FF), accentSoft = Color(0xFF26234D), onAccent = Color(0xFF0F0F10),
    coral = Color(0xFFFF9A85), tintPeach = Color(0xFF3A2A22), tintMint = Color(0xFF1E3328), tintRose = Color(0xFF3A222B),
)

/** Type scale from the prototype: h1 30, h2 17, body 16, muted 14. Numbers use tabular figures. */
@Immutable
data class Type(
    val h1: TextStyle = TextStyle(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    val h2: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    val small: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val label: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val big: TextStyle = TextStyle(fontSize = 76.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em),
) {
    fun tabular(style: TextStyle) = style.copy(fontFeatureSettings = "tnum")
}

private val LocalTokens = staticCompositionLocalOf { LightTokens }
private val LocalType = staticCompositionLocalOf { Type() }

object AppTheme {
    val colors: Tokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
    val type: Type
        @Composable @ReadOnlyComposable get() = LocalType.current
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val t = if (dark) DarkTokens else LightTokens
    // Material components (text selection, ripples) pick up the same ink.
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = t.acc, onPrimary = t.on, background = t.bg, onBackground = t.fg,
        surface = t.bg, onSurface = t.fg, onSurfaceVariant = t.mut, outline = t.bd, error = t.bad,
        // The date and time pickers take their tints from these; keep them in the app's black and white.
        primaryContainer = t.acc, onPrimaryContainer = t.on,
        secondaryContainer = t.accs, onSecondaryContainer = t.fg,
        tertiaryContainer = t.acc, onTertiaryContainer = t.on,
        surfaceVariant = t.accs,
        surfaceContainer = t.bg, surfaceContainerHigh = t.bg, surfaceContainerHighest = t.accs,
    )
    CompositionLocalProvider(LocalTokens provides t, LocalType provides Type()) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
