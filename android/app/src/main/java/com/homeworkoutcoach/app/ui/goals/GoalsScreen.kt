package com.homeworkoutcoach.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.GoalState

/** Goals: what you are working towards, what you reached, and what ended. */
@Composable
fun GoalsScreen(
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    vm: GoalsViewModel = viewModel(factory = Factories.GoalList),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui ?: return
    Page {
        H1("Goals")
        Muted(s.summary)
        s.active.forEach { GoalCard(it, onOpen) }
        PrimaryButton("Add goal", onAdd)
        if (s.reached.isNotEmpty()) {
            H2("Reached", top = 32.dp)
            s.reached.forEach { GoalCard(it, onOpen) }
        }
        if (s.ended.isNotEmpty()) {
            H2("Ended", top = 32.dp)
            s.ended.forEach { GoalCard(it, onOpen) }
        }
    }
}

@Composable
private fun GoalCard(row: GoalRowUi, onOpen: (String) -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.soft)
            .clickable(role = Role.Button) { onOpen(row.id) }.padding(16.dp),
    ) {
        Body(row.title, strong = true)
        Small(row.detail, Modifier.padding(top = 2.dp))
        // A thin bar: filled as far as the goal has got.
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(row.fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(2.dp))
                    .background(if (row.state == GoalState.REACHED) c.ok else c.fg),
            )
        }
        Small(row.status, Modifier.padding(top = 8.dp), color = if (row.state == GoalState.REACHED) c.ok else c.mut)
    }
}
