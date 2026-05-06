package com.simplebackup.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simplebackup.app.AppContainer
import com.simplebackup.app.SimpleBackupApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class SettingsUiState(
    val phoneOverride: String? = null,
    val fromDateMs: Long? = null,
    val fromDateText: String = "",
    val versionName: String = "0.1.0"
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container: AppContainer = (app as SimpleBackupApplication).container
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun load() = viewModelScope.launch {
        val app = getApplication<Application>()
        val versionName = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.1.0"
        }.getOrDefault("0.1.0")
        val s = withContext(Dispatchers.IO) { container.settings.current() }
        _state.value = SettingsUiState(
            phoneOverride = s.phoneOverride,
            fromDateMs = s.fromDateMs,
            fromDateText = s.fromDateMs?.let { dateFmt.format(java.util.Date(it)) } ?: "",
            versionName = versionName
        )
    }

    fun save(phoneOverride: String?, fromDateText: String?, onDone: () -> Unit) =
        viewModelScope.launch {
            val parsedFromDate = fromDateText
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { dateFmt.parse(it)?.time }.getOrNull() }
            withContext(Dispatchers.IO) {
                container.settings.update {
                    it.copy(phoneOverride = phoneOverride, fromDateMs = parsedFromDate)
                }
            }
            onDone()
        }

    fun reset() = viewModelScope.launch(Dispatchers.IO) {
        val s = container.settings.current()
        val devicePhone = container.resolveDevicePhone(s.phoneOverride)
        File(container.filesDir, "Backup_${devicePhone}.json").delete()
        File(container.filesDir, "Backup_${devicePhone}.html").delete()
        container.settings.update { it.copy(lastRunMs = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                )
            }
        }
    }
}
