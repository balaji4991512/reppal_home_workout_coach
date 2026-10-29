package com.homeworkoutcoach.app.ui.habits

import com.homeworkoutcoach.app.ui.components.DeleteAction
import com.homeworkoutcoach.app.ui.components.FormButtons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Chips
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.engine.AutoSource
import com.homeworkoutcoach.engine.HabitKind
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import com.homeworkoutcoach.engine.Habits
import androidx.compose.foundation.layout.width
import java.time.DayOfWeek
import java.time.LocalTime
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext

/**
 * Add or edit a habit. [id] is null for a new one. [onDone] closes the form; [onGone] runs after the habit is archived
 * or deleted, so the caller can leave the habit's own page too.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    id: String?,
    onDone: () -> Unit,
    onGone: () -> Unit,
    vm: HabitEditViewModel = viewModel(key = "habit-edit-${id ?: "new"}", factory = Factories.habitEdit(id)),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val d = s.draft
    val c = AppTheme.colors
    val context = LocalContext.current
    // Which reminder time is being set: its place in the list, or -1 for a new one; null when no clock is open.
    var pickTime by rememberSaveable { mutableStateOf<Int?>(null) }
    var allIcons by rememberSaveable { mutableStateOf(false) }
    // How the reminders are set: none, at times picked one by one, or repeating through a window of the day.
    var mode by rememberSaveable { mutableStateOf(if (d.reminders.isEmpty()) RemindMode.NONE else RemindMode.TIMES) }
    var everyText by rememberSaveable { mutableStateOf("2") }
    var everyHours by rememberSaveable { mutableStateOf(true) }
    var fromMinute by rememberSaveable { mutableStateOf(8 * 60) }
    var toMinute by rememberSaveable { mutableStateOf(20 * 60) }
    // Which end of the window a clock is open for: 0 start, 1 end.
    var pickEnd by rememberSaveable { mutableStateOf<Int?>(null) }
    // Android 13 and up ask before notifications show; the reminders are kept either way.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askToNotify() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun repeat(every: String, hours: Boolean, from: Int, to: Int) {
        val n = every.toIntOrNull()?.takeIf { it >= 1 } ?: return
        val times = Habits.evenly(LocalTime.of(from / 60, from % 60), LocalTime.of(to / 60, to % 60), n * if (hours) 60 else 1)
        vm.edit { it.copy(reminders = times) }
        if (times.isNotEmpty()) askToNotify()
    }

    pickTime?.let { index ->
        val start = d.reminders.getOrNull(index) ?: d.reminders.lastOrNull()?.plusHours(3)?.takeIf { it > d.reminders.last() } ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton({
                    val chosen = LocalTime.of(state.hour, state.minute)
                    vm.edit { draft ->
                        val rest = draft.reminders.filterIndexed { i, _ -> i != index }
                        draft.copy(reminders = Habits.tidyReminders(rest + chosen))
                    }
                    pickTime = null
                    askToNotify()
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (index >= 0) TextButton({
                        vm.edit { draft -> draft.copy(reminders = draft.reminders.filterIndexed { i, _ -> i != index }) }
                        pickTime = null
                    }) { Text("Remove") }
                    TextButton({ pickTime = null }) { Text("Cancel") }
                }
            },
            text = { TimePicker(state) },
        )
    }

    pickEnd?.let { end ->
        val start = (if (end == 0) fromMinute else toMinute).let { LocalTime.of(it / 60, it % 60) }
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickEnd = null },
            confirmButton = {
                TextButton({
                    val chosen = state.hour * 60 + state.minute
                    if (end == 0) fromMinute = chosen else toMinute = chosen
                    pickEnd = null
                    repeat(everyText, everyHours, if (end == 0) chosen else fromMinute, if (end == 0) toMinute else chosen)
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickEnd = null }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }

    if (allIcons) {
        ModalBottomSheet(onDismissRequest = { allIcons = false }, containerColor = c.bg) {
            AllIcons(d.icon, d.color) { key ->
                vm.edit { it.copy(icon = key, color = HabitStyles.colorFor(key)) }
                allIcons = false
            }
        }
    }

    Page {
        H1(if (id == null) "New habit" else "Edit habit")

        Label("Name")
        Field(d.name, { v -> vm.edit { it.copy(name = v) } }, "For example, Drink water")
        if (s.suggestions.isNotEmpty()) {
            Small(if (d.name.isBlank()) "Popular, or type your own" else "Tap one to fill it in", Modifier.padding(top = 10.dp, bottom = 6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.soft)) {
                s.suggestions.forEachIndexed { i, sug ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { vm.pick(sug) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(HabitLook.emoji(sug.icon), fontSize = 22.sp)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(sug.name, color = c.fg, style = AppTheme.type.bodyStrong)
                            Small(HabitFormat.suggestionDetail(sug))
                        }
                    }
                    if (i < s.suggestions.lastIndex) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.bd))
                }
            }
        }

        Label("Icon")
        val quick = (listOfNotNull(HabitIcons.find(d.icon)) + HabitIcons.quickFor(d.name, HabitIcons.starters, 6)).distinctBy { it.key }.take(5)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { icon -> IconTile(icon.key, icon.key == d.icon, HabitStyles.colorFor(icon.key)) { vm.edit { it.copy(icon = icon.key, color = icon.color) } } }
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft).clickable(role = Role.Button) { allIcons = true }
                    .padding(horizontal = 14.dp).semantics { contentDescription = "See all ${HabitIcons.all.size} icons" },
                contentAlignment = Alignment.Center,
            ) { Text("All icons", color = c.accent, style = AppTheme.type.label) }
        }

        Label("How do you track it?")
        Chips {
            Chip("Done or not", d.kind == HabitKind.DONE, { vm.edit { it.withKind(HabitKind.DONE) } })
            Chip("A number", d.kind == HabitKind.COUNT, { vm.edit { it.withKind(HabitKind.COUNT) } })
            Chip("Minutes", d.kind == HabitKind.TIME, { vm.edit { it.withKind(HabitKind.TIME) } })
        }
        if (d.kind != HabitKind.DONE) {
            Label(HabitFormat.targetLabel(d.kind))
            Field(
                d.targetText,
                { v -> vm.edit { it.copy(targetText = v.filter(Char::isDigit).take(4)) } },
                if (d.kind == HabitKind.COUNT) "8" else "20",
                label = HabitFormat.targetLabel(d.kind),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            d.target?.takeIf { d.kind == HabitKind.TIME && it >= 60 }?.let { Small(HabitFormat.minutes(it), Modifier.padding(top = 4.dp)) }
        }

        Label("When?")
        Chips {
            Chip("Every day", d.scheduleType == ScheduleType.DAILY, { vm.edit { it.copy(scheduleType = ScheduleType.DAILY) } })
            Chip("Certain days", d.scheduleType == ScheduleType.WEEKDAYS, { vm.edit { it.copy(scheduleType = ScheduleType.WEEKDAYS) } })
            Chip("Times a week", d.scheduleType == ScheduleType.TIMES, { vm.edit { it.copy(scheduleType = ScheduleType.TIMES) } })
        }
        when (d.scheduleType) {
            ScheduleType.WEEKDAYS -> Chips {
                DayOfWeek.entries.forEach { day ->
                    Chip(
                        HabitFormat.dayShort(day), day in d.weekdays,
                        { vm.edit { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day) } },
                        small = true,
                    )
                }
            }
            ScheduleType.TIMES -> {
                Chips {
                    (1..7).forEach { n -> Chip("$n", d.times == n, { vm.edit { it.copy(times = n) } }, small = true) }
                }
                Small("On any days; it stays on your list until the week has that many.")
            }
            ScheduleType.DAILY -> Unit
        }

        Label("Reminders")
        Chips {
            Chip("None", mode == RemindMode.NONE, { mode = RemindMode.NONE; vm.edit { it.copy(reminders = emptyList()) } }, small = true)
            Chip("At set times", mode == RemindMode.TIMES, {
                if (mode != RemindMode.TIMES) {
                    // always a fresh list, whether it comes from None or from Repeat's generated times; the clock opens for the first one
                    mode = RemindMode.TIMES
                    vm.edit { it.copy(reminders = emptyList()) }
                    pickTime = -1
                }
            }, small = true)
            Chip("Repeat", mode == RemindMode.REPEAT, {
                mode = RemindMode.REPEAT
                repeat(everyText, everyHours, fromMinute, toMinute)
            }, small = true)
        }
        when (mode) {
            RemindMode.NONE -> Unit
            RemindMode.TIMES -> {
                Small("Pick the times you want, such as 4 AM, 8 AM and 4 PM.")
                Chips {
                    d.reminders.forEachIndexed { i, time -> Chip(HabitFormat.clock(time), true, { pickTime = i }, small = true) }
                    if (d.reminders.size < Habits.MAX_REMINDERS) Chip("+ Add a time", false, { pickTime = -1 }, small = true)
                }
            }
            RemindMode.REPEAT -> {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Small("Every")
                    Field(
                        everyText,
                        { v -> everyText = v.filter(Char::isDigit).take(3); repeat(everyText, everyHours, fromMinute, toMinute) },
                        "2", Modifier.width(84.dp), label = "How many", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Chip("Hours", everyHours, { everyHours = true; repeat(everyText, true, fromMinute, toMinute) }, small = true)
                    Chip("Minutes", !everyHours, { everyHours = false; repeat(everyText, false, fromMinute, toMinute) }, small = true)
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Small("From")
                    Chip(HabitFormat.clock(LocalTime.of(fromMinute / 60, fromMinute % 60)), true, { pickEnd = 0 }, small = true)
                    Small("to")
                    Chip(HabitFormat.clock(LocalTime.of(toMinute / 60, toMinute % 60)), true, { pickEnd = 1 }, small = true)
                }
                val gap = (everyText.toIntOrNull() ?: 0) * if (everyHours) 60 else 1
                Small(
                    when {
                        gap < 1 -> "Enter how often."
                        toMinute < fromMinute -> "The end has to be after the start."
                        (toMinute - fromMinute) / gap + 1 > Habits.MAX_REMINDERS ->
                            "That is more than ${Habits.MAX_REMINDERS} a day, so only the first ${Habits.MAX_REMINDERS} are kept. Try a longer gap."
                        else -> "${d.reminders.size} ${if (d.reminders.size == 1) "reminder" else "reminders"}: ${d.reminders.joinToString(", ") { HabitFormat.clock(it) }}"
                    },
                    Modifier.padding(top = 10.dp),
                )
            }
        }
        if (d.reminders.isNotEmpty()) Small("You are reminded at each time on days it is due and not done yet. Reminders stop once it is done.", Modifier.padding(top = 6.dp))

        if (d.auto == AutoSource.WORKOUT) Muted("This one ticks itself on days you finish a workout. You can still change it by hand.")

        if (s.showProblem) s.problem?.let { Text(it, Modifier.padding(top = 14.dp), color = c.bad, style = AppTheme.type.bodyStrong) }

        PrimaryButton("Save", { vm.save(onDone) })
        if (id == null) {
            FormButtons(onDone)
        } else {
            // Cancel and Archive share a row; Delete sits well apart so it is not hit by accident.
            FormButtons(
                onDone,
                extraLabel = if (d.archived) "Bring back" else "Archive",
                onExtra = { vm.setArchived(!d.archived, onGone) },
            )
            if (!d.archived) Small("Archive hides a habit and keeps its history.", Modifier.padding(top = 8.dp))
            DeleteAction("Delete habit", "Delete ${d.name.ifBlank { "this habit" }} and all its history? This can’t be undone.", { vm.delete(onGone) })
        }
    }
}

@Composable
private fun Label(text: String) = Small(text, Modifier.padding(top = 20.dp, bottom = 6.dp))

private enum class RemindMode { NONE, TIMES, REPEAT }

@Composable
private fun IconTile(key: String, on: Boolean, color: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    val tint = HabitLook.color(color)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(48.dp).clip(shape)
            .background(if (on) c.accs else c.bg)
            .border(if (on) BorderStroke(2.dp, tint) else BorderStroke(1.dp, c.bd), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = on; contentDescription = "Icon $key" },
        contentAlignment = Alignment.Center,
    ) { Text(HabitLook.emoji(key), fontSize = 22.sp) }
}

/** Every icon, in groups, in a sheet. Picking one closes it. */
@Composable
private fun AllIcons(selected: String, color: String, onPick: (String) -> Unit) {
    val c = AppTheme.colors
    LazyVerticalGrid(
        GridCells.Adaptive(56.dp),
        Modifier.fillMaxWidth().heightIn(max = 560.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HabitIcons.groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }) { Small(group, Modifier.padding(top = 12.dp)) }
            items(HabitIcons.inGroup(group), key = { it.key }) { icon -> IconTile(icon.key, icon.key == selected, icon.color) { onPick(icon.key) } }
        }
    }
}
