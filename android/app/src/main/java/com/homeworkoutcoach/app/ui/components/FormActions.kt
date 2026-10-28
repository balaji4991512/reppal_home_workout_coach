package com.homeworkoutcoach.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.theme.AppTheme

/**
 * What a form ends with under its Save button: a Cancel button, and optionally a second one beside it (Archive, for
 * example). Without a second button, Cancel is full width.
 */
@Composable
fun FormButtons(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
    extraLabel: String? = null,
    onExtra: () -> Unit = {},
    extraTint: Color = AppTheme.colors.accent,
) {
    if (extraLabel == null) {
        OutlineButton(cancelLabel, onCancel, modifier)
        return
    }
    Row(modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { OutlineButton(cancelLabel, onCancel, compact = true) }
        Box(Modifier.weight(1f)) { OutlineButton(extraLabel, onExtra, tint = extraTint, compact = true) }
    }
}

/**
 * A red Delete button, set well apart from the buttons above it. Tapping it asks [question] in a red-tinted box with
 * "Keep it" and "Delete", so nothing is deleted by one stray tap.
 */
@Composable
fun DeleteAction(
    label: String,
    question: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 36.dp,
) {
    val c = AppTheme.colors
    var asking by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        Column(modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bad.copy(alpha = 0.08f)).padding(14.dp)) {
            Text(question, color = c.fg, style = AppTheme.type.bodyStrong)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { OutlineButton("Keep it", { asking = false }, compact = true) }
                Box(Modifier.weight(1f)) { OutlineButton("Delete", { asking = false; onDelete() }, tint = c.bad, compact = true) }
            }
        }
    } else {
        OutlineButton(label, { asking = true }, modifier.padding(top = gap), tint = c.bad, compact = true)
    }
}

/** The page's main action in indigo, with an icon: "Start workout", "Weigh in", "Start". */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(c.accent)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, Modifier.size(20.dp), tint = c.onAccent)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
        }
        Text(text, color = c.onAccent, style = AppTheme.type.bodyStrong)
    }
}

/** A pill choice: filled indigo when selected, soft and white otherwise. For the choices in a coloured page. */
@Composable
fun AccentChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = AppTheme.colors
    Box(
        modifier.heightIn(min = if (small) 38.dp else 44.dp).clip(RoundedCornerShape(99.dp))
            .background(if (selected) c.accent else c.soft)
            .border(androidx.compose.foundation.BorderStroke(1.dp, if (selected) c.accent else c.bd), RoundedCornerShape(99.dp))
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 18.dp, vertical = 6.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text, color = if (selected) c.onAccent else c.fg,
            style = AppTheme.type.label.copy(fontSize = if (small) 14.sp else 15.sp, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium),
        )
    }
}
