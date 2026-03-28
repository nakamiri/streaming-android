package com.reaream.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

class YouTubeAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "YouTubeAuth"
        private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val PREFS_NAME = "youtube_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRY_TIME = "expiry_time"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }

    private val clientId: String = com.reaream.app.BuildConfig.YOUTUBE_CLIENT_ID
    private val clientSecret: String = com.reaream.app.BuildConfig.YOUTUBE_CLIENT_SECRET
    private val redirectUri: String = clientId.split(".").reversed().joinToString(".") + ":/"

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isConfigured(): Boolean = clientId.isNotBlank()

    /**
     * Launch Chrome Custom Tab with Google OAuth page.
     * Users can switch to brand accounts on Google's web UI.
     */
    fun launchAuthFlow(context: android.app.Activity) {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        // Store code_verifier for later token exchange
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()

        val authUrl = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", YOUTUBE_SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        Log.d(TAG, "Launching auth flow, redirect_uri=$redirectUri")

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUrl)
    }

    /**
     * Handle the OAuth redirect callback. Extract auth code and exchange for tokens.
     */
    suspend fun handleRedirect(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Log.e(TAG, "OAuth error: $error")
            return false
        }

        if (code == null) {
            Log.e(TAG, "No auth code in redirect")
            return false
        }

        val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
        if (codeVerifier == null) {
            Log.e(TAG, "No code_verifier found")
            return false
        }

        return exchangeCodeForTokens(code, codeVerifier)
    }

    private suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("code", code)
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("redirect_uri", redirectUri)
                    .add("grant_type", "authorization_code")
                    .add("code_verifier", codeVerifier)
                    .build()

                val request = Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext false

                if (!response.isSuccessful) {
                    Log.e(TAG, "Token exchange failed: $responseBody")
                    return@withContext false
                }

                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                val accessToken = jsonObj["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext false
                val refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600

                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putLong(KEY_EXPIRY_TIME, System.currentTimeMillis() + expiresIn * 1000)
                    .apply {
                        if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
                    }
                    .remove(KEY_CODE_VERIFIER) // Clean up
                    .apply()

                Log.d(TAG, "Token exchange successful")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error", e)
                false
            }
        }

    suspend fun getValidAccessToken(): String? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiryTime = prefs.getLong(KEY_EXPIRY_TIME, 0)

        if (System.currentTimeMillis() > expiryTime - 5 * 60 * 1000) {
            return refreshAccessToken()
        }

        return accessToken
    }

    private suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: run {
            Log.e(TAG, "No refresh token, re-auth needed")
            return@withContext null
        }

        try {
            val body = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "Token refresh failed: $responseBody")
                return@withContext null
            }

            val jsonObj = json.parseToJsonElement(responseBody).jsonObject
            val newAccessToken = jsonObj["access_token"]?.jsonPrimitive?.content
                ?: return@withContext null
            val expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600

            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newAccessToken)
                .putLong(KEY_EXPIRY_TIME, System.currentTimeMillis() + expiresIn * 1000)
                .apply()

            Log.d(TAG, "Token refresh successful")
            newAccessToken
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            null
        }
    }

    fun isSignedIn(): Boolean = prefs.getString(KEY_ACCESS_TOKEN, null) != null

    fun getChannelName(): String? = prefs.getString(KEY_CHANNEL_NAME, null)

    fun setChannelName(name: String) {
        prefs.edit().putString(KEY_CHANNEL_NAME, name).apply()
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
