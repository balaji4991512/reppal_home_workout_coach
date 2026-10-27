package com.homeworkoutcoach.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.R
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.RestoreUi
import com.homeworkoutcoach.app.ui.ProfileViewModel
import com.homeworkoutcoach.app.ui.components.Body
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
    onWeek: () -> Unit,
    onSetup: () -> Unit,
    onWiped: () -> Unit,
    onPrivacy: () -> Unit = {},
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
            it, onWeek, onSetup,
            onWipe = { vm.wipe(onWiped) },
            onExport = { saveAs.launch(vm.exportFileName(appName)) },
            exportNote = exportNote,
            onImport = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            restore = restore,
            onConfirmRestore = vm::confirmRestore,
            onCancelRestore = vm::cancelRestore,
            onPrivacy = onPrivacy,
        )
    }
}

/** History and profile [vProfile]. Erasing needs a second tap. */
@Composable
fun ProfileContent(
    ui: ProfileUi,
    onWeek: () -> Unit,
    onSetup: () -> Unit,
    onWipe: () -> Unit,
    onExport: () -> Unit = {},
    exportNote: String? = null,
    onImport: () -> Unit = {},
    restore: RestoreUi? = null,
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    val c = AppTheme.colors
    var confirmWipe by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    Page {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.acc), contentAlignment = Alignment.Center) {
                Text(ui.initial, color = c.on, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
            }
            Column {
                H1(ui.title)
                Muted(ui.saved)
            }
        }

        Column(Modifier.padding(top = 8.dp)) {
            ui.rows.forEachIndexed { i, (label, value) ->
                LineRow(divider = i < ui.rows.lastIndex, left = { Body(label) }) {
                    Text(value, Modifier.weight(1f, fill = false), color = c.fg, style = AppTheme.type.body, textAlign = TextAlign.End)
                }
            }
        }

        H2("Where you are now")
        if (ui.current.isEmpty()) {
            Muted("Finish a workout and your current targets show up here.")
        } else {
            ui.current.forEachIndexed { i, (name, presc) ->
                LineRow(divider = i < ui.current.lastIndex, left = { Body(name) }) {
                    Text(presc, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                }
            }
            Muted("These are the targets for your next session of each exercise.")
        }

        H2("Workout history")
        if (ui.history.isEmpty()) Muted("No workouts yet.")
        ui.history.forEach { h ->
            val expanded = h.id in open
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { open = if (expanded) open - h.id else open + h.id }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(h.title, strong = true)
                        if (h.totals.isNotEmpty()) Small(h.totals)
                    }
                    Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp).rotate(if (expanded) 180f else 0f), tint = c.mut)
                }
                AnimatedVisibility(expanded) { SessionCard(h.card) }
            }
            Hairline()
        }

        PrimaryButton("Back to your week", onWeek)
        PrimaryButton("Edit equipment and plan", onSetup, alt = true)

        H2("Your data")
        Muted("Everything is stored on this phone. Save a copy of your plan, workouts and weigh-ins as a file you keep.")
        TextLink("Privacy", onPrivacy)
        PrimaryButton("Export my data", onExport, alt = true)
        exportNote?.let { Text(it, Modifier.padding(top = 8.dp), color = c.fg, style = AppTheme.type.bodyStrong) }
        PrimaryButton("Restore from a file", onImport, alt = true)
        when (restore) {
            is RestoreUi.Confirm -> Column(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.soft).padding(14.dp),
            ) {
                Body("Restore from this file?", strong = true)
                Body(restore.summary, Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { PrimaryButton("Cancel", onCancelRestore, alt = true, compact = true) }
                    Box(Modifier.weight(1f)) { PrimaryButton("Restore", onConfirmRestore, compact = true) }
                }
            }
            is RestoreUi.Message -> Text(
                restore.text,
                Modifier.padding(top = 8.dp),
                color = if (restore.bad) c.bad else c.fg,
                style = AppTheme.type.bodyStrong,
            )
            null -> Unit
        }
        PrimaryButton(
            if (confirmWipe) "Tap again to erase everything" else "Erase saved data",
            { if (confirmWipe) onWipe() else confirmWipe = true },
            alt = true,
        )
    }
}
