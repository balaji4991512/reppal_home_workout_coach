package com.homeworkoutcoach.app.ui.shell

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

/** A tab's page: a hero with today's number, then a card for each page with what is happening there. [header] goes above the cards. */
@Composable
fun HubScreen(tab: Tab, started: Boolean, onOpen: (String) -> Unit, header: @Composable ColumnScope.() -> Unit = {}) {
    val c = AppTheme.colors
    val vm: HubViewModel = viewModel(factory = Factories.Hub)
    val all by vm.ui.collectAsStateWithLifecycle()
    val ui = all?.get(tab)
    val tints = listOf(c.accentSoft, c.tintMint, c.tintPeach, c.tintRose)
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
        tab.pages.filter { started || !it.needsPlan }.forEachIndexed { i, page ->
            key(page.route) { PageCard(page, ui?.lines?.get(page.route), tints[i % tints.size], i, onOpen) }
        }
    }
}

/** The hub's big number in a ring, on the lavender-to-peach wash. */
@Composable
private fun HeroCard(hero: HubHeroUi, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val fraction by animateFloatAsState(hero.fraction ?: 0f, tween(700), label = "hubRing")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c.accentSoft, c.tintPeach), start = Offset.Zero, end = Offset(900f, 500f)))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ProgressRing(fraction, size = 92.dp, stroke = 10.dp, track = c.bg.copy(alpha = 0.7f)) {
            Text(
                hero.big, color = c.fg, textAlign = TextAlign.Center,
                style = AppTheme.type.tabular(AppTheme.type.h2.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(hero.label, color = c.fg, style = AppTheme.type.h2.copy(fontSize = 19.sp, lineHeight = 25.sp))
            hero.sub?.let { Text(it, Modifier.padding(top = 4.dp), color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold)) }
        }
    }
}

/** A page's card: a tinted icon, its name, what is happening there now (or what it is for), and a chevron. */
@Composable
private fun PageCard(page: HubPage, line: String?, tint: Color, index: Int, onOpen: (String) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fadeLiftIn(index * 60).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.soft)
            .clickable(role = Role.Button) { onOpen(page.route) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(page.icon, null, Modifier.size(24.dp), tint = c.fg)
        }
        Column(Modifier.weight(1f)) {
            Body(page.label, strong = true)
            if (line != null) Text(line, color = c.accent, style = AppTheme.type.small.copy(fontWeight = FontWeight.SemiBold))
            else Small(page.blurb)
        }
        Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = c.mut)
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
