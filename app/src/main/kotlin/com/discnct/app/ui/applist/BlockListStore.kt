package com.discnct.app.ui.applist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.blockListDataStore by preferencesDataStore(name = "block_list")
private val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")

/** Persists which package names the user has marked as blocked. */
class BlockListStore(private val context: Context) {

    val blockedPackages: Flow<Set<String>> =
        context.blockListDataStore.data.map { it[BLOCKED_PACKAGES] ?: emptySet() }

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        context.blockListDataStore.edit { prefs ->
            val current = prefs[BLOCKED_PACKAGES] ?: emptySet()
            prefs[BLOCKED_PACKAGES] = if (blocked) current + packageName else current - packageName
        }
    }
}
