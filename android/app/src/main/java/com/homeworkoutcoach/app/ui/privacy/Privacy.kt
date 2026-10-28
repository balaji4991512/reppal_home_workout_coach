package com.homeworkoutcoach.app.ui.privacy

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homeworkoutcoach.app.ui.components.Body
import com.homeworkoutcoach.app.ui.components.H1
import com.homeworkoutcoach.app.ui.components.H2
import com.homeworkoutcoach.app.ui.components.Muted
import com.homeworkoutcoach.app.ui.components.Page
import com.homeworkoutcoach.app.ui.components.PrimaryButton

/**
 * The privacy policy. Keep it true to what the app does, and keep PRIVACY.md (the hosted copy for the Play
 * Store) in step with it.
 */
object PrivacyPolicy {
    const val UPDATED = "27 October 2026"

    fun sections(app: String, contact: String?): List<Pair<String, String>> = listOfNotNull(
        "In short" to
            "$app keeps your training data on your phone. There are no ads, no analytics and no tracking, " +
            "and your data is never sold. Some features use Google services, explained below.",
        "What is stored on your phone" to
            "Your setup (name if you give one, equipment, level, days, split, anything you asked to work around, " +
            "height), your plan and progress for each exercise, your workouts with every set, any notes you " +
            "typed or said, your weigh-ins, your habits with what you ticked or entered for each day, your " +
            "planner tasks, your routines with the days you finished them, and your journal. It stays in the " +
            "app's private storage on this phone.",
        "Signing in with Google" to
            "Signing in is optional. If you choose it, Google confirms your account through Google Play services " +
            "and Firebase Authentication, and $app receives your name and email address. Google keeps a record " +
            "of the sign-in under its own privacy policy (policies.google.com/privacy). Your workouts are not " +
            "uploaded: they stay on this phone either way.",
        "Voice input" to
            "The microphone is only used while you tap Mic during a workout. Your phone's speech service turns " +
            "what you say into text: on the phone when it can, otherwise through Google's speech service online, " +
            "under Google's privacy policy. $app keeps only the text you save with a set, never the audio.",
        "Bluetooth scale" to
            "If you weigh in with a Bluetooth scale, $app listens for the readings the scale broadcasts. It does " +
            "not connect to the scale and does not use Bluetooth to find your location. It saves the weight, the " +
            "time and an ID for your scale, so other people's scales nearby are ignored.",
        "Your choices" to
            "Profile lets you export everything to a file you keep, restore from such a file, and " +
            "erase everything on this phone. Uninstalling the app also deletes its data from this phone. You can " +
            "sign out at any time from the menu, and turn off microphone or Nearby devices access in Android " +
            "settings.",
        "Deleting your account" to
            "If you signed in, Profile has Delete account. It deletes your account (your Google " +
            "sign-in with $app, kept by Firebase) and erases everything on this phone. " +
            (contact?.takeIf { it.isNotBlank() }?.let { "Without the app, you can ask for deletion at $it." } ?: ""),
        "Health" to
            "$app is a training log, not medical advice. If an exercise hurts, stop and check with a doctor or " +
            "physio.",
        "Children" to "$app is not meant for children under 13.",
        "Changes" to "If this policy changes, the new version will be in the app with a new date.",
        contact?.takeIf { it.isNotBlank() }?.let { "Contact" to "Questions about privacy: $it" },
    )
}

@Composable
fun PrivacyScreen(appName: String, contact: String?, onBack: () -> Unit) {
    Page {
        H1("Privacy")
        Muted("Updated ${PrivacyPolicy.UPDATED}")
        PrivacyPolicy.sections(appName, contact).forEachIndexed { i, (title, text) ->
            H2(title, top = if (i == 0) 16.dp else 24.dp)
            Body(text)
        }
        PrimaryButton("Back", onBack, Modifier.padding(top = 8.dp), alt = true)
    }
}
