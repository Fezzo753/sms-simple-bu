package com.sms_export_viewer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val selectedE164: Set<String>,
    val includeAll: Boolean,
    val fromDateMs: Long?,
    val phoneOverride: String?,
    val lastRunMs: Long?
)

class SettingsRepository(private val context: Context) {
    private val K_SELECTED = stringSetPreferencesKey("selected_e164")
    private val K_INCLUDE_ALL = booleanPreferencesKey("include_all")
    private val K_FROM_DATE = longPreferencesKey("from_date_ms")
    private val K_PHONE_OVR = stringPreferencesKey("phone_override")
    private val K_LAST_RUN = longPreferencesKey("last_run")

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            selectedE164 = p[K_SELECTED] ?: emptySet(),
            includeAll = p[K_INCLUDE_ALL] ?: false,
            fromDateMs = p[K_FROM_DATE],
            phoneOverride = p[K_PHONE_OVR],
            lastRunMs = p[K_LAST_RUN]
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(
                selectedE164 = p[K_SELECTED] ?: emptySet(),
                includeAll = p[K_INCLUDE_ALL] ?: false,
                fromDateMs = p[K_FROM_DATE],
                phoneOverride = p[K_PHONE_OVR],
                lastRunMs = p[K_LAST_RUN]
            )
            val n = transform(cur)
            p[K_SELECTED] = n.selectedE164
            p[K_INCLUDE_ALL] = n.includeAll
            n.fromDateMs?.let { p[K_FROM_DATE] = it } ?: p.remove(K_FROM_DATE)
            n.phoneOverride?.let { p[K_PHONE_OVR] = it } ?: p.remove(K_PHONE_OVR)
            n.lastRunMs?.let { p[K_LAST_RUN] = it } ?: p.remove(K_LAST_RUN)
        }
    }
}
