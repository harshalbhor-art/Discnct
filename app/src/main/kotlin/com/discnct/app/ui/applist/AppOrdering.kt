package com.discnct.app.ui.applist

/**
 * Well-known social / short-form apps that get pinned to the top of the block lists, in this
 * order — these are the ones people usually come here to blunt, so they shouldn't have to scroll
 * or search to find them. Kept as a plain, easily-extended table.
 */
val SOCIAL_PRIORITY: List<String> = listOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",   // TikTok
    "com.ss.android.ugc.trill",   // TikTok (alternate regional build)
    "com.facebook.katana",
    "com.google.android.youtube",
    "com.snapchat.android",
    "com.twitter.android",
    "com.x.android",              // X (formerly Twitter)
    "com.reddit.frontpage",
    "com.pinterest",
    "com.facebook.orca",          // Messenger
    "org.telegram.messenger",
    "com.whatsapp",
    "com.linkedin.android",
)

private val socialRank: Map<String, Int> =
    SOCIAL_PRIORITY.withIndex().associate { (index, pkg) -> pkg to index }

/**
 * Ordering for both app lists (Section 1 and Section 2): pinned social apps first in
 * [SOCIAL_PRIORITY] order, then every other app by most foreground time (from Usage Access),
 * then alphabetically as a stable tiebreaker when usage is unknown or equal.
 */
fun appListComparator(usageMillis: Map<String, Long>): Comparator<InstalledApp> =
    compareBy<InstalledApp> { socialRank[it.packageName] ?: Int.MAX_VALUE }
        .thenByDescending { usageMillis[it.packageName] ?: 0L }
        .thenBy { it.label.lowercase() }

/** Compact label for a foreground-time duration, e.g. "3h 12m", "45m", or "" for none. */
fun formatUsage(millis: Long): String {
    if (millis <= 0L) return ""
    val totalMinutes = millis / 60_000L
    if (totalMinutes < 1L) return "<1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
