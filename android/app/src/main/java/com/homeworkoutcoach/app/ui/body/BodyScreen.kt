package com.homeworkoutcoach.app.ui.body

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Pill
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
        heightCm = height,
        onWeighIn = ::weighInNow,
        onManual = vm::addManual,
        onHeight = vm::setHeight,
        onRange = vm::setRange,
        onDelete = vm::delete,
    )
    WeighInDialog(
        state = weighIn,
        onClose = vm::cancelWeighIn,
        onUndo = vm::undo,
        onRetry = ::weighInNow,
        onBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
    )
}

@Composable
fun BodyContent(
    ui: BodyUi,
    heightCm: Int?,
    onWeighIn: () -> Unit,
    onManual: (String) -> String?,
    onHeight: (String) -> String?,
    onRange: (Range) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val c = AppTheme.colors
    Page {
        H1("Body")
        Muted("Your weight over time, from the Bluetooth scale or typed in.")

        Hero(ui.latest, onWeighIn, onManual)

        if (ui.stats.isNotEmpty()) {
            H2("Trends")
            ui.stats.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { s -> StatTile(s, Modifier.weight(1f)) }
                }
            }
        }
        HeightRow(heightCm, onHeight)

        if (ui.history.isNotEmpty()) {
            H2("Weight")
            RangeTabs(ui.range, onRange)
            if (ui.points.isEmpty()) Muted("No weigh-ins in this range.", Modifier.padding(vertical = 16.dp))
            else LineChart(
                ui.points,
                unit = "Weight in kilograms",
                fromZero = false,
                label = { BodyPresenter.weight(it) },
                emphasis = true,
            )
            ui.rangeNote?.let { Small(it, Modifier.padding(top = 4.dp)) }

            H2("History")
            var open by rememberSaveable { mutableStateOf<Long?>(null) }
            ui.history.forEach { h ->
                Hairline()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .clickable(role = Role.Button) { open = if (open == h.id) null else h.id }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Body(h.date)
                        Small(h.detail)
                    }
                    Text(h.weight, color = c.fg, style = AppTheme.type.tabular(AppTheme.type.bodyStrong.copy(fontSize = 17.sp)))
                }
                if (open == h.id) {
                    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Delete this weigh-in", false, { onDelete(h.id); open = null }, small = true)
                        Chip("Keep", false, { open = null }, small = true)
                    }
                }
            }
            Hairline()
        }
    }
}

/** Latest weight, when, change since last; Weigh in; typed entry. */
@Composable
private fun Hero(latest: LatestUi?, onWeighIn: () -> Unit, onManual: (String) -> String?) {
    val c = AppTheme.colors
    var manual by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(20.dp),
    ) {
        if (latest != null) {
            Small("Latest")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    latest.weight.removeSuffix(" kg"),
                    Modifier.alignByBaseline(),
                    color = c.fg,
                    style = AppTheme.type.tabular(TextStyle(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)),
                )
                Text("kg", Modifier.alignByBaseline(), color = c.mut, style = AppTheme.type.body.copy(fontSize = 18.sp))
            }
            Small(latest.whenText)
            latest.sinceLast?.let { Pill(it, c.bg, c.fg, Modifier.padding(top = 10.dp)) }
            latest.dayNote?.let { Small(it, Modifier.padding(top = 8.dp)) }
        } else {
            Body("No weigh-ins yet", strong = true)
            Small("Tap Weigh in, then step on your scale.")
        }

        PrimaryButton("Weigh in", onWeighIn, Modifier.padding(top = 6.dp))

        if (!manual) {
            Text(
                "or enter weight by hand",
                Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 44.dp).clickable(role = Role.Button) { manual = true }
                    .padding(top = 12.dp),
                color = c.mut,
                style = AppTheme.type.small,
                textAlign = TextAlign.Center,
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

@Composable
private fun StatTile(s: StatUi, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.soft).padding(14.dp)) {
        Small(s.label)
        Text(
            s.value,
            Modifier.padding(top = 4.dp),
            color = if (s.value == BodyPresenter.DASH) c.mut else c.fg,
            style = AppTheme.type.tabular(TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)),
        )
        s.note?.let { Small(it) }
    }
}

@Composable
private fun HeightRow(heightCm: Int?, onHeight: (String) -> String?) {
    val c = AppTheme.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    if (heightCm != null && !editing) {
        Small(
            "Height $heightCm cm · Change",
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { text = heightCm.toString(); editing = true }.padding(top = 12.dp),
        )
        return
    }
    Small("Your height, for BMI", Modifier.padding(top = 12.dp, bottom = 6.dp))
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
