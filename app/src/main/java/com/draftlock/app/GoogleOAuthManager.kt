package com.draftlock.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleOAuthManager(private val context: Context) {
    companion object {
        const val AUTH_REQUEST_CODE = 7001
        const val PICKER_REQUEST_CODE = 7002
    }

    private val authService = AuthorizationService(context)
    private val store = SecureAuthStore(context)

    // Must match the client ID used to build the manifest redirect scheme.
    val effectiveClientId: String
        get() = BuildConfig.GOOGLE_CLIENT_ID.trim()

    private val redirectUri: Uri
        get() = Uri.parse(
            "com.googleusercontent.apps.${effectiveClientId.substringBefore(".apps.googleusercontent.com")}:/oauth2redirect"
        )

    // Full Drive access is required for DraftLock's cloud-wide Google Docs search
    // and editing of existing documents the user can access.
    private val driveScope = "https://www.googleapis.com/auth/drive"

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
                .setScope("openid email profile $driveScope")
                .setAdditionalParameters(
                    mapOf(
                        "access_type" to "offline",
                        "include_granted_scopes" to "true"
                    )
                )
                .build()

            val launch = {
                try {
                    val activity = context as? Activity
                    if (activity != null) {
                        activity.startActivityForResult(
                            authService.getAuthorizationRequestIntent(request),
                            AUTH_REQUEST_CODE
                        )
                    } else {
                        val completionIntent = Intent(context, OAuthCompletionActivity::class.java)
                        val completionPendingIntent = PendingIntent.getActivity(
                            context,
                            AUTH_REQUEST_CODE,
                            completionIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        authService.performAuthorizationRequest(request, completionPendingIntent)
                    }
                } catch (e: Exception) {
                    onError("Unable to open Google sign-in: " + (e.message ?: e.javaClass.simpleName))
                }
            }

            if (context is Activity) context.runOnUiThread(launch) else launch()
        }
    }

    fun startPicker(onError: (String) -> Unit = {}) {
        if (!isConfigured) {
            onError("Google OAuth is not configured.")
            return
        }

        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse("https://accounts.google.com")) { configuration, ex ->
            if (configuration == null) {
                onError(ex?.errorDescription ?: ex?.error ?: "Google authorization configuration unavailable")
                return@fetchFromIssuer
            }

            // Picker uses the same full-drive scope as sign-in: DraftLock does
            // cloud-wide search + opens arbitrary user docs, which drive.file
            // (only files the app created/opened) cannot cover.
            val request = AuthorizationRequest.Builder(
                configuration,
                effectiveClientId,
                ResponseTypeValues.CODE,
                redirectUri
            )
                .setScope(driveScope)
                .setPrompt(AuthorizationRequest.Prompt.CONSENT)
                .setAdditionalParameters(
                    mapOf(
                        "access_type" to "offline",
                        "trigger_onepick" to "true",
                        "allow_multiple" to "false",
                        "mimetypes" to "application/vnd.google-apps.document"
                    )
                )
                .build()

            val launch = {
                try {
                    val activity = context as? Activity
                    if (activity != null) {
                        activity.startActivityForResult(
                            authService.getAuthorizationRequestIntent(request),
                            PICKER_REQUEST_CODE
                        )
                    } else {
                        onError("Google Picker requires an Activity context.")
                    }
                } catch (e: Exception) {
                    onError("Unable to open Google Picker: " + (e.message ?: e.javaClass.simpleName))
                }
            }

            if (context is Activity) context.runOnUiThread(launch) else launch()
        }
    }

    fun handlePickerResult(intent: Intent, onComplete: (List<String>, String) -> Unit) {
        val pickedIds = intent.data?.getQueryParameter("picked_file_ids")
            ?.split(",")
            ?.map { Uri.decode(it).trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)

        if (response == null) {
            onComplete(emptyList(), error?.errorDescription ?: error?.error ?: "Google Picker authorization failed")
            return
        }

        val state = loadState() ?: AuthState()
        state.update(response, error)

        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
            if (tokenResponse == null) {
                onComplete(
                    emptyList(),
                    "Google Picker token exchange failed: " +
                        (tokenError?.errorDescription ?: tokenError?.error ?: tokenError?.type ?: "unknown OAuth error")
                )
                return@performTokenRequest
            }

            state.update(tokenResponse, tokenError)
            try {
                store.save(state.jsonSerializeString())
                if (pickedIds.isEmpty()) {
                    onComplete(emptyList(), "No Google Doc was selected")
                } else {
                    onComplete(pickedIds, "")
                }
            } catch (e: Exception) {
                onComplete(emptyList(), "Could not save Google Picker authorization: " + (e.message ?: "storage error"))
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

        val authState = AuthState()
        authState.update(response, error)

        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, tokenError ->
            if (tokenResponse == null) {
                onComplete(
                    false,
                    "Google token exchange failed: " +
                        (tokenError?.errorDescription ?: tokenError?.error ?: tokenError?.type ?: "unknown OAuth error")
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

    /**
     * Suspending variant of [withFreshToken]. Returns null when there is no
     * usable authorization. Throws on refresh failure so callers can map the
     * error. Never hangs forever — callers should still apply withTimeout.
     */
    suspend fun freshTokenSuspend(): String? =
        suspendCancellableCoroutine { cont ->
            withFreshToken(
                onToken = { token ->
                    if (cont.isActive) cont.resume(token)
                },
                onError = { err ->
                    if (cont.isActive) cont.resumeWithException(GoogleAuthException(err))
                }
            )
        }

    fun withFreshToken(onToken: (String?) -> Unit, onError: (String) -> Unit = {}) {
        val state = loadState()
        if (state == null || !state.isAuthorized) {
            onError("Not connected")
            return
        }

        state.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) {
                onError(ex.errorDescription ?: ex.error ?: "Token refresh failed")
            } else {
                try {
                    store.save(state.jsonSerializeString())
                    onToken(accessToken)
                } catch (e: Exception) {
                    onError("Could not save refreshed Google login: ${e.message ?: "storage error"}")
                }
            }
        }
    }

    fun isConnected(): Boolean = loadState()?.isAuthorized == true

    fun disconnect() = store.clear()

    fun close() = authService.dispose()
}

class GoogleAuthException(message: String) : IllegalStateException(message)