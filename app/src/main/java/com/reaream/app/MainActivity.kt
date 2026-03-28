package com.reaream.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.reaream.app.ui.MainViewModel
import com.reaream.app.ui.ReareamApp
import com.reaream.app.ui.PermissionScreen
import com.reaream.app.ui.theme.ReareamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == OAuthRedirectActivity.ACTION_OAUTH_CALLBACK) {
            intent.data?.let { uri -> viewModel.handleOAuthCallback(uri) }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle OAuth callback if launched from OAuthRedirectActivity
        if (intent?.action == OAuthRedirectActivity.ACTION_OAUTH_CALLBACK) {
            intent.data?.let { uri -> viewModel.handleOAuthCallback(uri) }
        }

        setContent {
            ReareamTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                    )
                )

                if (permissionsState.allPermissionsGranted) {
                    ReareamApp(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PermissionScreen(
                        onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
