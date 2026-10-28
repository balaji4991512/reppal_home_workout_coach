package com.homeworkoutcoach.app.ui.character

import com.homeworkoutcoach.app.ui.components.DeleteAction
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Pill
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Reflections

/** The Character screen: what you actually did this week, one focus for it, and a short reflection. */
@Composable
fun CharacterScreen(vm: CharacterViewModel = viewModel(factory = Factories.Character)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    var wentWell by rememberSaveable(s.reflection.wentWell) { mutableStateOf(s.reflection.wentWell) }
    var differently by rememberSaveable(s.reflection.differently) { mutableStateOf(s.reflection.differently) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }

    Page {
        H1("Character")
        Muted("This week, ${s.weekLabel}")

        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft).padding(16.dp)) {
            Body(s.summary)
        }

        H2("This week's focus", top = 28.dp)
        Chips {
            s.cards.forEach { card -> Chip(card.label, card.isFocus, { vm.chooseFocus(card.quality) }, small = true) }
        }
        if (s.focus != s.suggested) Small("Suggested for you: ${s.suggested.label.lowercase()}.")

        H2("What you did", top = 28.dp)
        s.cards.forEach { card ->
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body(card.label, Modifier.weight(1f), strong = true)
                    if (card.isFocus) Pill("This week's focus", c.fg, c.bg)
                }
                Small(card.line, Modifier.padding(top = 4.dp), color = c.fg)
                card.trend?.let { Small(it, Modifier.padding(top = 4.dp)) }
            }
        }

        H2("End of the week", top = 32.dp)
        Small(Reflections.WENT_WELL, Modifier.padding(top = 4.dp, bottom = 6.dp))
        Field(
            wentWell, { wentWell = it; problem = null; saved = false }, "A line or two", minLines = 2, label = Reflections.WENT_WELL,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Small(Reflections.DIFFERENTLY, Modifier.padding(top = 16.dp, bottom = 6.dp))
        Field(
            differently, { differently = it; problem = null; saved = false }, "One small change", minLines = 2, label = Reflections.DIFFERENTLY,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        problem?.let { Text(it, Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.small) }
        PrimaryButton(if (saved) "Saved" else "Save", {
            problem = vm.saveReflection(wentWell, differently)
            saved = problem == null
        }, alt = saved)
        if (s.reflection.hasEntry) {
            DeleteAction(
                "Delete this reflection", "Delete this week’s reflection?",
                { wentWell = ""; differently = ""; vm.deleteReflection() }, gap = 24.dp,
            )
        }

        if (s.earlier.isNotEmpty()) {
            H2("Earlier weeks", top = 32.dp)
            s.earlier.forEach { e ->
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(14.dp)) {
                    Small(e.weekLabel)
                    if (e.wentWell.isNotBlank()) Body(e.wentWell, Modifier.padding(top = 4.dp))
                    if (e.differently.isNotBlank()) Small("Differently: ${e.differently}", Modifier.padding(top = 4.dp), color = c.fg)
                }
            }
        }
    }
}
