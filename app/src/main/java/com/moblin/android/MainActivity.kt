package com.moblin.android

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
import com.moblin.android.ui.MainViewModel
import com.moblin.android.ui.MoblinApp
import com.moblin.android.ui.PermissionScreen
import com.moblin.android.ui.theme.MoblinTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoblinTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                    )
                )

                if (permissionsState.allPermissionsGranted) {
                    MoblinApp(
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
