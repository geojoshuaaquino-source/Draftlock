package com.draftlock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Completes the AppAuth flow after Google redirects back through the
 * configured custom scheme, then shows the result before returning to DraftLock.
 */
class OAuthCompletionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val oauthManager = GoogleOAuthManager(this)
        oauthManager.handleResult(intent) { ok, message ->
            Log.i("DraftLock", "OAuth completion ok=$ok message=$message")
            showOAuthResult(ok, message)
        }
    }

    private fun showOAuthResult(ok: Boolean, message: String) {
        val title = if (ok) "Google sign-in successful" else "Google sign-in failed"
        val body = if (ok) {
            "Your Google account is now connected."
        } else {
            message.ifBlank { "Google sign-in could not be completed." }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> returnToApp() }
            .show()
    }

    private fun returnToApp() {
        val appIntent = Intent(this, MockupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(appIntent)
        finish()
    }
}
