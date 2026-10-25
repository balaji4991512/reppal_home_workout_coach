package com.homeworkoutcoach.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.data.UserSettings
import com.homeworkoutcoach.app.ui.Copy
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.Chip
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.Hairline
import com.homeworkoutcoach.app.ui.components.Logo
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton
import com.homeworkoutcoach.app.ui.components.Small
import com.homeworkoutcoach.app.ui.setup.SetupScreen
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.week.WeekScreen
import kotlinx.coroutines.launch

/** Screens in the side menu, in the prototype's order. */
enum class Dest(val route: String, val label: String, val icon: ImageVector, val beforeSetup: Boolean = false) {
    Week("week", "This week", AppIcons.Calendar),
    Today("today", "Today’s workout", AppIcons.Dumbbell),
    Progress("progress", "Progress", AppIcons.Chart),
    Profile("profile", "History and profile", AppIcons.User),
    Setup("setup", "Equipment and plan", AppIcons.Sliders, beforeSetup = true),
    How("how", "How it works", AppIcons.Info, beforeSetup = true),
}

@Composable
fun AppShell(appName: String, settings: UserSettings, onTheme: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val c = AppTheme.colors

    fun go(d: Dest) {
        scope.launch { drawer.close() }
        if (current == d.route) return
        nav.navigate(d.route) {
            popUpTo(nav.graph.startDestinationId) { saveState = false }
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = c.scrim,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = c.bg,
                drawerShape = RoundedCornerShape(0.dp),
                modifier = Modifier.width(300.dp),
            ) {
                MenuContent(
                    appName = appName,
                    settings = settings,
                    current = current,
                    onClose = { scope.launch { drawer.close() } },
                    onGo = ::go,
                    onTheme = onTheme,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            TopBar(appName) { scope.launch { drawer.open() } }
            AppNavHost(nav, appName, settings.started, ::go)
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, appName: String, started: Boolean, go: (Dest) -> Unit) {
    NavHost(nav, startDestination = if (started) Dest.Week.route else Dest.Setup.route) {
        composable(Dest.Week.route) { WeekScreen(onStart = { go(Dest.Today) }) }
        composable(Dest.Setup.route) {
            SetupScreen(appName, onBuilt = {
                nav.navigate(Dest.Week.route) {
                    popUpTo(nav.graph.id) { inclusive = true }
                }
            })
        }
        composable(Dest.How.route) { HowItWorks(appName, started) { go(if (started) Dest.Week else Dest.Setup) } }
        composable(Dest.Today.route) { ComingNext("Today’s workout", "The workout screen is built next.") }
        composable(Dest.Progress.route) { ComingNext("Progress", "Charts per exercise are built after the workout screen.") }
        composable(Dest.Profile.route) { ComingNext("History and profile", "Saved workouts and your profile are built after the workout screen.") }
    }
}

@Composable
private fun TopBar(appName: String, onMenu: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.bg).statusBarsPadding().heightIn(min = 56.dp).padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            AppIcons.Menu,
            contentDescription = "Open menu",
            tint = c.fg,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onMenu).padding(11.dp),
        )
        Brand(appName)
    }
}

@Composable
private fun Brand(appName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Logo(36.dp)
        Text(appName, color = AppTheme.colors.fg, style = AppTheme.type.bodyStrong.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MenuContent(
    appName: String,
    settings: UserSettings,
    current: String?,
    onClose: () -> Unit,
    onGo: (Dest) -> Unit,
    onTheme: (ThemeMode) -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxHeight().systemBarsPadding().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Brand(appName) }
            Icon(
                AppIcons.Close, "Close menu", tint = c.fg,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClose).padding(11.dp),
            )
        }
        Small("Progressive overload, set by set.", Modifier.padding(horizontal = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Dest.entries.filter { settings.started || it.beforeSetup }.forEach { d ->
                val on = current == d.route
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) c.accs else c.bg)
                        .clickable(role = Role.Button) { onGo(d) }
                        .semantics { selected = on }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(d.icon, null, Modifier.size(22.dp), tint = if (on) c.fg else c.mut)
                    Text(
                        d.label,
                        color = if (on) c.fg else c.mut,
                        style = AppTheme.type.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column {
            Hairline()
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
                Body(settings.name.ifBlank { "Your plan" }, strong = true)
                Small("Guest")
                Small(
                    if (settings.started) "${Copy.levelName(settings.level)}, ${Copy.count(settings.daysPerWeek, "day")} a week"
                    else "Set up your plan to begin.",
                )
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Auto").forEach { (m, label) ->
                        Chip(label, settings.theme == m, { onTheme(m) }, Modifier.weight(1f), small = true)
                    }
                }
            }
        }
    }
}

/** How your plan adapts [vHow]. */
@Composable
private fun HowItWorks(appName: String, started: Boolean, onDone: () -> Unit) {
    Page {
        H1("How your plan adapts")
        Muted("Plain rules, so you always know why a number changed.")
        listOf(
            "Built from your equipment." to "Only exercises you can do with your bench, dumbbells and bodyweight appear. Anything you said you can’t do is left out.",
            "Set by set." to "Hit your reps and call it easy, and the next set goes up a rep, then a heavier dumbbell. Miss the target and the next set stays the same.",
            "Workout by workout." to "Finish the expected volume (sets × reps × weight) and next time aims for about 10% more. Fall short and you repeat the same plan.",
            "Pain stops an exercise." to "Say or tap that it hurt, and it is removed from your plan. Check with a doctor or physio before trying it again.",
        ).forEach { (title, text) ->
            Row(Modifier.padding(top = 12.dp)) {
                Body("•  ")
                Column {
                    Body(title, strong = true)
                    Body(text)
                }
            }
        }
        Muted("$appName is a training log, not medical advice.", Modifier.padding(top = 12.dp))
        PrimaryButton(if (started) "Back to your week" else "Set up your plan", onDone)
    }
}

@Composable
private fun ComingNext(title: String, text: String) {
    Page {
        H1(title)
        Muted(text)
    }
}
