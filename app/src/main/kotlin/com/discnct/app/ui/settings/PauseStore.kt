package com.discnct.app.ui.settings

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pauseDataStore by preferencesDataStore(name = "pause")
private val PAUSED_UNTIL = longPreferencesKey("paused_until")
private val PAUSED_AT_ELAPSED = longPreferencesKey("paused_at_elapsed")
private val PAUSE_DURATION_MS = longPreferencesKey("pause_duration_ms")

/** The three numbers [com.discnct.app.pause.isPauseActive] needs to tell whether a pause is still
 * running without trusting the device's wall clock alone. */
data class PauseWindow(
    val untilWallMillis: Long,
    val atElapsedMillis: Long,
    val durationMillis: Long,
)

/**
 * "Pause everything" — a planned, time-boxed exception (e.g. "I need Instagram for 15 minutes
 * to post something"), distinct from turning a blocker off outright. Capped at [MAX_PAUSE_MINUTES]
 * per request so it can't become a silent permanent off switch, matching how paid blockers gate
 * pausing. Persisted (not in-memory like BlockCooldown) so a pause survives the app being killed.
 *
 * The deadline is stored both as wall-clock time (so Settings can show "paused until 3:45") and as
 * an elapsed-realtime anchor + duration, so enforcement can check [com.discnct.app.pause.isPauseActive]
 * instead of comparing against the wall clock directly — the wall clock is something the user can
 * set backward to keep a pause looking alive well past its real 60-minute cap.
 */
class PauseStore(private val context: Context) {
    /** 0 means not paused. Emits the raw epoch-millis deadline for display; enforcement should use
     * [window] instead, which is resistant to the device clock being changed. */
    val pausedUntilMillis: Flow<Long> = context.pauseDataStore.data.map { it[PAUSED_UNTIL] ?: 0L }

    val window: Flow<PauseWindow> = context.pauseDataStore.data.map {
        PauseWindow(
            untilWallMillis = it[PAUSED_UNTIL] ?: 0L,
            atElapsedMillis = it[PAUSED_AT_ELAPSED] ?: 0L,
            durationMillis = it[PAUSE_DURATION_MS] ?: 0L,
        )
    }

    suspend fun pauseFor(minutes: Int) {
        val capped = minutes.coerceIn(1, MAX_PAUSE_MINUTES)
        val durationMs = capped * 60_000L
        context.pauseDataStore.edit {
            it[PAUSED_UNTIL] = System.currentTimeMillis() + durationMs
            it[PAUSED_AT_ELAPSED] = SystemClock.elapsedRealtime()
            it[PAUSE_DURATION_MS] = durationMs
        }
    }

    suspend fun resumeNow() {
        context.pauseDataStore.edit {
            it[PAUSED_UNTIL] = 0L
            it[PAUSED_AT_ELAPSED] = 0L
            it[PAUSE_DURATION_MS] = 0L
        }
    }

    companion object {
        const val MAX_PAUSE_MINUTES = 60
    }
}
