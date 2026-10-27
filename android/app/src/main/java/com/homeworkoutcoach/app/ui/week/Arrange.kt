package com.homeworkoutcoach.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.data.SettingsRepository
import com.homeworkoutcoach.app.data.TrainingRepository
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.plan.PlanActions
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.Library
import com.homeworkoutcoach.engine.Planner
import com.homeworkoutcoach.engine.Recovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ArrangeRow(
    val weekday: Int,
    val day: String,
    /** Index of the session in the plan's default order, or null for a rest day. */
    val session: Int?,
    val title: String?,
    val detail: String?,
    val selected: Boolean,
)

data class ArrangeUi(
    val rows: List<ArrangeRow>,
    /** "Every muscle gets at least a day's rest." or what to fix. */
    val headline: String,
    val ok: Boolean,
    /** Back-to-back pairs to fix (they block saving). */
    val problems: List<String>,
    /** Back-to-back pairs the plan has anyway at this many days a week (allowed). */
    val unavoidable: List<String>,
    val canSave: Boolean,
    val isDefault: Boolean,
)

/** The Rearrange days screen: which session falls on which weekday, with the recovery check. Pure, so it is tested. */
class ArrangePresenter(private val lib: Library, private val planner: Planner = Planner(lib)) {

    /** The current arrangement as weekdays per default session, filling in the plan's own days if none is saved. */
    fun initial(settings: UserSettings, swaps: Map<String, String>): List<Int> {
        val ps = settings.planSettings(swaps)
        val n = planner.defaultSequence(ps).size
        return settings.sessionWeekdays?.takeIf { w -> w.size == n && w.distinct().size == n } ?: planner.workoutWeekdays(settings.daysPerWeek)
    }

    /** The plan's own weekdays for each session. */
    fun defaultDays(settings: UserSettings): List<Int> = planner.workoutWeekdays(settings.daysPerWeek)

    /** Move the selected session to [weekday], swapping with whatever is there. Returns the new arrangement. */
    fun move(draft: List<Int>, session: Int, weekday: Int): List<Int> {
        val other = draft.indexOf(weekday)
        return draft.toMutableList().also {
            if (other >= 0) it[other] = draft[session]
            it[session] = weekday
        }
    }

    fun present(settings: UserSettings, swaps: Map<String, String>, draft: List<Int>, selected: Int?): ArrangeUi {
        val base = settings.planSettings(swaps)
        val ps = base.copy(sessionWeekdays = draft)
        val labels = planner.defaultSequence(ps)
        fun names(clashes: List<Recovery.Clash>) = clashes.groupBy { it.firstDay to it.secondDay }.map { (days, list) ->
            val muscles = list.map { lib.muscleLabels.getValue(it.muscle).lowercase() }
            "${joinAnd(muscles).replaceFirstChar { it.uppercase() }}: ${lib.dayNames[days.first]} and ${lib.dayNames[days.second]}"
        }
        val all = Recovery.clashes(lib, planner, ps)
        val blocking = Recovery.newClashes(lib, planner, ps)
        val allowed = all - blocking.toSet()
        val isDefault = draft == planner.workoutWeekdays(settings.daysPerWeek)
        return ArrangeUi(
            rows = lib.dayNames.mapIndexed { wd, dayName ->
                val i = draft.indexOf(wd).takeIf { it >= 0 }
                val day = i?.let { planner.buildDay(labels[it], ps) }
                ArrangeRow(
                    weekday = wd,
                    day = dayName,
                    session = i,
                    title = i?.let { planner.dayName(labels[it]) },
                    detail = day?.let { Copy.count(it.items.size, "exercise") },
                    selected = i != null && i == selected,
                )
            },
            headline = when {
                blocking.isNotEmpty() -> "Some muscles would get no rest day. Move one of these:"
                else -> "Every muscle gets at least a day's rest" + if (allowed.isEmpty()) "." else ", apart from what this many days a week needs."
            },
            ok = blocking.isEmpty(),
            problems = names(blocking),
            unavoidable = names(allowed),
            canSave = blocking.isEmpty(),
            isDefault = isDefault,
        )
    }

    private fun joinAnd(items: List<String>) =
        if (items.size > 1) items.dropLast(1).joinToString(", ") + " and " + items.last() else items.first()
}

class ArrangeViewModel(
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val actions: PlanActions,
    val presenter: ArrangePresenter,
) : ViewModel() {
    data class State(val settings: UserSettings, val swaps: Map<String, String>, val draft: List<Int>, val selected: Int?)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            val swaps = training.swaps().first()
            _state.value = State(s, swaps, presenter.initial(s, swaps), null)
        }
    }

    /** Tap a session to pick it up, then tap a day to put it there. Tapping the picked session again drops it. */
    fun tap(row: ArrangeRow) {
        val st = _state.value ?: return
        val picked = st.selected
        _state.value = when {
            picked == null -> st.copy(selected = row.session)
            row.session == picked -> st.copy(selected = null)
            else -> st.copy(draft = presenter.move(st.draft, picked, row.weekday), selected = null)
        }
    }

    fun resetToDefault() {
        val st = _state.value ?: return
        _state.value = st.copy(draft = presenter.defaultDays(st.settings), selected = null)
    }

    fun save(onDone: () -> Unit) {
        val st = _state.value ?: return
        viewModelScope.launch {
            val isDefault = st.draft == presenter.defaultDays(st.settings)
            actions.arrangeWeek(if (isDefault) null else st.draft)
            onDone()
        }
    }
}

@Composable
fun ArrangeScreen(onDone: () -> Unit, vm: ArrangeViewModel = viewModel(factory = Factories.Arrange)) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val ui = vm.presenter.present(s.settings, s.swaps, s.draft, s.selected)
    val c = AppTheme.colors
    Page {
        H1("Rearrange days")
        Muted(
            if (s.selected == null) "Tap a workout, then tap the day to move it to. Days swap if both have a workout."
            else "Now tap the day to move it to.",
        )

        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.soft)) {
            ui.rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().heightIn(min = 1.dp).background(c.bd))
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .clickable(role = Role.Button, enabled = r.session != null || s.selected != null) { vm.tap(r) }
                        .semantics { selected = r.selected }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.day.take(3), Modifier.width(52.dp).padding(start = 8.dp), color = c.mut, style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold))
                    Box(
                        Modifier.weight(1f).clip(shape)
                            .then(if (r.selected) Modifier.background(c.acc) else if (r.session != null) Modifier.border(1.dp, c.bd, shape).background(c.bg) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (r.title != null) {
                            Column {
                                Text(r.title, color = if (r.selected) c.on else c.fg, style = AppTheme.type.bodyStrong)
                                r.detail?.let { Text(it, color = if (r.selected) c.on else c.mut, style = AppTheme.type.small) }
                            }
                        } else {
                            Text(if (s.selected != null) "Rest · tap to move here" else "Rest", color = c.mut, style = AppTheme.type.body)
                        }
                    }
                }
            }
        }

        Text(
            ui.headline,
            Modifier.padding(top = 16.dp),
            color = if (ui.ok) c.ok else c.bad,
            style = AppTheme.type.bodyStrong,
        )
        ui.problems.forEach { Body("• $it", Modifier.padding(top = 4.dp), color = c.bad) }
        if (ui.unavoidable.isNotEmpty()) {
            Small("With this many days a week, these follow each other anyway:", Modifier.padding(top = 10.dp))
            ui.unavoidable.forEach { Small("• $it") }
        }
        Small("Saving starts a fresh week from its first day. Your history and progress are kept.", Modifier.padding(top = 12.dp))

        PrimaryButton("Save", { if (ui.canSave) vm.save(onDone) }, alt = !ui.canSave)
        if (!ui.isDefault) TextLink("Back to the plan's own days", vm::resetToDefault)
        TextLink("Cancel", onDone)
    }
}
