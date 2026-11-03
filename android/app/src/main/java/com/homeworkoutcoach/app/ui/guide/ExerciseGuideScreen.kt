package com.homeworkoutcoach.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** How to do an exercise: the moving figure with the worked muscles lit, a muscle map, the steps and what to watch for. */
@Composable
fun ExerciseGuideScreen(id: String, onBack: () -> Unit, vm: GuideViewModel = viewModel(key = "guide-$id", factory = Factories.guide(id))) {
    val c = AppTheme.colors
    Page {
        Row(
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.ChevronLeft, null, Modifier.size(20.dp), tint = c.mut)
            Small("Back")
        }
        val g = vm.ui ?: run {
            Muted("This exercise isn’t in the library.")
            return@Page
        }
        H1(g.name)
        Small(g.needs, Modifier.padding(top = 2.dp))

        g.motion?.let { motion ->
            var playing by rememberSaveable(g.id) { mutableStateOf(true) }
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 900f)))
                    .clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Play") { playing = !playing }
                    .semantics { contentDescription = "${g.name}, drawn moving. Worked: ${g.main}." }
                    .padding(12.dp),
            ) {
                ExerciseAnimation(motion, g.primary, g.secondary, playing, Modifier.fillMaxWidth().height(250.dp))
                Text(
                    if (playing) "Tap to pause" else "Paused · tap to play",
                    Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(99.dp)).background(c.bg.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = c.mut, style = AppTheme.type.small.copy(fontSize = 12.sp),
                )
            }
        }

        H2("Muscles worked", top = 24.dp)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(c.coral, "Main: ${g.main}")
            g.also?.let { Legend(lerp(c.bg, c.coral, 0.45f), "Also: $it") }
        }
        // the map is 210 wide (front, a gap, back), so the labels share that width to sit under each body
        Column(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.width(231.dp)) {
                BodyMap(g.primary, g.secondary, Modifier.fillMaxWidth().height(220.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("Front", Modifier.weight(100f), color = c.mut, style = AppTheme.type.small, textAlign = TextAlign.Center)
                    Box(Modifier.weight(10f))
                    Text("Back", Modifier.weight(100f), color = c.mut, style = AppTheme.type.small, textAlign = TextAlign.Center)
                }
            }
        }

        if (g.steps.isNotEmpty()) {
            H2("How to do it", top = 24.dp)
            g.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Body(step, Modifier.weight(1f))
                }
            }
        }
        if (g.tips.isNotEmpty()) {
            H2("Watch out", top = 24.dp)
            Column(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tintPeach).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                g.tips.forEach { Body("• $it") }
            }
        }
        Small(
            "Stop if anything hurts. Pain is a signal, not something to push through.",
            Modifier.padding(top = 18.dp, bottom = 8.dp),
            color = c.mut,
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Small(text, color = AppTheme.colors.fg)
    }
}
