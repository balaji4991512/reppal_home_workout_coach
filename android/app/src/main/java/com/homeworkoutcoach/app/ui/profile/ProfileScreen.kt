package com.homeworkoutcoach.app.ui.profile

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.R
import com.homeworkoutcoach.app.ui.DeleteUi
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.ProfileViewModel
import com.homeworkoutcoach.app.ui.RestoreUi
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.OutlineButton
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.LineRow
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.week.SessionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    onSetup: () -> Unit,
    onWiped: () -> Unit,
    onPrivacy: () -> Unit = {},
    onEditWorkout: (Long) -> Unit = {},
    vm: ProfileViewModel = viewModel(factory = Factories.Profile),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val exportNote by vm.exportNote.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    // The system "save as" screen: the user picks Downloads, Drive or anywhere else; no storage permission needed.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.export(appName) { text ->
                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
            }
        }
    }
    val restore by vm.restore.collectAsStateWithLifecycle()
    val delete by vm.delete.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Drive and some file apps label JSON as plain text or binary, so accept those too.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                }
                vm.checkFile(text)
            }
        }
    }
    ui?.let {
        ProfileContent(
            it, onSetup,
            onWipe = { vm.wipe(onWiped) },
            onExport = { saveAs.launch(vm.exportFileName(appName)) },
            exportNote = exportNote,
            onImport = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            restore = restore,
            onConfirmRestore = vm::confirmRestore,
            onCancelRestore = vm::cancelRestore,
            onPrivacy = onPrivacy,
            delete = delete,
            onDeleteAccount = { (context as? Activity)?.let(vm::deleteAccount) },
            onEditWorkout = onEditWorkout,
        )
    }
}

/** Profile [vProfile]: a card, training setup, current targets, history, then data and account. */
@Composable
fun ProfileContent(
    ui: ProfileUi,
    onSetup: () -> Unit,
    onWipe: () -> Unit,
    onExport: () -> Unit = {},
    exportNote: String? = null,
    onImport: () -> Unit = {},
    restore: RestoreUi? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    delete: DeleteUi = DeleteUi(),
    onDeleteAccount: () -> Unit = {},
    onEditWorkout: (Long) -> Unit = {},
) {
    val c = AppTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    Page {
        // Who you are.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.acc), contentAlignment = Alignment.Center) {
                    Text(ui.initial, color = c.on, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold))
                }
                Column(Modifier.weight(1f)) {
                    Text(ui.title, color = c.fg, style = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold))
                    Small(ui.subtitle)
                }
            }
            Hairline(Modifier.padding(top = 16.dp))
            Body(ui.stats, Modifier.padding(top = 12.dp), strong = true)
        }

        SectionLabel("Training setup")
        Group {
            ui.setup.forEachIndexed { i, line ->
                Text(
                    line,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = if (i == 0) 14.dp else 2.dp),
                    color = c.fg,
                    style = AppTheme.type.body,
                )
            }
            Spacer(Modifier.height(14.dp))
            GroupDivider()
            GroupRow("Edit plan settings", onClick = onSetup)
        }

        SectionLabel("Current targets")
        Group {
            if (ui.current.isEmpty()) {
                GroupNote("Finish a workout and your targets for next time show up here.")
            } else {
                ui.current.forEachIndexed { i, (name, presc) ->
                    if (i > 0) GroupDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Body(name, Modifier.weight(1f))
                        Text(presc, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                    }
                }
            }
        }

        SectionLabel("Workout history")
        Group {
            if (ui.history.isEmpty()) GroupNote("No workouts yet.")
            val shown = if (showAll) ui.history else ui.history.take(HISTORY_PREVIEW)
            shown.forEachIndexed { i, h ->
                val expanded = h.id in open
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .clickable(role = Role.Button) { open = if (expanded) open - h.id else open + h.id }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(h.title, strong = true)
                        if (h.totals.isNotEmpty()) Small(h.totals)
                    }
                    Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = c.mut)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                        SessionCard(h.card)
                        OutlineButton("Edit this workout", { onEditWorkout(h.id) }, tint = c.accent, compact = true)
                    }
                }
            }
            if (ui.history.size > HISTORY_PREVIEW) {
                GroupDivider()
                GroupRow(if (showAll) "Show fewer" else "Show all ${ui.history.size} workouts", onClick = { showAll = !showAll })
            }
        }

        SectionLabel("Data")
        Group {
            GroupRow("Export my data", "Save everything as a file you keep", onExport)
            GroupDivider()
            GroupRow("Restore from a file", "Replace this phone's data with an export", onImport)
            GroupDivider()
            GroupRow("Privacy", "How your data is handled", onPrivacy)
        }
        exportNote?.let { Note(it) }
        when (restore) {
            is RestoreUi.Confirm -> ConfirmCard(
                title = "Restore from this file?",
                text = restore.summary,
                confirm = "Restore",
                onConfirm = onConfirmRestore,
                onCancel = onCancelRestore,
            )
            is RestoreUi.Message -> Note(restore.text, bad = restore.bad)
            null -> Unit
        }

        SectionLabel("Reset")
        Group {
            GroupRow(
                if (confirmWipe) "Tap again to erase everything" else "Erase data on this phone",
                "Plan, workouts and weigh-ins. Export first to keep a copy.",
                { if (confirmWipe) onWipe() else confirmWipe = true },
                danger = true,
            )
            if (ui.signedIn) {
                GroupDivider()
                GroupRow(
                    if (delete.busy) "Deleting…" else "Delete account",
                    "Removes your Google sign-in with this app and erases this phone",
                    { if (!delete.busy) confirmDelete = true },
                    danger = true,
                )
            }
        }
        if (confirmDelete) {
            ConfirmCard(
                title = "Delete your account?",
                text = "This can't be undone. Google may ask you to pick your account once more to confirm.",
                confirm = "Delete",
                onConfirm = { confirmDelete = false; onDeleteAccount() },
                onCancel = { confirmDelete = false },
            )
        }
        delete.error?.let { Note(it, bad = true) }
    }
}

private const val HISTORY_PREVIEW = 5

/** Small grey capitals above a group, so a section title never looks tappable. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp),
        color = AppTheme.colors.mut,
        style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
    )
}

/** A rounded card holding one section's rows. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.soft), content = content)
}

/** Divider between rows in a group, inset from the left edge. */
@Composable
private fun GroupDivider() = Hairline(Modifier.padding(start = 16.dp))

@Composable
private fun GroupNote(text: String) = Text(
    text,
    Modifier.padding(16.dp),
    color = AppTheme.colors.mut,
    style = AppTheme.type.body,
)

/** A tappable row in a group: label, optional description, arrow. */
@Composable
private fun GroupRow(label: String, detail: String? = null, onClick: () -> Unit, danger: Boolean = false) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Body(label, color = if (danger) c.bad else c.fg, strong = true)
            detail?.let { Small(it) }
        }
        Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = c.mut)
    }
}

@Composable
private fun ConfirmCard(title: String, text: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = AppTheme.colors
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.bd, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Body(title, strong = true)
        Body(text, Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton("Cancel", onCancel, alt = true, compact = true) }
            Box(Modifier.weight(1f)) { PrimaryButton(confirm, onConfirm, compact = true) }
        }
    }
}

@Composable
private fun Note(text: String, bad: Boolean = false) {
    val c = AppTheme.colors
    Text(text, Modifier.padding(start = 4.dp, top = 10.dp), color = if (bad) c.bad else c.ok, style = AppTheme.type.bodyStrong)
}
