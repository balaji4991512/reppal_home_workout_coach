package com.homeworkoutcoach.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.components.ProgressRing
import com.homeworkoutcoach.app.ui.components.fadeLiftIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeworkoutcoach.app.data.Account
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme

/** The five tabs: the selected one is raised, the rest are quiet. */
@Composable
fun BottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(c.soft)) {
        HorizontalDivider(color = c.bd)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp).selectableGroup()) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                        .selectable(selected = on, role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) c.accentSoft else c.soft).padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Icon(tab.icon, null, Modifier.size(22.dp), tint = if (on) c.accent else c.mut)
                    }
                    Text(
                        tab.label, Modifier.padding(top = 2.dp), color = if (on) c.fg else c.mut, textAlign = TextAlign.Center,
                        style = AppTheme.type.small.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    )
                }
            }
        }
    }
}

/** A tab's page: a hero with today's number, then a colourful tile for each page showing what is happening there. */
@Composable
fun HubScreen(tab: Tab, started: Boolean, onOpen: (String) -> Unit, header: @Composable ColumnScope.() -> Unit = {}) {
    val c = AppTheme.colors
    val vm: HubViewModel = viewModel(factory = Factories.Hub)
    val all by vm.ui.collectAsStateWithLifecycle()
    val ui = all?.get(tab)
    Page {
        H1(tab.label)
        Muted(tab.tagline)
        ui?.hero?.let { HeroCard(it, Modifier.padding(top = 14.dp)) }
        header()
        val waiting = !started && tab.pages.any { it.needsPlan }
        if (waiting) {
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.soft).padding(16.dp)) {
                Body("Set up your plan first", strong = true)
                Small("Tell Wick what equipment you have and these pages open.")
                PrimaryButton("Set up my plan", { onOpen("setup") })
            }
        }
        val pages = tab.pages.filter { started || !it.needsPlan }
        // Two tiles to a row; an odd one out goes first and takes the whole width.
        val rows = if (pages.size % 2 == 1) listOf(listOf(pages.first())) + pages.drop(1).chunked(2) else pages.chunked(2)
        var n = 0
        rows.forEach { row ->
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { page ->
                    val i = n++
                    key(page.route) {
                        PageTile(page, ui?.lines?.get(page.route), ui?.fractions?.get(page.route), tileTint(i), i, Modifier.weight(1f), onOpen)
                    }
                }
            }
        }
    }
}

/** The soft colours the tiles take in turn: lavender, mint, peach, rose. */
@Composable
private fun tileTint(i: Int): Color {
    val c = AppTheme.colors
    return listOf(c.accentSoft, c.tintMint, c.tintPeach, c.tintRose)[i % 4]
}

/** The hub's big number in a ring, on a lavender-to-peach wash with soft shapes, and a short line under the label. */
@Composable
private fun HeroCard(hero: HubHeroUi, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val fraction by animateFloatAsState(hero.fraction ?: 0f, tween(700), label = "hubRing")
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 600f))),
    ) {
        // soft shapes behind, so the card is not a flat wash
        Canvas(Modifier.matchParentSize()) {
            drawCircle(c.accent.copy(alpha = 0.07f), radius = size.height * 0.75f, center = Offset(size.width * 0.95f, -size.height * 0.15f))
            drawCircle(c.coral.copy(alpha = 0.10f), radius = size.height * 0.45f, center = Offset(size.width * 0.78f, size.height * 1.05f))
            drawCircle(c.bg.copy(alpha = 0.35f), radius = size.height * 0.30f, center = Offset(size.width * 0.05f, size.height * 1.0f))
        }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(fraction, size = 104.dp, stroke = 11.dp, track = c.bg.copy(alpha = 0.7f)) {
                Text(
                    hero.big, color = c.fg, textAlign = TextAlign.Center,
                    style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(hero.label, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp, lineHeight = 26.sp))
                hero.sub?.let {
                    Text(
                        it, Modifier.padding(top = 8.dp).clip(RoundedCornerShape(99.dp)).background(c.bg.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 5.dp),
                        color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/**
 * A page as a coloured tile: a big icon on a white disc, the page's name, what is happening there now (or what it is
 * for), and a little bar when there is something to fill. The whole tile opens the page.
 */
@Composable
private fun PageTile(
    page: HubPage,
    line: String?,
    fraction: Float?,
    tint: Color,
    index: Int,
    modifier: Modifier,
    onOpen: (String) -> Unit,
) {
    val c = AppTheme.colors
    val bar by animateFloatAsState(fraction ?: 0f, tween(700), label = "tileBar")
    Column(
        modifier.fadeLiftIn(index * 70).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(tint)
            .clickable(role = Role.Button) { onOpen(page.route) }.padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(c.bg.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Icon(page.icon, null, Modifier.size(26.dp), tint = c.fg)
            }
            Spacer(Modifier.weight(1f))
            Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = c.fg.copy(alpha = 0.45f))
        }
        Spacer(Modifier.height(14.dp))
        Text(page.label, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 18.sp, lineHeight = 22.sp))
        Text(
            line ?: page.blurb, Modifier.padding(top = 2.dp), color = if (line != null) c.fg else c.mut,
            style = AppTheme.type.small.copy(fontWeight = if (line != null) FontWeight.Medium else FontWeight.Normal),
        )
        if (fraction != null) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.bg.copy(alpha = 0.7f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(c.accent))
            }
        }
    }
}

/** Who you are and the plan in a line, with signing in or out; then the theme. */
@Composable
fun MeHeader(settings: UserSettings, account: Account?, onTheme: (ThemeMode) -> Unit, onAccount: () -> Unit) {
    val c = AppTheme.colors
    val initial = settings.name.trim().firstOrNull()?.uppercase() ?: "W"
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 500f)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Text(initial, color = c.onAccent, style = AppTheme.type.h1.copy(fontSize = 28.sp, lineHeight = 32.sp))
        }
        Column(Modifier.weight(1f)) {
            Text(settings.name.ifBlank { "Your plan" }, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 20.sp, lineHeight = 26.sp))
            Small(account?.email ?: "Guest on this phone")
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(99.dp)).background(c.bg.copy(alpha = 0.75f))
                    .clickable(role = Role.Button, onClick = onAccount).heightIn(min = 40.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.SignInOut, null, Modifier.size(18.dp), tint = c.fg)
                Text(if (account == null) "Sign in" else "Sign out", color = c.fg, style = AppTheme.type.label)
            }
        }
    }
    H2("Appearance", top = 24.dp)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Auto").forEach { (m, label) ->
            Chip(label, settings.theme == m, { onTheme(m) }, Modifier.weight(1f), small = true)
        }
    }
    H2("More", top = 24.dp)
}
