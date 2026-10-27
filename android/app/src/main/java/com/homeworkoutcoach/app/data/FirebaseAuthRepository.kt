package com.homeworkoutcoach.app.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in with Credential Manager, then Firebase Auth with the Google ID token.
 * Firebase keeps the session, so the user stays signed in across restarts.
 */
class FirebaseAuthRepository(private val context: Context) : AuthRepository {
    /** Generated from google-services.json; missing in builds without it. */
    private val webClientId: String? = context.resources
        .getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }
        ?.let(context::getString)

    private val auth: FirebaseAuth? =
        if (webClientId != null && FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null

    private val _account = MutableStateFlow(auth?.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account

    init {
        auth?.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        val clientId = webClientId
        val firebase = auth
        if (clientId == null || firebase == null) return SignInResult.NotConfigured
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed("Unexpected credential type")
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val user = firebase.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
                ?: return SignInResult.Failed("No user returned")
            SignInResult.SignedIn(user.toAccount())
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoAccount
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e.type)
        } catch (e: Exception) {
            SignInResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun signOut() {
        auth?.signOut()
        // Forget the chosen account so the picker shows again next time.
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser.toAccount() = Account(email = email.orEmpty(), name = displayName)
}
