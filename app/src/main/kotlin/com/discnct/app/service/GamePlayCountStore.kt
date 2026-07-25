package com.discnct.app.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.discnct.app.game.GameType
import com.discnct.app.game.logic.availableGames
import com.discnct.app.game.logic.countsForDay
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gamePlayCountDataStore by preferencesDataStore(name = "game_play_counts")
private val COUNTED_DAY = longPreferencesKey("counted_day")
private fun playsKey(type: GameType) = intPreferencesKey("plays_${type.name}")

/**
 * How many times each puzzle has been opened today. The stored day is written alongside the
 * counts, so a rollover is handled on read (see [countsForDay]) — no alarm or background job is
 * needed to zero these out at midnight, which matters for an app whose whole point is that it
 * keeps working while you're not looking at it.
 *
 * Counting starts when a game *opens*, not when it's won: otherwise backing out of a puzzle you
 * don't like would be a free reroll, and the cap would mean nothing.
 */
class GamePlayCountStore(private val context: Context) {

    val playsToday: Flow<Map<GameType, Int>> =
        context.gamePlayCountDataStore.data.map { prefs ->
            val stored = GameType.entries.associate { it.name to (prefs[playsKey(it)] ?: 0) }
            val fresh = countsForDay(
                storedDay = prefs[COUNTED_DAY] ?: 0L,
                today = todayEpochDay(),
                stored = stored,
            )
            GameType.entries.associateWith { fresh[it.name] ?: 0 }
        }

    suspend fun recordPlay(type: GameType) {
        context.gamePlayCountDataStore.edit { prefs ->
            val today = todayEpochDay()
            if (prefs[COUNTED_DAY] != today) {
                // First play of a new day: clear yesterday's numbers before counting this one.
                GameType.entries.forEach { prefs.remove(playsKey(it)) }
                prefs[COUNTED_DAY] = today
            }
            prefs[playsKey(type)] = (prefs[playsKey(type)] ?: 0) + 1
        }
    }

    private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}

/**
 * The games the block screen may offer right now: switched on by the user *and* still under their
 * daily cap. Timed games (Breathing, Fidget Spinner) have no cap, so they never drop out.
 */
fun playableGames(enabled: Set<GameType>, playsToday: Map<GameType, Int>): Set<GameType> {
    val names = availableGames(
        enabled = enabled.mapTo(mutableSetOf()) { it.name },
        caps = GameType.entries.associate { it.name to it.dailyPlayCap },
        playsToday = playsToday.entries.associate { (type, plays) -> type.name to plays },
    )
    return GameType.entries.filterTo(mutableSetOf()) { it.name in names }
}
