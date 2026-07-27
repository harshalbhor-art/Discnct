package com.discnct.app.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.discnct.app.game.GameType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameStatsDataStore by preferencesDataStore(name = "game_stats")

private fun playsKey(type: GameType) = intPreferencesKey("plays_${type.name}")
private fun minutesKey(type: GameType) = intPreferencesKey("minutes_${type.name}")
private val LAST_PLAYED_AT = longPreferencesKey("last_played_at")

/**
 * The running record behind the Stats tab: how often each game has been played and how many
 * minutes it has bought back.
 *
 * Only games finished from the block screen are recorded. The TRY previews in Section 2 stay out
 * of this deliberately — a try earns no unlock time, so counting it would inflate a score that is
 * supposed to mean "time I actually won back".
 */
class GameStatsStore(private val context: Context) {

    /** One entry per game, including never-played ones, so the breakdown lists the full roster. */
    val tallies: Flow<List<GameTally>> =
        context.gameStatsDataStore.data.map { prefs ->
            GameType.entries.map { type ->
                GameTally(
                    key = type.name,
                    plays = prefs[playsKey(type)] ?: 0,
                    minutesEarned = prefs[minutesKey(type)] ?: 0,
                )
            }
        }

    /** Epoch millis of the most recent recorded play, or null if nothing has been played yet. */
    val lastPlayedAt: Flow<Long?> =
        context.gameStatsDataStore.data.map { it[LAST_PLAYED_AT] }

    suspend fun recordPlay(type: GameType, minutesEarned: Int, atMillis: Long = System.currentTimeMillis()) {
        context.gameStatsDataStore.edit { prefs ->
            prefs[playsKey(type)] = (prefs[playsKey(type)] ?: 0) + 1
            prefs[minutesKey(type)] = (prefs[minutesKey(type)] ?: 0) + minutesEarned.coerceAtLeast(0)
            prefs[LAST_PLAYED_AT] = atMillis
        }
    }

    suspend fun reset() {
        context.gameStatsDataStore.edit { it.clear() }
    }
}
