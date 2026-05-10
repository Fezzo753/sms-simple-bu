package com.sms_export_viewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()
    var phoneOverride by remember(state.phoneOverride) { mutableStateOf(state.phoneOverride ?: "") }
    var fromDateText by remember(state.fromDateText) { mutableStateOf(state.fromDateText) }
    var fromDateEnabled by remember(state.fromDateMs) { mutableStateOf(state.fromDateMs != null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Phone number", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = phoneOverride,
                onValueChange = { phoneOverride = it },
                label = { Text("Override (optional)") },
                supportingText = { Text("Leave blank to use SIM number. Used as the file name suffix.") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("Date filter", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Only back up events from a date forward", modifier = Modifier.weight(1f))
                Switch(
                    checked = fromDateEnabled,
                    onCheckedChange = {
                        fromDateEnabled = it
                        if (!it) fromDateText = ""
                    }
                )
            }
            if (fromDateEnabled) {
                OutlinedTextField(
                    value = fromDateText,
                    onValueChange = { fromDateText = it },
                    label = { Text("From date (YYYY-MM-DD)") },
                    supportingText = { Text("Events earlier than this date are skipped.") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleSmall)
            Text("SimpleBackup ${state.versionName}", style = MaterialTheme.typography.bodyMedium)
            Text("On-device SMS, MMS, and call-log backup.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()

            Text("Danger zone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { showResetConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset backup") }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.save(
                        phoneOverride = phoneOverride.trim().ifBlank { null },
                        fromDateText = if (fromDateEnabled) fromDateText.trim() else null,
                        onDone = onBack
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset backup?") },
            text = { Text("This deletes the local JSON and HTML backup files. Selected contacts and settings are preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    vm.reset()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
