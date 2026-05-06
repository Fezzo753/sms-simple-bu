package com.simplebackup.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplebackup.app.backup.BackupProgress
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEditContacts: () -> Unit = {},
    onView: () -> Unit = {},
    onShare: (String) -> Unit = {},
    onSettings: () -> Unit = {},
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    LaunchedEffect(Unit) { vm.start() }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.progress) {
        when (val p = state.progress) {
            is BackupProgress.Done -> {
                snackbarHostState.showSnackbar("Added ${p.newMessages} new messages and ${p.newCalls} new calls.")
                vm.dismissCompletion()
            }
            is BackupProgress.Failed -> {
                val r = snackbarHostState.showSnackbar(
                    message = "Backup failed: ${p.message}",
                    actionLabel = "Try again"
                )
                vm.dismissCompletion()
                if (r == SnackbarResult.ActionPerformed) vm.backupNow()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SimpleBackup",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (state.devicePhone.isNotBlank() && state.devicePhone != "+0000000000") state.devicePhone
                        else "Phone number not detected",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Last backup: " + (state.lastBackupMs?.let {
                            DateFormat.getDateTimeInstance().format(Date(it))
                        } ?: "never"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${state.totalMessages} messages · ${state.totalCalls} calls",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Contacts", style = MaterialTheme.typography.titleSmall)
            if (state.selectedContacts.isEmpty()) {
                Text(
                    "No contacts selected. Tap 'Edit contacts' to choose.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectedContacts.take(20).forEach { c ->
                        AssistChip(
                            onClick = onEditContacts,
                            label = { Text(c.displayName ?: c.e164) }
                        )
                    }
                    if (state.selectedContacts.size > 20) {
                        AssistChip(
                            onClick = onEditContacts,
                            label = { Text("+${state.selectedContacts.size - 20} more") }
                        )
                    }
                }
            }
            TextButton(onClick = onEditContacts) { Text("Edit contacts ›") }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.backupNow() },
                enabled = state.canBackup && state.progress !is BackupProgress.Running,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(
                    when (val p = state.progress) {
                        is BackupProgress.Running -> p.phase.label + "…"
                        else -> "Back up now"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onView,
                    enabled = state.htmlExists,
                    modifier = Modifier.weight(1f)
                ) { Text("View") }
                OutlinedButton(
                    onClick = { onShare(state.devicePhone) },
                    enabled = state.htmlExists,
                    modifier = Modifier.weight(1f)
                ) { Text("Share") }
            }

            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "Backups are stored inside this app's private storage. Uninstalling the app deletes them. Use Share to copy a backup elsewhere.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.size(48.dp))
        }
    }

    val progress = state.progress
    if (progress is BackupProgress.Running) {
        BackupProgressSheet(progress = progress, onCancel = { vm.cancelBackup() })
    }
}
