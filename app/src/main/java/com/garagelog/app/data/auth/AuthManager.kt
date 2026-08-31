package com.garagelog.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.garagelog.app.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed class SignInStep {
    data class Complete(val email: String) : SignInStep()
    data class NeedsDriveConsent(val intentSender: IntentSender) : SignInStep()
}

/**
 * Wraps two distinct Google flows that are easy to conflate: (1) identity sign-in via
 * Credential Manager (who is this) and (2) authorization for the Drive appdata scope via
 * the Identity Authorization Client (can this app touch this person's Drive). Both are
 * needed — sign-in alone doesn't grant Drive access.
 */
class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val authorizationClient = Identity.getAuthorizationClient(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _signedInEmail = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    @Volatile private var pendingEmail: String? = null
    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var cachedTokenExpiryMillis: Long = 0L

    /** Starts sign-in. May finish immediately, or may need [completeDriveConsent] after a consent screen. */
    suspend fun beginSignIn(activity: Activity): Result<SignInStep> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(activity, request)
        val credential = response.credential as CustomCredential
        val idTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        pendingEmail = idTokenCredential.id
        requestDriveAuthorization()
    }

    /** Call from the launcher callback after resolving a [SignInStep.NeedsDriveConsent] intent sender. */
    suspend fun completeDriveConsent(data: Intent?): Result<SignInStep.Complete> = runCatching {
        requireNotNull(data) { "No consent result returned." }
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        val email = requireNotNull(pendingEmail) { "Missing pending sign-in email." }
        onAuthorized(email, requireNotNull(result.accessToken) { "Drive access was not granted." })
        SignInStep.Complete(email)
    }

    private suspend fun requestDriveAuthorization(): SignInStep {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = authorizationClient.authorize(authRequest).await()
        return if (result.hasResolution()) {
            SignInStep.NeedsDriveConsent(requireNotNull(result.pendingIntent).intentSender)
        } else {
            val email = requireNotNull(pendingEmail)
            onAuthorized(email, requireNotNull(result.accessToken) { "Drive access was not granted." })
            SignInStep.Complete(email)
        }
    }

    private fun onAuthorized(email: String, accessToken: String) {
        cachedAccessToken = accessToken
        cachedTokenExpiryMillis = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS
        _signedInEmail.value = email
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    /** Returns a Drive-scoped access token, silently refreshing if the cached one's stale. Null if signed out. */
    suspend fun getValidAccessToken(): String? {
        if (signedInEmail.value == null) return null
        cachedAccessToken?.let { token ->
            if (System.currentTimeMillis() < cachedTokenExpiryMillis) return token
        }
        return runCatching {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val result = authorizationClient.authorize(authRequest).await()
            // Already-granted scopes refresh silently (no resolution needed); if this ever DOES need
            // a resolution again, that means access was revoked externally — sync just skips this round
            // and the owner will need to sign in again from Settings.
            if (result.hasResolution()) return null
            val token = requireNotNull(result.accessToken)
            cachedAccessToken = token
            cachedTokenExpiryMillis = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS
            token
        }.getOrNull()
    }

    suspend fun signOut() {
        cachedAccessToken = null
        pendingEmail = null
        _signedInEmail.value = null
        prefs.edit().remove(KEY_EMAIL).apply()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    companion object {
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val PREFS_NAME = "garage_log_auth"
        private const val KEY_EMAIL = "signed_in_email"
        // Google access tokens last ~1h; refresh a bit early so a sync never starts with one about to expire.
        private const val TOKEN_LIFETIME_MILLIS = 50L * 60L * 1000L
    }
}
