package com.discnct.app.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reelBlockDataStore by preferencesDataStore(name = "reel_block_list")
private val REEL_BLOCKED_PACKAGES = stringSetPreferencesKey("reel_blocked_packages")

/**
 * Persists which apps should have their Reels/Shorts feed blocked *surgically* — the rest of the
 * app stays usable. Kept separate from [com.discnct.app.ui.applist.BlockListStore] (whole-app
 * blocking) so a user can, say, allow Instagram DMs but bounce out of Reels.
 */
class ReelBlockStore(private val context: Context) {

    val reelBlockedPackages: Flow<Set<String>> =
        context.reelBlockDataStore.data.map { it[REEL_BLOCKED_PACKAGES] ?: emptySet() }

    suspend fun setReelBlocked(packageName: String, blocked: Boolean) {
        context.reelBlockDataStore.edit { prefs ->
            val current = prefs[REEL_BLOCKED_PACKAGES] ?: emptySet()
            prefs[REEL_BLOCKED_PACKAGES] = if (blocked) current + packageName else current - packageName
        }
    }
}
