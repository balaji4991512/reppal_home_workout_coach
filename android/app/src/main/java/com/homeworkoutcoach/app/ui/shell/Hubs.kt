package com.homeworkoutcoach.app.ui.shell

import androidx.compose.foundation.background
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
import com.homeworkoutcoach.app.ui.Copy
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

/** A tab's page: a card for each of its pages. [header] goes above the cards. */
@Composable
fun HubScreen(tab: Tab, started: Boolean, onOpen: (String) -> Unit, header: @Composable ColumnScope.() -> Unit = {}) {
    val c = AppTheme.colors
    Page {
        H1(tab.label)
        Muted(tab.tagline)
        header()
        val waiting = !started && tab.pages.any { it.needsPlan }
        if (waiting) {
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(16.dp)) {
                Body("Set up your plan first", strong = true)
                Small("Tell Wick what equipment you have and these pages open.")
                PrimaryButton("Set up my plan", { onOpen("setup") })
            }
        }
        tab.pages.filter { started || !it.needsPlan }.forEach { PageCard(it, onOpen) }
    }
}

@Composable
private fun PageCard(page: HubPage, onOpen: (String) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft)
            .clickable(role = Role.Button) { onOpen(page.route) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.accs), contentAlignment = Alignment.Center) {
            Icon(page.icon, null, Modifier.size(22.dp), tint = c.fg)
        }
        Column(Modifier.weight(1f)) {
            Body(page.label, strong = true)
            Small(page.blurb)
        }
        Icon(AppIcons.Forward, null, Modifier.size(18.dp), tint = c.mut)
    }
}

/** What the old side menu kept at its foot: who you are, the plan in a line, the theme and signing in or out. */
@Composable
fun MeHeader(settings: UserSettings, account: Account?, onTheme: (ThemeMode) -> Unit, onAccount: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft).padding(16.dp)) {
        Body(settings.name.ifBlank { "Your plan" }, strong = true)
        Small(account?.email ?: "Guest")
        Small(
            if (settings.started) "${Copy.levelName(settings.level)}, ${Copy.count(settings.daysPerWeek, "day")} a week"
            else "Set up your plan to begin.",
        )
        Row(
            Modifier.padding(top = 14.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAccount).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.SignInOut, null, Modifier.size(20.dp), tint = c.mut)
            Text(if (account == null) "Sign in" else "Sign out", color = c.fg, style = AppTheme.type.label)
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
