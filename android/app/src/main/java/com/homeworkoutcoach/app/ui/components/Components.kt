package com.homeworkoutcoach.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** Scrolling page: max 600dp wide, 20dp side gutter, room at the bottom. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 72.dp)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
fun H1(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, color = AppTheme.colors.fg, style = AppTheme.type.h1)

/** Section heading with the prototype's 32dp top margin. */
@Composable
fun H2(text: String, modifier: Modifier = Modifier, top: Dp = 32.dp) =
    Text(text, modifier.padding(top = top, bottom = 6.dp), color = AppTheme.colors.fg, style = AppTheme.type.h2)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.fg, strong: Boolean = false) =
    Text(text, modifier, color = color, style = if (strong) AppTheme.type.bodyStrong else AppTheme.type.body)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(vertical = 6.dp), color = AppTheme.colors.mut, style = AppTheme.type.small)

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = if (small) 38.dp else 44.dp)
            .clip(shape)
            .background(if (selected) c.acc else c.bg)
            .border(1.dp, if (selected) c.acc else c.bd, shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.on else c.fg,
            style = AppTheme.type.label.copy(fontSize = if (small) 14.sp else 15.sp),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    /** Smaller, with no top margin, for buttons side by side. */
    compact: Boolean = false,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .padding(top = if (compact) 0.dp else 14.dp)
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 52.dp)
            .clip(shape)
            .background(if (alt) Color.Transparent else c.acc)
            .then(if (alt) Modifier.border(BorderStroke(1.dp, c.bd), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(if (compact) 10.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (alt) c.fg else c.on,
            style = if (compact) AppTheme.type.bodyStrong.copy(fontSize = 15.sp) else AppTheme.type.bodyStrong,
        )
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    label: String = placeholder,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = AppTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        textStyle = AppTheme.type.body.copy(color = c.fg),
        cursorBrush = SolidColor(c.fg),
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                Modifier
                    .border(1.dp, c.bd, RoundedCornerShape(12.dp))
                    .padding(13.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = c.mut, style = AppTheme.type.body)
                inner()
            }
        },
    )
}

/** A hairline-separated row: label (and optional small line) on the left, value on the right. */
@Composable
fun LineRow(
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), content = left)
            right()
        }
        if (divider) Hairline()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.colors.bd)

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.colors.mut) =
    Text(text, modifier, color = color, style = AppTheme.type.small)

/** Thin progress segments: [done] of [total] filled. */
@Composable
fun Segments(total: Int, done: Int, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < done) c.fg else c.bd),
            )
        }
    }
}

@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(99.dp)).background(background).padding(horizontal = 9.dp, vertical = 3.dp),
        color = foreground,
        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/** Dumbbell mark in a rounded square (prototype LOGO, 40x40 grid). */
@Composable
fun Logo(size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Canvas(modifier.size(size).semantics { contentDescription = "Logo" }) {
        val u = this.size.width / 40f
        drawRoundRect(c.acc, cornerRadius = CornerRadius(11 * u))
        fun bar(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(c.on, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
        bar(4f, 16f, 3.5f, 8f, 1.5f)
        bar(8.5f, 13f, 4.5f, 14f, 2f)
        bar(13f, 18.6f, 14f, 2.8f, 1.4f)
        bar(27f, 10f, 4.5f, 20f, 2f)
        bar(32.5f, 7f, 3.5f, 26f, 1.5f)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

/** A quiet underlined link with a full-size touch target, such as "Privacy". */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            color = AppTheme.colors.mut,
            style = AppTheme.type.small.copy(textDecoration = TextDecoration.Underline),
        )
    }
}
