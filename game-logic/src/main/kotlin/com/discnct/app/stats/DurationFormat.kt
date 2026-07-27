package com.discnct.app.stats

/**
 * Durations for the stats screen, always rounded down to the minute. "4h 12m" reads faster than
 * "4.2 hours", and seconds are noise at this scale — a screen-time figure that ticks is a figure
 * you watch instead of act on.
 */
fun formatDurationMillis(millis: Long): String = formatMinutes((millis / 60_000L).toInt())

/** Same rules, for figures already counted in minutes (game rewards). */
fun formatMinutes(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val rest = safe % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/**
 * The split form the hero readouts use: a big number and a small unit beside it, so the number
 * can be set in the display face without dragging "h"/"m" up to 48sp with it.
 */
fun splitDuration(millis: Long): Pair<String, String> {
    val totalMinutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours" to "h ${minutes}m" else "$minutes" to "m"
}
