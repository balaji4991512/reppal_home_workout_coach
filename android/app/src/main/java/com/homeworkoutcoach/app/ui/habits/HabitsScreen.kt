package com.homeworkoutcoach.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.HabitKind

/** The Habits list: tick what you did today, open a habit for its calendar, add new ones. */
@Composable
fun HabitsScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    vm: HabitsViewModel = viewModel(factory = Factories.HabitList),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    // The habit whose amount box is open, and whether archived habits are showing.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    @Composable
    fun rows(list: List<HabitRowUi>, dueToday: Boolean) = list.forEach { row ->
        HabitRow(
            row = row,
            dueToday = dueToday,
            expanded = open == row.id,
            onBadge = {
                if (row.kind == HabitKind.DONE) vm.record(row, if (row.done) 0 else 1)
                else open = if (open == row.id) null else row.id
            },
            onOpen = { onOpen(row.id) },
            onSet = { vm.record(row, it) },
        )
    }

    Page {
        H1("Habits")
        Muted(s.summary)
        if (s.today.isNotEmpty()) {
            H2("Today", top = 24.dp)
            Hairline(Modifier.padding(top = 4.dp))
            rows(s.today, dueToday = true)
        }
        if (s.other.isNotEmpty()) {
            H2("Not due today", top = 28.dp)
            Hairline(Modifier.padding(top = 4.dp))
            rows(s.other, dueToday = false)
        }
        PrimaryButton("Add habit", onAdd)
        if (s.archived.isNotEmpty()) {
            TextLink(if (showArchived) "Hide archived" else "Archived (${s.archived.size})", { showArchived = !showArchived })
            if (showArchived) {
                Hairline()
                s.archived.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(row.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Body(row.name, Modifier.weight(1f))
                        Small("Archived")
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun HabitRow(
    row: HabitRowUi,
    dueToday: Boolean,
    expanded: Boolean,
    onBadge: () -> Unit,
    onOpen: () -> Unit,
    onSet: (Int) -> Unit,
) {
    val c = AppTheme.colors
    val sub = when {
        row.kind == HabitKind.DONE -> row.week ?: row.progress
        else -> listOfNotNull(row.progress, row.week).joinToString(" · ")
    }.let { if (!dueToday && row.week == null) "Not due today" else it }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp)
                    .clickable(role = Role.Button, onClick = onBadge)
                    .semantics {
                        contentDescription = if (row.kind == HabitKind.DONE) {
                            if (row.done) "Mark ${row.name} not done" else "Mark ${row.name} done"
                        } else "Change today's amount for ${row.name}"
                    },
                contentAlignment = Alignment.Center,
            ) { HabitBadge(row.icon, row.color, row.fraction, row.done) }
            Column(Modifier.weight(1f)) {
                Body(row.name, strong = true)
                Small(sub)
            }
            Icon(AppIcons.Forward, null, Modifier.size(20.dp), tint = c.mut)
        }
        if (expanded) {
            AmountPanel(row.kind, row.target, row.amount, onSet, Modifier.padding(bottom = 10.dp))
        }
        Hairline()
    }
}
