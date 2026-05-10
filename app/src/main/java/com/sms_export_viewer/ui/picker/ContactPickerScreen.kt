package com.sms_export_viewer.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    onDone: () -> Unit = {},
    vm: ContactPickerViewModel = viewModel(factory = ContactPickerViewModel.Factory)
) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose contacts") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!state.includeAll) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Selected: ${state.selected.size}",
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { vm.save(onDone) }) { Text("Done") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { vm.save(onDone) }) { Text("Done") }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include all contacts", modifier = Modifier.weight(1f))
                Switch(checked = state.includeAll, onCheckedChange = { vm.toggleIncludeAll() })
            }
            HorizontalDivider()

            if (!state.includeAll) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { vm.setQuery(it) },
                    label = { Text("Search contacts") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true
                )

                if (state.loading) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val q = state.query.trim().lowercase()
                    val filtered = state.contacts.filter {
                        if (q.isEmpty()) true
                        else (it.displayName?.lowercase()?.contains(q) == true) || it.e164.contains(q)
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { it.e164 }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggle(c.e164) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = c.e164 in state.selected,
                                    onCheckedChange = { vm.toggle(c.e164) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.displayName ?: c.e164)
                                    if (c.displayName != null) {
                                        Text(c.e164, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        if (!state.showingAll) {
                            item {
                                TextButton(
                                    onClick = { vm.expandToAll() },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                ) { Text("Show all contacts") }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "All contacts will be included in backups.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
