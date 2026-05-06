package com.simplebackup.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simplebackup.app.AppContainer
import com.simplebackup.app.SimpleBackupApplication
import com.simplebackup.app.backup.BackupProgress
import com.simplebackup.app.core.Backup
import com.simplebackup.app.core.Event
import com.simplebackup.app.data.DiscoveredContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeState(
    val devicePhone: String = "",
    val lastBackupMs: Long? = null,
    val totalMessages: Int = 0,
    val totalCalls: Int = 0,
    val selectedContacts: List<DiscoveredContact> = emptyList(),
    val canBackup: Boolean = false,
    val progress: BackupProgress = BackupProgress.Idle,
    val htmlExists: Boolean = false
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val container: AppContainer = (app as SimpleBackupApplication).container
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var backupJob: Job? = null

    fun start() = viewModelScope.launch(Dispatchers.IO) {
        val s = container.settings.current()
        val devicePhone = container.resolveDevicePhone(s.phoneOverride)
        val nameMap = runCatching { container.discovery.nameMap() }.getOrDefault(emptyMap())
        val selectedContacts = s.selectedE164.map {
            DiscoveredContact(it, nameMap[it])
        }.sortedBy { it.displayName ?: it.e164 }
        val (msgCount, callCount) = loadCounts(devicePhone)
        val htmlExists = File(container.filesDir, "Backup_${devicePhone}.html").exists()
        _state.value = HomeState(
            devicePhone = devicePhone,
            lastBackupMs = s.lastRunMs,
            totalMessages = msgCount,
            totalCalls = callCount,
            selectedContacts = selectedContacts,
            canBackup = s.selectedE164.isNotEmpty() || s.includeAll,
            progress = BackupProgress.Idle,
            htmlExists = htmlExists
        )
    }

    fun backupNow() {
        if (backupJob?.isActive == true) return
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            val s = container.settings.current()
            val devicePhone = container.resolveDevicePhone(s.phoneOverride)
            val nameMap = runCatching { container.discovery.nameMap() }.getOrDefault(emptyMap())
            val orchestrator = container.orchestrator(devicePhone, nameMap)
            launch {
                orchestrator.progress.collect { p ->
                    _state.value = _state.value.copy(progress = p)
                }
            }
            val addresses = if (s.includeAll) emptySet() else s.selectedE164
            val result = orchestrator.run(addresses)
            if (result.isSuccess) {
                container.settings.update { it.copy(lastRunMs = System.currentTimeMillis()) }
                start()
            }
        }
    }

    fun cancelBackup() {
        backupJob?.cancel()
        _state.value = _state.value.copy(progress = BackupProgress.Idle)
    }

    fun dismissCompletion() {
        if (_state.value.progress is BackupProgress.Done || _state.value.progress is BackupProgress.Failed) {
            _state.value = _state.value.copy(progress = BackupProgress.Idle)
        }
    }

    private suspend fun loadCounts(devicePhone: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val f = File(container.filesDir, "Backup_${devicePhone}.json")
        if (!f.exists()) return@withContext 0 to 0
        runCatching {
            val backup = container.json.decodeFromString(Backup.serializer(), f.readText())
            val msg = backup.events.count { it is Event.Sms || it is Event.Mms }
            val calls = backup.events.count { it is Event.Call }
            msg to calls
        }.getOrDefault(0 to 0)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}
