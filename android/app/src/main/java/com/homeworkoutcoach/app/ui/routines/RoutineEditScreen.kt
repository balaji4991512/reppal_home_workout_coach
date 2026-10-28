package com.homeworkoutcoach.app.ui.routines

import com.homeworkoutcoach.app.ui.components.DeleteAction
import com.homeworkoutcoach.app.ui.components.FormButtons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.DayPart

/**
 * Add or edit a routine. [id] is null for a new one. [onDone] closes the form; [onGone] runs after the routine is
 * deleted. Steps are reordered by dragging their handle.
 */
@Composable
fun RoutineEditScreen(
    id: String?,
    onDone: () -> Unit,
    onGone: () -> Unit,
    vm: RoutineEditViewModel = viewModel(key = "routine-edit-${id ?: "new"}", factory = Factories.routineEdit(id)),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val d = s.draft
    val c = AppTheme.colors
    var newStep by rememberSaveable { mutableStateOf("") }
    var linkOpen by rememberSaveable { mutableStateOf<String?>(null) }

    // Drag and drop: the step being dragged, how far it has moved, and where each step sits on screen.
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val stepsNow by rememberUpdatedState(d.steps)
    // Read when called, so the gesture callbacks (kept across recompositions) see the latest.
    fun targetIndex(): Int? = stepsNow.indexOfFirst { bounds[it.id]?.let { b -> startY + dragY in b.top..b.bottom } == true }.takeIf { it >= 0 }
    val target = if (dragging != null) targetIndex() else null
    fun endDrag(drop: Boolean) {
        val from = stepsNow.indexOfFirst { it.id == dragging }
        val to = targetIndex()
        if (drop && from >= 0 && to != null) vm.edit { it.moveStep(from, to) }
        dragging = null
        dragY = 0f
    }

    fun addStep() {
        if (newStep.isBlank()) return
        vm.addStep(newStep.trim())
        newStep = ""
    }

    Page {
        H1(if (id == null) "New routine" else "Edit routine")

        if (s.suggestions.isNotEmpty()) {
            Label("Start from a suggestion")
            Chips { s.suggestions.forEach { Chip(it.name, d.name == it.name, { vm.pick(it) }, small = true) } }
        }

        Label("Name")
        Field(
            d.name, { v -> vm.edit { it.copy(name = v) } }, "For example, Morning",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Label("Part of the day")
        Chips {
            DayPart.entries.forEach { p -> Chip(RoutineFormat.part(p), d.part == p, { vm.edit { it.copy(part = p) } }) }
        }

        Label("Steps")
        if (d.steps.isNotEmpty()) Small("Drag the handle to put steps in order.", Modifier.padding(bottom = 6.dp))
        d.steps.forEachIndexed { index, step ->
            androidx.compose.runtime.key(step.id) {
                val lifted = dragging == step.id
                val hovered = target == index && dragging != null && !lifted
                val habitName = s.habits.firstOrNull { it.habit.id == step.habitId }?.habit?.name
                Column(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { bounds[step.id] = it.boundsInRoot() }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { if (lifted) translationY = dragY }
                        .then(if (lifted) Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).background(c.bg) else if (hovered) Modifier.background(c.accs) else Modifier)
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Grip,
                            contentDescription = "Drag ${step.title.ifBlank { "step ${index + 1}" }}",
                            tint = c.mut,
                            modifier = Modifier.size(48.dp).padding(12.dp).pointerInput(step.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = step.id
                                        dragY = 0f
                                        startY = bounds[step.id]?.center?.y ?: 0f
                                    },
                                    onDragEnd = { endDrag(true) },
                                    onDragCancel = { endDrag(false) },
                                    onDrag = { change, amount -> change.consume(); dragY += amount.y },
                                )
                            },
                        )
                        Box(Modifier.weight(1f)) {
                            Field(
                                step.title, { v -> vm.edit { it.editStep(step.id) { st -> st.copy(title = v) } } }, "Step",
                                label = "Step ${index + 1}",
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                        }
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { vm.edit { it.removeStep(step.id) } }
                                .semantics { contentDescription = "Remove step ${index + 1}" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Close, null, Modifier.size(20.dp), tint = c.mut) }
                    }
                    Row(Modifier.padding(start = 48.dp, top = 2.dp)) {
                        val label = habitName?.let { "Ticks $it" } ?: if (step.habitId != null) "Ticks a habit" else "Link a habit"
                        Chip(label, step.habitId != null, { linkOpen = if (linkOpen == step.id) null else step.id }, small = true)
                    }
                    if (linkOpen == step.id) {
                        Chips(Modifier.padding(start = 48.dp)) {
                            Chip("None", step.habitId == null, { vm.edit { it.editStep(step.id) { st -> st.copy(habitId = null) } }; linkOpen = null }, small = true)
                            s.habits.forEach { h ->
                                Chip(
                                    h.habit.name, step.habitId == h.habit.id,
                                    { vm.edit { it.editStep(step.id) { st -> st.copy(habitId = h.habit.id) } }; linkOpen = null },
                                    small = true,
                                )
                            }
                        }
                        if (s.habits.isEmpty()) Small("Add a habit first, then you can link it here.", Modifier.padding(start = 48.dp, bottom = 6.dp))
                    }
                }
            }
        }

        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                newStep, { newStep = it }, "Add a step", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addStep() }),
            )
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.acc).clickable(role = Role.Button) { addStep() }
                    .semantics { contentDescription = "Add step" },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, null, Modifier.size(24.dp), tint = c.on) }
        }

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton("Save", { vm.save(onDone) })
        FormButtons(onDone)
        if (id != null) {
            DeleteAction(
                "Delete routine", "Delete ${d.name.ifBlank { "this routine" }} and the days you finished it? This can’t be undone.",
                { vm.delete(onGone) },
            )
        }
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))
