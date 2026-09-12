package com.draftlock.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.draftlock.app.BuildConfig
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse

class GoogleOAuthManager(private val context: Context) {
    private val authService = AuthorizationService(context)
    private val store = SecureAuthStore(context)

    // Must match the client ID used to build the manifest redirect scheme.
    // Do not substitute a runtime client ID: that would create a redirect URI
    // that the installed RedirectUriReceiverActivity cannot receive.
    val effectiveClientId: String
        get() = BuildConfig.GOOGLE_CLIENT_ID.trim()

    private val redirectUri: Uri
        get() = Uri.parse(
            "com.googleusercontent.apps.${effectiveClientId.substringBefore(".apps.googleusercontent.com")}:/oauth2redirect"
        )

    private val driveScope = "https://www.googleapis.com/auth/drive.file"
    private val docsScope = "https://www.googleapis.com/auth/documents"

    val isConfigured: Boolean
        get() = effectiveClientId.endsWith(".apps.googleusercontent.com") &&
            !effectiveClientId.startsWith("YOUR_")

    /**
     * Kept for compatibility with older Settings code. OAuth intentionally uses
     * the build-time client ID so the manifest and request can never diverge.
     */
    fun setRuntimeClientId(id: String) {
        context.getSharedPreferences("draftlock_runtime", Context.MODE_PRIVATE)
            .edit()
            .putString("runtime_google_client_id", id.trim())
            .apply()
    }

    fun startAuthorization(onError: (String) -> Unit = {}) {
        if (!isConfigured) {
            onError("Google OAuth is not configured. Set GOOGLE_CLIENT_ID to the Android OAuth client for package com.draftlock.app.")
            return
        }

        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse("https://accounts.google.com")) { configuration, ex ->
            if (configuration == null) {
                onError(ex?.errorDescription ?: ex?.error ?: "Google authorization configuration unavailable")
                return@fetchFromIssuer
            }

            val request = AuthorizationRequest.Builder(
                configuration,
                effectiveClientId,
                ResponseTypeValues.CODE,
                redirectUri
            )
                .setScope("openid email profile $driveScope $docsScope")
                .build()

            // Use a dedicated completion activity instead of MainActivity. This
            // avoids Compose/lifecycle timing and task-stack ambiguity when AppAuth
            // returns from Chrome/Custom Tabs.
            val completionIntent = Intent(context, OAuthCompletionActivity::class.java)
            val completionPendingIntent = PendingIntent.getActivity(
                context,
                70,
                completionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                authService.performAuthorizationRequest(request, completionPendingIntent)
            } catch (e: Exception) {
                onError(e.message ?: "Unable to open Google sign-in")
            }
        }
    }

    fun handleResult(intent: Intent, onComplete: (Boolean, String) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)

        if (response == null) {
            onComplete(false, error?.errorDescription ?: error?.error ?: "Google authorization failed")
            return
        }

        val authState = AuthState(response, error)
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, tokenError ->
            if (tokenResponse == null) {
                onComplete(
                    false,
                    tokenError?.errorDescription ?: tokenError?.error ?: "Google token exchange failed"
                )
                return@performTokenRequest
            }

            authState.update(tokenResponse, tokenError)
            try {
                store.save(authState.jsonSerializeString())
                onComplete(true, "Google account connected")
            } catch (e: Exception) {
                onComplete(false, "Could not save Google login: ${e.message ?: "storage error"}")
            }
        }
    }

    fun loadState(): AuthState? = store.read()?.let {
        try {
            AuthState.jsonDeserialize(it)
        } catch (_: Exception) {
            null
        }
    }

    fun withFreshToken(onToken: (String?) -> Unit, onError: (String) -> Unit = {}) {
        val state = loadState()
        if (state == null) {
            onError("Not connected")
            return
        }
        state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) onError(ex.errorDescription ?: ex.error ?: "Token refresh failed")
            else onToken(accessToken)
        }
    }

    fun isConnected(): Boolean = loadState() != null

    fun disconnect() = store.clear()

    fun close() = authService.dispose()
}
