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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.homeworkoutcoach.app.ui.habits.HabitDetailScreen
import com.homeworkoutcoach.app.ui.habits.HabitEditScreen
import com.homeworkoutcoach.app.ui.habits.HabitsScreen
import com.homeworkoutcoach.app.ui.character.CharacterScreen
import com.homeworkoutcoach.app.ui.focus.FocusScreen
import com.homeworkoutcoach.app.ui.goals.GoalEditScreen
import com.homeworkoutcoach.app.ui.goals.GoalsScreen
import com.homeworkoutcoach.app.ui.journal.JournalScreen
import com.homeworkoutcoach.app.ui.planner.PlannerScreen
import com.homeworkoutcoach.app.ui.routines.RoutineEditScreen
import com.homeworkoutcoach.app.ui.routines.RoutineRunScreen
import com.homeworkoutcoach.app.ui.routines.RoutinesScreen
import com.homeworkoutcoach.app.ui.today.TodayScreen
import com.homeworkoutcoach.app.ui.planner.TaskEditScreen
import com.homeworkoutcoach.app.ui.week.ArrangeScreen
import com.homeworkoutcoach.app.ui.week.WeekScreen
import com.homeworkoutcoach.app.ui.workout.WorkoutScreen

/** Route of the privacy policy, linked from the menu footer, How it works and Profile. */
private const val PRIVACY = "privacy"

/** Route of Rearrange days, opened from the Week page. */
private const val ARRANGE = "arrange"

/** Routes inside Habits: the habit's page, its form, and the form for a new one. */
private const val HABIT = "habit/{id}"
private const val HABIT_EDIT = "habit/{id}/edit"
private const val HABIT_NEW = "habit-new"

/** Route of a task's edit form, opened from the Planner. */
private const val TASK = "task/{id}"

/** Routes inside Goals: a goal's form, and the form for a new one. */
private const val GOAL = "goal/{id}"
private const val GOAL_NEW = "goal-new"

/** Routes inside Routines: running one, its form, and the form for a new one. */
private const val ROUTINE_RUN = "routine/{id}/run"
private const val ROUTINE_EDIT = "routine/{id}/edit"
private const val ROUTINE_NEW = "routine-new"

/** Routes of the pages that content links to directly (Today's "All habits", and so on). */
enum class Dest(val route: String, val label: String, val icon: ImageVector, val beforeSetup: Boolean = false) {
    Today("today", "Today", AppIcons.Sun, beforeSetup = true),
    Habits("habits", "Habits", AppIcons.List, beforeSetup = true),
    Planner("planner", "Planner", AppIcons.Planner, beforeSetup = true),
    Routines("routines", "Routines", AppIcons.Routine, beforeSetup = true),
    Journal("journal", "Journal", AppIcons.Pen, beforeSetup = true),
    Goals("goals", "Goals", AppIcons.Target, beforeSetup = true),
    Focus("focus", "Focus", AppIcons.Timer, beforeSetup = true),
    Character("character", "Character", AppIcons.Star, beforeSetup = true),
    Week("week", "Week", AppIcons.Calendar),
    Workout("workout", "Workout", AppIcons.Dumbbell),
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
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val c = AppTheme.colors
    // A workout with logged sets in progress; leaving it asks first, and [pending] is where it was going.
    var workoutActive by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun guarded(action: () -> Unit) {
        if (workoutActive && current == Dest.Workout.route) pending = action else action()
    }

    /** Opens a page on top of where you are, so Back returns here. */
    fun open(route: String) {
        if (current != route) nav.navigate(route) { launchSingleTop = true }
    }

    fun go(d: Dest) = guarded { open(d.route) }

    fun goTab(tab: Tab) = guarded {
        nav.navigate(tab.root) {
            popUpTo(nav.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }

    val tab = Tab.of(current)
    val showBack = Tab.isHubPage(current) && current != PRIVACY && nav.previousBackStackEntry != null
    val showBar = settings.started || current != Dest.Setup.route
    val onRun = current?.endsWith("/run") == true

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(appName, showBack) { guarded { nav.popBackStack() } }
        Box(Modifier.weight(1f)) {
            AppNavHost(
                nav, appName, settings.started, ::go,
                onOpen = { r -> guarded { open(r) } },
                onWorkoutActive = { workoutActive = it },
                onLeaveRequest = { go(Dest.Week) },
                me = {
                    HubScreen(Tab.Me, settings.started, { r -> guarded { open(r) } }) {
                        MeHeader(settings, account, onTheme) { if (account == null) onSignIn() else onSignOut() }
                    }
                },
            )
        }
        if (showBar && !onRun) BottomBar(tab, ::goTab)
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = c.soft,
            title = { Text("Leave this workout?", color = c.fg, style = AppTheme.type.h2) },
            text = { Text("Sets you logged will be lost.", color = c.mut, style = AppTheme.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    val action = pending
                    pending = null
                    workoutActive = false
                    action?.invoke()
                }) { Text("Leave", color = c.fg) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Stay", color = c.fg) } },
        )
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    appName: String,
    started: Boolean,
    go: (Dest) -> Unit,
    onOpen: (String) -> Unit,
    onWorkoutActive: (Boolean) -> Unit,
    onLeaveRequest: () -> Unit,
    me: @Composable () -> Unit,
) {
    NavHost(nav, startDestination = if (started) Dest.Today.route else Dest.Setup.route) {
        composable(Tab.Train.root) { HubScreen(Tab.Train, started, onOpen) }
        composable(Tab.Plan.root) { HubScreen(Tab.Plan, started, onOpen) }
        composable(Tab.Reflect.root) { HubScreen(Tab.Reflect, started, onOpen) }
        composable(Tab.Me.root) { me() }
        composable(Dest.Week.route) { WeekScreen(onStart = { go(Dest.Workout) }, onArrange = { nav.navigate(ARRANGE) }) }
        composable(ARRANGE) { ArrangeScreen(onDone = { nav.popBackStack() }) }
        composable(Dest.Habits.route) {
            HabitsScreen(onOpen = { nav.navigate("habit/$it") }, onAdd = { nav.navigate(HABIT_NEW) })
        }
        composable(Dest.Planner.route) { PlannerScreen(onOpen = { nav.navigate("task/$it") }) }
        composable(Dest.Journal.route) { JournalScreen() }
        composable(Dest.Focus.route) { FocusScreen() }
        composable(Dest.Character.route) { CharacterScreen() }
        composable(Dest.Goals.route) { GoalsScreen(onOpen = { nav.navigate("goal/$it") }, onAdd = { nav.navigate(GOAL_NEW) }) }
        composable(GOAL_NEW) { GoalEditScreen(id = null, onDone = { nav.popBackStack() }) }
        composable(GOAL) { entry -> GoalEditScreen(entry.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() }) }
        composable(Dest.Routines.route) {
            RoutinesScreen(
                onRun = { nav.navigate("routine/$it/run") },
                onEdit = { nav.navigate("routine/$it/edit") },
                onAdd = { nav.navigate(ROUTINE_NEW) },
            )
        }
        composable(ROUTINE_NEW) { RoutineEditScreen(id = null, onDone = { nav.popBackStack() }, onGone = { nav.popBackStack() }) }
        composable(ROUTINE_RUN) { entry ->
            RoutineRunScreen(entry.arguments?.getString("id").orEmpty(), onClose = { nav.popBackStack() })
        }
        composable(ROUTINE_EDIT) { entry ->
            RoutineEditScreen(entry.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() }, onGone = { nav.popBackStack() })
        }
        composable(TASK) { entry ->
            TaskEditScreen(entry.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() })
        }
        composable(HABIT_NEW) {
            HabitEditScreen(id = null, onDone = { nav.popBackStack() }, onGone = { nav.popBackStack() })
        }
        composable(HABIT) { entry ->
            HabitDetailScreen(
                entry.arguments?.getString("id").orEmpty(),
                onEdit = { nav.navigate("habit/${entry.arguments?.getString("id").orEmpty()}/edit") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(HABIT_EDIT) { entry ->
            HabitEditScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onDone = { nav.popBackStack() },
                // archived or deleted: leave the habit's page too
                onGone = { nav.popBackStack(Dest.Habits.route, inclusive = false) },
            )
        }
        composable(Dest.Setup.route) {
            SetupScreen(appName, onBuilt = {
                nav.navigate(Dest.Today.route) {
                    popUpTo(nav.graph.id) { inclusive = true }
                }
            })
        }
        composable(Dest.How.route) {
            HowItWorks(appName, started, onPrivacy = { nav.navigate(PRIVACY) }) { go(if (started) Dest.Week else Dest.Setup) }
        }
        composable(PRIVACY) { PrivacyScreen(appName, stringResource(R.string.privacy_contact), onBack = { nav.popBackStack() }) }
        composable(Dest.Today.route) {
            TodayScreen(
                onStartWorkout = { go(Dest.Workout) },
                onSetup = { go(Dest.Setup) },
                onRunRoutine = { nav.navigate("routine/$it/run") },
                onRoutines = { go(Dest.Routines) },
                onOpenHabit = { nav.navigate("habit/$it") },
                onHabits = { go(Dest.Habits) },
                onAddHabit = { nav.navigate(HABIT_NEW) },
                onOpenTask = { nav.navigate("task/$it") },
                onPlanner = { go(Dest.Planner) },
                onJournal = { go(Dest.Journal) },
            )
        }
        composable(Dest.Workout.route) {
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
private fun TopBar(appName: String, showBack: Boolean, onBack: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.bg).statusBarsPadding().heightIn(min = 56.dp).padding(start = if (showBack) 10.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showBack) {
            Icon(
                AppIcons.Back,
                contentDescription = "Back",
                tint = c.fg,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onBack).padding(11.dp),
            )
        }
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
