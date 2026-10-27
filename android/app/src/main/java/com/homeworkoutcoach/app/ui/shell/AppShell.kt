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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.homeworkoutcoach.app.data.Account
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
import com.homeworkoutcoach.app.ui.components.TextLink
import com.homeworkoutcoach.app.ui.privacy.PrivacyScreen
import androidx.compose.ui.res.stringResource
import com.homeworkoutcoach.app.R
import com.homeworkoutcoach.app.ui.body.BodyScreen
import com.homeworkoutcoach.app.ui.profile.ProfileScreen
import com.homeworkoutcoach.app.ui.progress.ExerciseProgressScreen
import com.homeworkoutcoach.app.ui.progress.ProgressScreen
import com.homeworkoutcoach.app.ui.setup.SetupScreen
import com.homeworkoutcoach.app.ui.theme.AppIcons
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.week.ArrangeScreen
import com.homeworkoutcoach.app.ui.week.WeekScreen
import com.homeworkoutcoach.app.ui.workout.WorkoutScreen
import kotlinx.coroutines.launch

/** Route of the privacy policy, linked from the menu footer, How it works and Profile. */
private const val PRIVACY = "privacy"

/** Route of Rearrange days, opened from the Week page. */
private const val ARRANGE = "arrange"

/** Screens in the side menu, in the prototype's order. */
enum class Dest(val route: String, val label: String, val icon: ImageVector, val beforeSetup: Boolean = false) {
    Week("week", "Week", AppIcons.Calendar),
    Today("today", "Workout", AppIcons.Dumbbell),
    Progress("progress", "Progress", AppIcons.Chart),
    Body("body", "Body", AppIcons.Scale),
    Profile("profile", "Profile", AppIcons.User),
    Setup("setup", "Plan settings", AppIcons.Sliders, beforeSetup = true),
    How("how", "How it works", AppIcons.Info, beforeSetup = true),
}

@Composable
fun AppShell(
    appName: String,
    settings: UserSettings,
    account: Account?,
    onTheme: (ThemeMode) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val c = AppTheme.colors
    // A workout with logged sets in progress; leaving it asks first.
    var workoutActive by remember { mutableStateOf(false) }
    var leaveTo by remember { mutableStateOf<Dest?>(null) }

    fun navigate(d: Dest) {
        scope.launch { drawer.close() }
        if (current == d.route) return
        nav.navigate(d.route) {
            popUpTo(nav.graph.startDestinationId) { saveState = false }
            launchSingleTop = true
        }
    }

    fun go(d: Dest) {
        if (workoutActive && current == Dest.Today.route && d != Dest.Today) {
            leaveTo = d
            scope.launch { drawer.open() }
            return
        }
        navigate(d)
    }

    // Closing the menu cancels a pending leave.
    LaunchedEffect(drawer.isClosed) { if (drawer.isClosed) leaveTo = null }

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
                    leavePrompt = leaveTo != null,
                    onStay = { scope.launch { drawer.close() } },
                    onLeave = {
                        val d = leaveTo ?: Dest.Week
                        workoutActive = false
                        leaveTo = null
                        navigate(d)
                    },
                    onClose = { scope.launch { drawer.close() } },
                    onGo = ::go,
                    onTheme = onTheme,
                    account = account,
                    onPrivacy = {
                        scope.launch { drawer.close() }
                        if (current != PRIVACY) nav.navigate(PRIVACY)
                    },
                    onAccount = {
                        scope.launch { drawer.close() }
                        if (account == null) onSignIn() else onSignOut()
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            TopBar(appName) { scope.launch { drawer.open() } }
            AppNavHost(
                nav, appName, settings.started, ::go,
                onWorkoutActive = { workoutActive = it },
                onLeaveRequest = { go(Dest.Week) },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    appName: String,
    started: Boolean,
    go: (Dest) -> Unit,
    onWorkoutActive: (Boolean) -> Unit,
    onLeaveRequest: () -> Unit,
) {
    NavHost(nav, startDestination = if (started) Dest.Week.route else Dest.Setup.route) {
        composable(Dest.Week.route) { WeekScreen(onStart = { go(Dest.Today) }, onArrange = { nav.navigate(ARRANGE) }) }
        composable(ARRANGE) { ArrangeScreen(onDone = { nav.popBackStack() }) }
        composable(Dest.Setup.route) {
            SetupScreen(appName, onBuilt = {
                nav.navigate(Dest.Week.route) {
                    popUpTo(nav.graph.id) { inclusive = true }
                }
            })
        }
        composable(Dest.How.route) {
            HowItWorks(appName, started, onPrivacy = { nav.navigate(PRIVACY) }) { go(if (started) Dest.Week else Dest.Setup) }
        }
        composable(PRIVACY) { PrivacyScreen(appName, stringResource(R.string.privacy_contact), onBack = { nav.popBackStack() }) }
        composable(Dest.Today.route) {
            WorkoutScreen(onActive = onWorkoutActive, onLeaveRequest = onLeaveRequest, onWeek = { go(Dest.Week) })
        }
        composable(Dest.Progress.route) { ProgressScreen(onOpen = { nav.navigate("exercise/$it") }) }
        composable("exercise/{id}") { entry ->
            ExerciseProgressScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Dest.Body.route) { BodyScreen() }
        composable(Dest.Profile.route) {
            ProfileScreen(
                onSetup = { go(Dest.Setup) },
                onWiped = { nav.navigate(Dest.Setup.route) { popUpTo(nav.graph.id) { inclusive = true } } },
                onPrivacy = { nav.navigate(PRIVACY) },
            )
        }
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
    leavePrompt: Boolean,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit,
    onGo: (Dest) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    account: Account?,
    onAccount: () -> Unit,
    onPrivacy: () -> Unit,
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
        if (leavePrompt) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.soft).padding(12.dp)) {
                Body("Leave this workout? Sets you logged will be lost.", strong = true)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { PrimaryButton("Stay", onStay, alt = true, compact = true) }
                    Box(Modifier.weight(1f)) { PrimaryButton("Leave", onLeave, compact = true) }
                }
            }
        }
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
                Small(account?.email ?: "Guest")
                Small(
                    if (settings.started) "${Copy.levelName(settings.level)}, ${Copy.count(settings.daysPerWeek, "day")} a week"
                    else "Set up your plan to begin.",
                )
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Auto").forEach { (m, label) ->
                        Chip(label, settings.theme == m, { onTheme(m) }, Modifier.weight(1f), small = true)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onAccount),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(AppIcons.SignInOut, null, Modifier.size(22.dp), tint = c.mut)
                    Text(if (account == null) "Sign in" else "Sign out", color = c.mut, style = AppTheme.type.label)
                }
                TextLink("Privacy", onPrivacy, Modifier.padding(start = 2.dp))
            }
        }
    }
}

/** How your plan adapts [vHow]. */
@Composable
private fun HowItWorks(appName: String, started: Boolean, onPrivacy: () -> Unit, onDone: () -> Unit) {
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
        TextLink("How $appName handles your data", onPrivacy)
        PrimaryButton(if (started) "Back to your week" else "Set up your plan", onDone)
    }
}
