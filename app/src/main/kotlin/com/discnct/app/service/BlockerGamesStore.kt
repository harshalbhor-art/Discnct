package com.discnct.app.service

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.discnct.app.game.GameType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.blockerGamesDataStore by preferencesDataStore(name = "blocker_games")
private val REEL_BLOCKING_ENABLED = booleanPreferencesKey("reel_blocking_enabled")
private val ENABLED_GAMES = stringSetPreferencesKey("enabled_games")

/**
 * Settings shared by the two levels that use games:
 *
 *  - [reelBlockingEnabled] is Level 1's master switch for the reel/shorts blocker. When off, the
 *    accessibility service stops interrupting Reels feeds even if per-app toggles are on.
 *    Defaults on so a fresh install that flips a per-app toggle "just works".
 *  - [enabledGames] is the pool of mini-games the block screen may offer as a "play to earn time"
 *    option, configured in Level 2 and drawn on by both levels. Defaults to every game. The
 *    setter refuses to empty the pool, so there is always at least one game to fall back to.
 *
 * How often each game may be played is a separate concern — see GamePlayCountStore.
 */
class BlockerGamesStore(private val context: Context) {

    val reelBlockingEnabled: Flow<Boolean> =
        context.blockerGamesDataStore.data.map { it[REEL_BLOCKING_ENABLED] ?: true }

    val enabledGames: Flow<Set<GameType>> =
        context.blockerGamesDataStore.data.map { prefs ->
            val stored = prefs[ENABLED_GAMES]
            if (stored == null) {
                GameType.entries.toSet()
            } else {
                stored.mapNotNull { name -> runCatching { GameType.valueOf(name) }.getOrNull() }.toSet()
            }
        }

    suspend fun setReelBlockingEnabled(enabled: Boolean) {
        context.blockerGamesDataStore.edit { it[REEL_BLOCKING_ENABLED] = enabled }
    }

    suspend fun setGameEnabled(type: GameType, enabled: Boolean) {
        context.blockerGamesDataStore.edit { prefs ->
            val current = (prefs[ENABLED_GAMES] ?: GameType.entries.map { it.name }.toSet()).toMutableSet()
            if (enabled) current.add(type.name) else current.remove(type.name)
            // Never let the pool go empty — a block screen with no game to offer is a dead end.
            if (current.isNotEmpty()) prefs[ENABLED_GAMES] = current
        }
    }
}
