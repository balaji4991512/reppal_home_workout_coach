package com.homeworkoutcoach.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.ui.components.CalendarStrip
import com.homeworkoutcoach.app.ui.components.StripDays
import com.homeworkoutcoach.engine.Habits
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.habits.MonthButton
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Journal

/** The journal: two lines for a day (today by default), and the days written before. */
@Composable
fun JournalScreen(vm: JournalViewModel = viewModel(factory = Factories.Journal)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    val c = AppTheme.colors
    // What is typed. It starts from what is saved for the day shown, and starts again when the day changes.
    var win by rememberSaveable(s.date, s.win) { mutableStateOf(s.win) }
    var improve by rememberSaveable(s.date, s.improve) { mutableStateOf(s.improve) }
    var problem by rememberSaveable(s.date) { mutableStateOf<String?>(null) }
    var saved by rememberSaveable(s.date) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(s.date) { mutableStateOf(false) }

    Page {
        val today = LocalDate.now()
        CalendarStrip(
            date = s.date,
            days = StripDays.week(s.date, today, s.badges, lastSelectable = today),
            onSelect = vm::select, onPreviousWeek = vm::previousWeek, onNextWeek = vm::nextWeek,
            canGoNext = Habits.weekStart(s.date).plusWeeks(1) <= today,
            eyebrow = "Journal",
        )
        Column(Modifier.padding(top = 22.dp)) {
            Text(s.title, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp))
            Small(s.subtitle)
            if (!s.isToday) TextLink("Back to today", vm::goToToday)
        }
        Muted(s.summary, Modifier.padding(top = 4.dp))

        Small(Journal.WIN_PROMPT, Modifier.padding(top = 20.dp, bottom = 6.dp))
        Field(
            win, { win = it; problem = null; saved = false }, "A good thing, big or small", minLines = 2,
            label = Journal.WIN_PROMPT, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Small(Journal.IMPROVE_PROMPT, Modifier.padding(top = 16.dp, bottom = 6.dp))
        Field(
            improve, { improve = it; problem = null; saved = false }, "Something to try differently", minLines = 2,
            label = Journal.IMPROVE_PROMPT, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        problem?.let { Text(it, Modifier.padding(top = 10.dp), color = c.bad, style = AppTheme.type.small) }
        PrimaryButton(if (saved) "Saved" else "Save", {
            problem = vm.save(win, improve)
            saved = problem == null
        }, alt = saved)

        if (s.hasEntry) {
            if (confirmDelete) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Small("Delete this day’s entry?", Modifier.weight(1f))
                    TextLink("Keep it", { confirmDelete = false })
                    TextLink("Delete", { confirmDelete = false; vm.delete() }, Modifier.padding(start = 16.dp))
                }
            } else {
                TextLink("Delete this entry", { confirmDelete = true })
            }
        }

        if (s.past.isNotEmpty()) {
            H2("Earlier", top = 32.dp)
            s.past.forEach { e ->
                Column(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft)
                        .clickable(role = Role.Button) { vm.select(e.date) }.padding(14.dp),
                ) {
                    Small(e.label)
                    if (e.win.isNotBlank()) Body(e.win, Modifier.padding(top = 4.dp))
                    if (e.improve.isNotBlank()) Small("Do better: ${e.improve}", Modifier.padding(top = 4.dp), color = c.fg)
                }
            }
        }
    }
}
