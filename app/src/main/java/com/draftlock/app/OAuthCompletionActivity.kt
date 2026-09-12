package com.draftlock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Receives AppAuth's completion PendingIntent outside of Compose/MainActivity.
 * The OAuth state is persisted before the app is recreated, so the normal
 * startup path can deterministically render the connected state.
 */
class OAuthCompletionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val oauthManager = GoogleOAuthManager(this)
        oauthManager.handleResult(intent) { ok, message ->
            Log.i("DraftLock", "OAuth completion ok=$ok message=$message")

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            mainIntent.putExtra("oauth_result_ok", ok)
            mainIntent.putExtra("oauth_result_message", message)
            startActivity(mainIntent)
            finish()
        }
    }
}
