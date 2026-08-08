package com.discnct.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pauseDataStore by preferencesDataStore(name = "pause")
private val PAUSED_UNTIL = longPreferencesKey("paused_until")

/**
 * "Pause everything" — a planned, time-boxed exception (e.g. "I need Instagram for 15 minutes
 * to post something"), distinct from turning a blocker off outright. Capped at [MAX_PAUSE_MINUTES]
 * per request so it can't become a silent permanent off switch, matching how paid blockers gate
 * pausing. Persisted (not in-memory like BlockCooldown) so a pause survives the app being killed.
 */
class PauseStore(private val context: Context) {
    /** 0 means not paused. Emits the raw epoch-millis deadline; callers compare to "now". */
    val pausedUntilMillis: Flow<Long> = context.pauseDataStore.data.map { it[PAUSED_UNTIL] ?: 0L }

    suspend fun pauseFor(minutes: Int) {
        val capped = minutes.coerceIn(1, MAX_PAUSE_MINUTES)
        context.pauseDataStore.edit { it[PAUSED_UNTIL] = System.currentTimeMillis() + capped * 60_000L }
    }

    suspend fun resumeNow() {
        context.pauseDataStore.edit { it[PAUSED_UNTIL] = 0L }
    }

    companion object {
        const val MAX_PAUSE_MINUTES = 60
    }
}
