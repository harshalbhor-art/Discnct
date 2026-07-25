package com.discnct.app.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.allowedAppsDataStore by preferencesDataStore(name = "allowed_apps")
private val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
private val HAS_BEEN_CONFIGURED = booleanPreferencesKey("has_been_configured")

/**
 * The short list of apps Discnct Home will show while the restricted launcher is on.
 *
 * [allowedPackages] is null until the user has actually made a choice — which is different from
 * having chosen nothing. An unconfigured list falls back to the device's essentials (see
 * [EssentialApps]) so that setting Discnct as your Home app can never strand you on a blank
 * screen with no way to call anyone; an explicitly emptied list is respected as written.
 */
class AllowedAppsStore(private val context: Context) {

    val allowedPackages: Flow<Set<String>?> =
        context.allowedAppsDataStore.data.map { prefs ->
            if (prefs[HAS_BEEN_CONFIGURED] == true) prefs[ALLOWED_PACKAGES] ?: emptySet() else null
        }

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        context.allowedAppsDataStore.edit { prefs ->
            val current = prefs[ALLOWED_PACKAGES] ?: emptySet()
            prefs[ALLOWED_PACKAGES] = if (allowed) current + packageName else current - packageName
            prefs[HAS_BEEN_CONFIGURED] = true
        }
    }

    /** Write the essentials in as a real, editable selection the first time the picker opens. */
    suspend fun seedIfUnconfigured(seed: Set<String>) {
        context.allowedAppsDataStore.edit { prefs ->
            if (prefs[HAS_BEEN_CONFIGURED] != true) {
                prefs[ALLOWED_PACKAGES] = seed
                prefs[HAS_BEEN_CONFIGURED] = true
            }
        }
    }
}
