package com.discnct.app.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.feedBlockDataStore by preferencesDataStore(name = "feed_block_list")
private val FEED_BLOCKED_PACKAGES = stringSetPreferencesKey("feed_blocked_packages")

/**
 * Persists which apps should have their scrolling feed hidden. The second switch of Level 1, kept
 * separate from [ReelBlockStore] because the two are genuinely independent: plenty of people want
 * Reels gone but still read the timeline, and the reverse is just as common.
 */
class FeedBlockStore(private val context: Context) {

    val feedBlockedPackages: Flow<Set<String>> =
        context.feedBlockDataStore.data.map { it[FEED_BLOCKED_PACKAGES] ?: emptySet() }

    suspend fun setFeedBlocked(packageName: String, blocked: Boolean) {
        context.feedBlockDataStore.edit { prefs ->
            val current = prefs[FEED_BLOCKED_PACKAGES] ?: emptySet()
            prefs[FEED_BLOCKED_PACKAGES] = if (blocked) current + packageName else current - packageName
        }
    }
}
