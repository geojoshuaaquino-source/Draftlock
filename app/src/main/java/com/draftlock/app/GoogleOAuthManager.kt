package com.draftlock.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.TaskStackBuilder
import com.draftlock.app.BuildConfig
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.AuthState
import net.openid.appauth.TokenResponse

class GoogleOAuthManager(private val context: Context) {
    private val authService = AuthorizationService(context)
    private val store = SecureAuthStore(context)
    val effectiveClientId: String get() {
        val runtime = context.getSharedPreferences("draftlock_runtime", Context.MODE_PRIVATE).getString("runtime_google_client_id", null)
        if (!runtime.isNullOrBlank() && runtime.contains(".apps.googleusercontent.com")) return runtime.trim()
        return BuildConfig.GOOGLE_CLIENT_ID
    }
    private val redirectUri: Uri get() = Uri.parse("com.googleusercontent.apps.${effectiveClientId.substringBefore(".apps.googleusercontent.com")}:/oauth2redirect")
    private val driveScope = "https://www.googleapis.com/auth/drive.file"
    private val docsScope = "https://www.googleapis.com/auth/documents"

    val isConfigured: Boolean get() = !effectiveClientId.startsWith("YOUR_") && effectiveClientId.contains(".apps.googleusercontent.com")

    fun setRuntimeClientId(id: String) {
        context.getSharedPreferences("draftlock_runtime", Context.MODE_PRIVATE).edit().putString("runtime_google_client_id", id.trim()).apply()
    }

    fun startAuthorization(onError: (String) -> Unit = {}) {
        if (!isConfigured) {
            onError("Add GOOGLE_CLIENT_ID to local.properties or set via Settings → Gmail. Get it from Google Cloud Console → Credentials → OAuth client Web.")
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
            ).setScope("openid email profile $driveScope $docsScope").build()
            val completionIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(Intent(context, MainActivity::class.java))
                .getPendingIntent(70, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                ?: run { onError("Unable to create OAuth PendingIntent"); return@fetchFromIssuer }
            authService.performAuthorizationRequest(request, completionIntent)
        }
    }

    fun handleResult(intent: Intent, onComplete: (Boolean, String) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = net.openid.appauth.AuthorizationException.fromIntent(intent)
        if (response == null) {
            onComplete(false, error?.errorDescription ?: "Google authorization failed")
            return
        }
        val authState = AuthState(response, error)
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, tokenError ->
            if (tokenResponse == null) {
                onComplete(false, tokenError?.errorDescription ?: "Google token exchange failed")
            } else {
                authState.update(tokenResponse, tokenError)
                store.save(authState.jsonSerializeString())
                onComplete(true, "Google account connected")
            }
        }
    }

    fun loadState(): AuthState? = store.read()?.let {
        try { AuthState.jsonDeserialize(it) } catch (_: Exception) { null }
    }
    fun withFreshToken(onToken: (String?) -> Unit, onError: (String) -> Unit = {}) {
        val state = loadState()
        if (state == null) { onError("Not connected"); return }
        state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) onError(ex.errorDescription ?: ex.error ?: "Token refresh failed")
            else onToken(accessToken)
        }
    }
    fun isConnected(): Boolean = loadState() != null
    fun disconnect() = store.clear()
    fun close() = authService.dispose()
}
