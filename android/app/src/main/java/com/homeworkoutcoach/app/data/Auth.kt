package com.homeworkoutcoach.app.data

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** The signed-in Google account. */
data class Account(val email: String, val name: String?)

sealed interface SignInResult {
    data class SignedIn(val account: Account) : SignInResult

    /** The user closed the account picker. */
    data object Cancelled : SignInResult

    /** No Google account on the phone, or none could be used. */
    data object NoAccount : SignInResult

    /** This build has no Google sign-in config (google-services.json). */
    data object NotConfigured : SignInResult

    data class Failed(val reason: String) : SignInResult
}

/** Sign-in state. Workout data stays on the phone either way; cloud backup is separate and later. */
interface AuthRepository {
    /** Null when signed out. */
    val account: StateFlow<Account?>

    /** Shows Google's account picker; needs the current Activity. */
    suspend fun signInWithGoogle(activity: Activity): SignInResult

    suspend fun signOut()
}
