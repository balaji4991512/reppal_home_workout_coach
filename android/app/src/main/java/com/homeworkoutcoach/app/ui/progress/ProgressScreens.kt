package com.homeworkoutcoach.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.ProgressViewModel
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme

@Composable
fun ProgressScreen(
    onOpen: (String) -> Unit,
    vm: ProgressViewModel = viewModel(factory = Factories.Progress),
    overviewVm: OverviewViewModel = viewModel(factory = Factories.Overview),
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val overview by overviewVm.ui.collectAsStateWithLifecycle()
    val items = remember(workouts) { workouts?.let(vm.presenter::list) } ?: return
    ProgressContent(items, onOpen, overview, overviewVm::select)
}

/** Exercise list [vProg]. */
@Composable
fun ProgressContent(
    items: List<ProgressItemUi>,
    onOpen: (String) -> Unit,
    overview: OverviewUi? = null,
    onRange: (com.homeworkoutcoach.engine.OverviewRange) -> Unit = {},
) {
    val c = AppTheme.colors
    Page {
        H1("Your progress")
        if (overview != null) {
            Column(Modifier.padding(top = 12.dp)) { OverviewSection(overview, onRange) }
        }
        H2("Exercises", top = 32.dp)
        Muted("Volume per session for each exercise you have logged.")
        if (items.isEmpty()) {
            EmptyNote("No workouts logged yet. Finish a workout and your progress shows up here.")
            return@Page
        }
        Column(Modifier.padding(top = 16.dp)) {
            items.forEach { item ->
                Hairline()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onOpen(item.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(item.name, strong = true)
                        Small(item.summary)
                    }
                    Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = c.mut)
                }
            }
        }
    }
}

@Composable
fun ExerciseProgressScreen(id: String, onBack: () -> Unit, vm: ProgressViewModel = viewModel(factory = Factories.Progress)) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(Range.QUARTER) }
    val w = workouts ?: return
    val ui = remember(w, range) { vm.presenter.detail(id, w, range, System.currentTimeMillis()) }
    ExerciseProgressContent(ui, onRange = { range = it }, onBack = onBack)
}

/** Per-exercise chart and recent sets [vEx]. */
@Composable
fun ExerciseProgressContent(ui: ExerciseDetailUi, onRange: (Range) -> Unit, onBack: () -> Unit) {
    val c = AppTheme.colors
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Back, "Back to progress", tint = c.fg,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onBack).padding(11.dp),
            )
            Text(
                ui.name, Modifier.weight(1f), color = c.fg, textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.width(44.dp))
        }

        RangeTabs(ui.range, onRange)

        H2(if (ui.bodyweight) "Reps per session" else "Volume per session", top = 26.dp)
        Small(ui.range.label + if (ui.bodyweight) "" else ", sets × reps × weight")
        if (ui.points.isEmpty()) EmptyNote("No workouts in this range.")
        else LineChart(ui.points, unit = if (ui.bodyweight) "Reps" else "Volume in kilograms")

        H2("Recent sets")
        Small("Best set from each workout")
        if (ui.recent.isEmpty()) EmptyNote("No sets yet.")
        ui.recent.forEach { r ->
            Column {
                Hairline(Modifier.padding(top = if (r == ui.recent.first()) 8.dp else 0.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(r.date, Modifier.width(64.dp), color = c.mut, style = AppTheme.type.label.copy(fontWeight = FontWeight.Normal))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.text, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.body))
                        r.effort?.let { Small(it) }
                    }
                    if (r.pr) {
                        Text(
                            "PR",
                            Modifier.clip(RoundedCornerShape(6.dp)).background(c.accs).padding(horizontal = 9.dp, vertical = 4.dp),
                            color = c.fg,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) = Text(
    text,
    Modifier.fillMaxWidth().padding(vertical = 28.dp),
    color = AppTheme.colors.mut,
    style = AppTheme.type.small,
    textAlign = TextAlign.Center,
)

/** 1W to All tabs over a chart. */
@Composable
fun RangeTabs(selected: Range, onRange: (Range) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).selectableGroup()) {
        Range.entries.forEach { r ->
            val on = r == selected
            Column(
                Modifier.weight(1f).heightIn(min = 46.dp)
                    .selectable(selected = on, role = Role.Tab) { onRange(r) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    r.tab,
                    Modifier.padding(vertical = 12.dp),
                    color = if (on) c.fg else c.mut,
                    style = AppTheme.type.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) c.fg else c.bd))
            }
        }
    }
}
