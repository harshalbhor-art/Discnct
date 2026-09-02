package com.discnct.app.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconstructs foreground sessions from the system's usage *event* stream, rather than reading
 * the pre-aggregated daily buckets.
 *
 * [android.app.usage.UsageStatsManager.queryAndAggregateUsageStats] — what
 * [com.discnct.app.ui.applist.UsageStatsRepository] uses to rank the block list — is fine for
 * "which apps does this person live in", but it can't answer "how much on Tuesday": the buckets it
 * returns don't line up with a range you ask for, so a per-day chart built on it drifts. Walking
 * the raw resume/pause pairs costs more work but the day boundaries are then ours to define, and a
 * session that runs past midnight can be split honestly across both days.
 *
 * Returns an empty list when Usage Access isn't granted (the query just yields no events), so
 * callers see "no data" rather than an error.
 */
class UsageHistoryRepository(private val context: Context) {

    suspend fun sessions(startMs: Long, endMs: Long): List<UsageSession> =
        withContext(Dispatchers.Default) {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext emptyList()

            val events = runCatching { manager.queryEvents(startMs, endMs) }.getOrNull()
                ?: return@withContext emptyList()

            val sessions = mutableListOf<UsageSession>()
            // Only one app is in the foreground at a time, so a single open session is enough
            // state: whatever resumes next implicitly ends whatever was running before it.
            var openPackage: String? = null
            var openStart = 0L

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    EVENT_FOREGROUND -> {
                        // A resume without a matching pause (app killed, device rebooted) would
                        // otherwise run forever; close the previous one here instead.
                        openPackage?.let { sessions.addSession(it, openStart, event.timeStamp) }
                        openPackage = event.packageName
                        openStart = event.timeStamp
                    }
                    EVENT_BACKGROUND -> {
                        if (openPackage == event.packageName) {
                            sessions.addSession(event.packageName, openStart, event.timeStamp)
                            openPackage = null
                        }
                    }
                }
            }

            // Whatever is still open at the end of the window is in the foreground right now.
            openPackage?.let { sessions.addSession(it, openStart, endMs) }

            sessions
        }

    private fun MutableList<UsageSession>.addSession(packageName: String, start: Long, end: Long) {
        // Zero-length and inverted spans are noise — a tap-through leaves a resume and a pause on
        // the same millisecond, and clock changes can run an event stream backwards.
        if (end > start) add(UsageSession(packageName, start, end))
    }

    @Suppress("DEPRECATION")
    private companion object {
        // ACTIVITY_RESUMED / ACTIVITY_PAUSED are API 29+, but carry the same values as the
        // MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND constants they replaced, which work from API 26.
        const val EVENT_FOREGROUND = UsageEvents.Event.MOVE_TO_FOREGROUND
        const val EVENT_BACKGROUND = UsageEvents.Event.MOVE_TO_BACKGROUND
    }
}
