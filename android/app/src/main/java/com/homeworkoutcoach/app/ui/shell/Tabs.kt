package com.homeworkoutcoach.app.ui.shell

import androidx.compose.ui.graphics.vector.ImageVector
import com.homeworkoutcoach.app.ui.theme.AppIcons

/** One page listed on a hub: a card with a line about it. [needsPlan] pages wait until the plan is set up. */
data class HubPage(val label: String, val blurb: String, val icon: ImageVector, val route: String, val needsPlan: Boolean = false)

/**
 * The five tabs along the bottom. Today is home; each other tab is a hub with a card per page, and a card opens that
 * page. [root] is the tab's own route.
 */
enum class Tab(val label: String, val icon: ImageVector, val root: String, val tagline: String, val pages: List<HubPage>) {
    Today("Today", AppIcons.Sun, "today", "", emptyList()),

    Train(
        "Train", AppIcons.Dumbbell, "hub/train", "Your workouts and your body.",
        listOf(
            HubPage("Week", "Your plan for the week and what is next", AppIcons.Calendar, "week", needsPlan = true),
            HubPage("Workout", "Start or carry on with a session", AppIcons.Dumbbell, "workout", needsPlan = true),
            HubPage("Body", "Weigh in and see how it moves", AppIcons.Scale, "body", needsPlan = true),
        ),
    ),

    Plan(
        "Plan", AppIcons.List, "hub/plan", "What you do each day.",
        listOf(
            HubPage("Habits", "Things you do each day or week", AppIcons.List, "habits"),
            HubPage("Routines", "Morning and night, step by step", AppIcons.Routine, "routines"),
            HubPage("Planner", "Tasks for today and the days ahead", AppIcons.Planner, "planner"),
            HubPage("Focus", "A timer for time you set aside", AppIcons.Timer, "focus"),
        ),
    ),

    Reflect(
        "Reflect", AppIcons.Pen, "hub/reflect", "Look back and look ahead.",
        listOf(
            HubPage("Progress", "How your habits, workouts and focus went", AppIcons.Chart, "progress"),
            HubPage("Goals", "Bigger aims with an end date", AppIcons.Target, "goals"),
            HubPage("Journal", "Two lines about your day", AppIcons.Pen, "journal"),
            HubPage("Character", "What you did this week, and one focus", AppIcons.Star, "character"),
        ),
    ),

    Me(
        "Me", AppIcons.User, "hub/me", "Your details and settings.",
        listOf(
            HubPage("Profile", "Your details, backup and your data", AppIcons.User, "profile", needsPlan = true),
            HubPage("Plan settings", "Equipment, level and days a week", AppIcons.Sliders, "setup"),
            HubPage("How it works", "Why a number changed", AppIcons.Info, "how"),
            HubPage("Privacy", "What stays on this phone", AppIcons.Lock, "privacy"),
        ),
    ),
    ;

    companion object {
        /** The tab a screen belongs to, from its route pattern (for example "habit/{id}"), so the bar highlights the right one. */
        fun of(route: String?): Tab? {
            val r = route ?: return null
            return when {
                r == "today" -> Today
                r == "hub/train" || r in setOf("week", "arrange", "workout", "body") -> Train
                r == "hub/plan" || r.startsWith("habit") || r.startsWith("routine") || r == "planner" || r.startsWith("task") || r == "focus" -> Plan
                r == "hub/reflect" || r == "progress" || r.startsWith("exercise") || r.startsWith("goal") || r == "journal" || r == "character" -> Reflect
                r == "hub/me" || r in setOf("profile", "setup", "how", "privacy") -> Me
                else -> null
            }
        }

        /** Whether [route] is a page opened from a hub, which gets a back arrow in the top bar. */
        fun isHubPage(route: String?): Boolean = entries.any { t -> t.pages.any { it.route == route } }
    }
}
