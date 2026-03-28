package com.reaream.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Handles the OAuth redirect from the browser after YouTube authentication.
 * The redirect URI scheme is the reversed OAuth client ID (e.g., com.googleusercontent.apps.XXX).
 * This activity captures the redirect, extracts the authorization code, and forwards it to MainActivity.
 */
class OAuthRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null) {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OAUTH_CALLBACK
                data = uri
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainIntent)
        }
        finish()
    }

    companion object {
        const val ACTION_OAUTH_CALLBACK = "com.reaream.app.OAUTH_CALLBACK"
    }
}
