package com.homeworkoutcoach.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
