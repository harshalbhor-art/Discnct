package com.discnct.app.ui.applist

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Reads how long each app has been in the foreground, using the Usage Access permission
 * (PACKAGE_USAGE_STATS). We only need a coarse "which apps does this person actually live in"
 * signal to rank the block lists, so a single aggregated query over a recent window is enough.
 *
 * Returns an empty map when the permission isn't granted (the query silently yields nothing),
 * so callers can treat "no usage data" and "permission off" the same: fall back to name order.
 */
class UsageStatsRepository(private val context: Context) {

    suspend fun foregroundMillisByPackage(days: Int = USAGE_WINDOW_DAYS): Map<String, Long> =
        withContext(Dispatchers.Default) {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@withContext emptyMap()

            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(days.toLong())

            val stats = runCatching {
                usageStatsManager.queryAndAggregateUsageStats(start, end)
            }.getOrNull().orEmpty()

            stats
                .mapValues { (_, usage) -> usage.totalTimeInForeground }
                .filterValues { it > 0L }
        }

    private companion object {
        const val USAGE_WINDOW_DAYS = 7
    }
}
