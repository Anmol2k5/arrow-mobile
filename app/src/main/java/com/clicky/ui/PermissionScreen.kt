package com.clicky.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clicky.copilot.GuidanceRuntimeController

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { GuidanceRuntimeController(context) }

    var accessibilityGranted by remember { mutableStateOf(false) }
    var overlayGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accessibilityGranted = controller.isAccessibilityServiceEnabled()
        overlayGranted = controller.isOverlayPermissionGranted()
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = controller.isOverlayPermissionGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Clicky Visual Copilot",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!accessibilityGranted) {
            Button(
                onClick = {
                    controller.requestAccessibilityPermission()
                    accessibilityGranted = controller.isAccessibilityServiceEnabled()
                }
            ) {
                Text("Grant Accessibility Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!overlayGranted) {
            Button(
                onClick = {
                    controller.requestOverlayPermission()
                }
            ) {
                Text("Grant Overlay Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (accessibilityGranted && overlayGranted) {
            Button(
                onClick = { onPermissionsGranted() }
            ) {
                Text("Continue")
            }
        }
    }
}
