package com.discnct.app.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherModeDataStore by preferencesDataStore(name = "launcher_mode")
private val RESTRICTED_MODE_ENABLED = booleanPreferencesKey("restricted_mode_enabled")

/**
 * Persists whether Level 3 enforcement is active. Being registered as the OS Home app is a
 * separate, always-on capability (see [LauncherStatus]) — this flag is the in-app switch for
 * whether Discnct Home narrows itself to the allowed apps and routes blocked ones through the
 * block screen, so a user who has already set Discnct as their Home app can still opt out of the
 * restriction without having to go pick a different launcher in system settings.
 */
class LauncherModeStore(private val context: Context) {

    val restrictedModeEnabled: Flow<Boolean> =
        context.launcherModeDataStore.data.map { it[RESTRICTED_MODE_ENABLED] ?: false }

    suspend fun setRestrictedModeEnabled(enabled: Boolean) {
        context.launcherModeDataStore.edit { prefs -> prefs[RESTRICTED_MODE_ENABLED] = enabled }
    }
}
