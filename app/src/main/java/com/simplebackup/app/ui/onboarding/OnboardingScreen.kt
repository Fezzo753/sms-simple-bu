package com.simplebackup.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simplebackup.app.ui.permissions.REQUIRED_PERMISSIONS
import com.simplebackup.app.ui.permissions.missingPermissions
import com.simplebackup.app.ui.permissions.openAppSettings
import com.simplebackup.app.ui.permissions.rationaleFor

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf<Set<String>>(emptySet()) }

    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isEmpty()) {
            onContinue()
        } else {
            permanentlyDenied = denied
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome to SimpleBackup", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick contacts. Tap one button. Get a single HTML file with every message and call to those contacts.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Text("Why we need three permissions:", style = MaterialTheme.typography.titleSmall)
        REQUIRED_PERMISSIONS.forEach { perm ->
            Text("• ${rationaleFor(perm)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Files are saved inside the app's private storage, so uninstalling the app deletes them. Use the Share button on the home screen to copy a backup elsewhere.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))

        if (permanentlyDenied.isNotEmpty()) {
            Text(
                "Some permissions were denied:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            permanentlyDenied.forEach { Text("• ${rationaleFor(it)}") }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Settings") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val missing = missingPermissions(context)
                    if (missing.isEmpty()) onContinue() else launcher.launch(missing.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Try again") }
        } else {
            Button(
                onClick = {
                    val missing = missingPermissions(context)
                    if (missing.isEmpty()) onContinue() else launcher.launch(missing.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue") }
        }
    }
}
