package com.reaream.app

import android.Manifest
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

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
