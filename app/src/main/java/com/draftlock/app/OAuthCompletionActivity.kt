package com.draftlock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Completes the AppAuth flow after Google redirects back through the
 * configured custom scheme, then returns to the mockup UI.
 */
class OAuthCompletionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val oauthManager = GoogleOAuthManager(this)
        oauthManager.handleResult(intent) { ok, message ->
            Log.i("DraftLock", "OAuth completion ok=$ok message=$message")

            val appIntent = Intent(this, MockupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("oauth_result_ok", ok)
                putExtra("oauth_result_message", message)
            }
            startActivity(appIntent)
            finish()
        }
    }
}
