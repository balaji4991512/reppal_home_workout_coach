package com.homeworkoutcoach.app.ui.body

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.scale.ScaleScanner
import com.homeworkoutcoach.app.scale.ScanProblem
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.Field
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.LineRow
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.progress.LineChart
import com.homeworkoutcoach.app.ui.progress.Range
import com.homeworkoutcoach.app.ui.progress.RangeTabs
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** Body: weigh in from the Bluetooth scale or by hand, then weight trends and history. */
@Composable
fun BodyScreen(vm: BodyViewModel = viewModel(factory = Factories.Body)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val weighIn by vm.weighIn.collectAsStateWithLifecycle()
    val height by vm.heightCm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scanner = remember { ScaleScanner(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { vm.cancelWeighIn() } }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) vm.startWeighIn(scanner) else vm.problem(ScanProblem.NO_PERMISSION)
    }
    fun weighInNow() {
        if (scanner.problem() == ScanProblem.NO_PERMISSION) askPermission.launch(scanner.permissions)
        else vm.startWeighIn(scanner)
    }

    val u = ui ?: return
    BodyContent(
        ui = u,
        weighIn = weighIn,
        heightCm = height,
        onWeighIn = ::weighInNow,
        onCancel = vm::cancelWeighIn,
        onUndo = vm::undo,
        onBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onManual = vm::addManual,
        onHeight = vm::setHeight,
        onRange = vm::setRange,
        onDelete = vm::delete,
    )
}

@Composable
fun BodyContent(
    ui: BodyUi,
    weighIn: WeighInUi,
    heightCm: Int?,
    onWeighIn: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onManual: (String) -> String?,
    onHeight: (String) -> String?,
    onRange: (Range) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val c = AppTheme.colors
    Page {
        H1("Body")
        Muted("Weight from your Bluetooth scale, or typed in.")

        ui.latest?.let { l ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l.weight.removeSuffix(" kg"), Modifier.alignByBaseline(), color = c.fg, style = AppTheme.type.tabular(AppTheme.type.big))
                Text("kg", Modifier.alignByBaseline(), color = c.mut, style = AppTheme.type.body.copy(fontSize = 17.sp))
            }
            Small(l.whenText)
        } ?: Muted("No weigh-ins yet.")

        WeighInCard(weighIn, onWeighIn, onCancel, onUndo, onBluetoothSettings, onManual)

        if (ui.stats.isNotEmpty()) {
            H2("Trends")
            ui.stats.forEachIndexed { i, s ->
                LineRow(divider = i < ui.stats.lastIndex, left = {
                    Body(s.label)
                    s.note?.let { Small(it) }
                }) {
                    Text(s.value, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                }
            }
        }
        HeightRow(heightCm, onHeight)

        if (ui.history.isNotEmpty()) {
            H2("Weight")
            RangeTabs(ui.range, onRange)
            if (ui.points.isEmpty()) Muted("No weigh-ins in this range.", Modifier.padding(vertical = 16.dp))
            else LineChart(ui.points, unit = "Weight in kilograms", fromZero = false, label = { BodyPresenter.weight(it) })
            ui.rangeNote?.let { Small(it) }

            H2("History")
            var open by rememberSaveable { mutableStateOf<Long?>(null) }
            ui.history.forEach { h ->
                Hairline()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { open = if (open == h.id) null else h.id }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(h.date)
                        Small(h.detail)
                    }
                    Text(h.weight, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong))
                }
                if (open == h.id) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Delete this weigh-in", false, { onDelete(h.id); open = null }, small = true)
                        Chip("Keep", false, { open = null }, small = true)
                    }
                }
            }
            Hairline()
        }
    }
}

@Composable
private fun WeighInCard(
    state: WeighInUi,
    onWeighIn: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onManual: (String) -> String?,
) {
    val c = AppTheme.colors
    var manual by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        when (state) {
            is WeighInUi.Listening -> {
                H2("Step on the scale", top = 16.dp)
                Text(
                    state.weightKg?.let { "${BodyPresenter.weight(it)} kg" } ?: "—",
                    color = c.fg,
                    style = AppTheme.type.tabular(AppTheme.type.h1),
                )
                Muted(
                    if (state.weightKg == null) "Listening for your scale. Tap it with your foot to wake it."
                    else "Stand still until the reading settles. It saves by itself.",
                )
                PrimaryButton("Cancel", onCancel, alt = true)
            }
            is WeighInUi.Saved -> {
                Text(
                    "Saved ${BodyPresenter.weight(state.weightKg)} kg.",
                    Modifier.padding(top = 12.dp),
                    color = c.ok,
                    style = AppTheme.type.bodyStrong,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Undo", false, onUndo, small = true)
                }
                PrimaryButton("Weigh in again", onWeighIn, alt = true)
            }
            is WeighInUi.Problem -> {
                Text(state.message, Modifier.padding(top = 12.dp), color = c.bad, style = AppTheme.type.bodyStrong)
                if (state.bluetoothOff) PrimaryButton("Open Bluetooth settings", onBluetoothSettings, alt = true)
                PrimaryButton("Weigh in", onWeighIn)
            }
            WeighInUi.Idle -> PrimaryButton("Weigh in", onWeighIn)
        }

        if (state !is WeighInUi.Listening) {
            if (!manual) {
                Small(
                    "Enter weight by hand",
                    Modifier.padding(top = 4.dp).heightIn(min = 44.dp).clickable(role = Role.Button) { manual = true }.padding(top = 12.dp),
                )
            } else {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        text, { text = it; error = null },
                        placeholder = "Weight in kg",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Chip("Save", false, {
                        error = onManual(text)
                        if (error == null) {
                            text = ""
                            manual = false
                        }
                    })
                }
                error?.let { Text(it, Modifier.padding(top = 6.dp), color = c.bad, style = AppTheme.type.small) }
            }
        }
    }
}

@Composable
private fun HeightRow(heightCm: Int?, onHeight: (String) -> String?) {
    val c = AppTheme.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    if (heightCm != null && !editing) {
        LineRow(
            modifier = Modifier.clickable(role = Role.Button) { text = heightCm.toString(); editing = true },
            divider = false,
            left = { Body("Height"); Small("Tap to change") },
        ) {
            Text("$heightCm cm", color = c.fg, style = AppTheme.type.bodyStrong)
        }
        return
    }
    H2("Your height", top = 20.dp)
    Muted("Used for BMI.")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(
            text, { text = it; error = null },
            placeholder = "Height in cm",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Chip("Save", false, {
            error = onHeight(text)
            if (error == null) editing = false
        })
    }
    error?.let { Text(it, Modifier.padding(top = 6.dp), color = c.bad, style = AppTheme.type.small) }
}
