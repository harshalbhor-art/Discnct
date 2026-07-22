package com.discnct.app.service

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, process-wide record of which blocked packages are temporarily allowed
 * (earned via the hold-to-unlock friction in BlockActivity). Deliberately not persisted —
 * surviving a process death/restart isn't a guarantee this app should make about a cooldown.
 */
object BlockCooldown {
    private val allowedUntilElapsedMs = ConcurrentHashMap<String, Long>()

    fun allow(packageName: String, durationMs: Long) {
        allowedUntilElapsedMs[packageName] = SystemClock.elapsedRealtime() + durationMs
    }

    fun isAllowed(packageName: String): Boolean {
        val until = allowedUntilElapsedMs[packageName] ?: return false
        return SystemClock.elapsedRealtime() < until
    }
}
