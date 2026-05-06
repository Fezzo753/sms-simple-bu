package com.simplebackup.app.ui.picker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simplebackup.app.AppContainer
import com.simplebackup.app.SimpleBackupApplication
import com.simplebackup.app.data.DiscoveredContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(
    val contacts: List<DiscoveredContact> = emptyList(),
    val selected: Set<String> = emptySet(),
    val includeAll: Boolean = false,
    val showingAll: Boolean = false,
    val query: String = "",
    val loading: Boolean = false
)

class ContactPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val container: AppContainer = (app as SimpleBackupApplication).container
    private val _state = MutableStateFlow(PickerState())
    val state: StateFlow<PickerState> = _state.asStateFlow()

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(loading = true) }
        val s = container.settings.current()
        val active = runCatching { container.discovery.discoverActiveContacts() }.getOrDefault(emptyList())
        _state.value = PickerState(
            contacts = active,
            selected = s.selectedE164,
            includeAll = s.includeAll,
            showingAll = false,
            query = "",
            loading = false
        )
    }

    fun toggle(e164: String) {
        _state.update {
            val s = it.selected.toMutableSet()
            if (e164 in s) s.remove(e164) else s.add(e164)
            it.copy(selected = s)
        }
    }

    fun toggleIncludeAll() {
        _state.update { it.copy(includeAll = !it.includeAll) }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun expandToAll() = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(loading = true) }
        val all = runCatching { container.discovery.loadAllContacts() }.getOrDefault(emptyList())
        _state.update { it.copy(contacts = all, showingAll = true, loading = false) }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        container.settings.update { it.copy(selectedE164 = s.selected, includeAll = s.includeAll) }
        onDone()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ContactPickerViewModel(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                )
            }
        }
    }
}
