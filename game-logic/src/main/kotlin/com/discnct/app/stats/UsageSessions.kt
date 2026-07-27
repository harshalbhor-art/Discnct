package com.discnct.app.stats

/**
 * One stretch of an app being in the foreground, as reconstructed from the system's usage
 * events. Half-open: [startMs, endMs).
 */
data class UsageSession(
    val packageName: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * How much of [session] falls inside [windowStart, windowEnd). Sessions routinely straddle a
 * day boundary — you open a video at 23:55 and put the phone down at 00:20 — so a day's total
 * has to count the slice, not the whole session or nothing.
 */
fun overlapMillis(session: UsageSession, windowStart: Long, windowEnd: Long): Long {
    val start = maxOf(session.startMs, windowStart)
    val end = minOf(session.endMs, windowEnd)
    return (end - start).coerceAtLeast(0L)
}

/** Foreground time per package inside the window, dropping packages with nothing in it. */
fun millisByPackage(
    sessions: List<UsageSession>,
    windowStart: Long,
    windowEnd: Long,
): Map<String, Long> {
    val totals = mutableMapOf<String, Long>()
    for (session in sessions) {
        val slice = overlapMillis(session, windowStart, windowEnd)
        if (slice > 0L) {
            totals[session.packageName] = (totals[session.packageName] ?: 0L) + slice
        }
    }
    return totals
}

/**
 * Total foreground time per bucket. [boundaries] is ascending and one longer than the bucket
 * count — `[day0, day1, day2]` describes two days. Time outside the outermost bounds is dropped,
 * and every bucket is reported even when empty so a chart keeps its shape.
 */
fun millisByBucket(sessions: List<UsageSession>, boundaries: List<Long>): List<Long> {
    if (boundaries.size < 2) return emptyList()
    return (0 until boundaries.size - 1).map { i ->
        val start = boundaries[i]
        val end = boundaries[i + 1]
        sessions.sumOf { overlapMillis(it, start, end) }
    }
}

/**
 * Bar heights as 0f..1f fractions of the largest value. An all-zero window returns all zeros
 * rather than dividing by nothing — a flat empty chart is the honest picture of a quiet week.
 */
fun barFractions(values: List<Long>): List<Float> {
    val max = values.maxOrNull() ?: 0L
    if (max <= 0L) return values.map { 0f }
    return values.map { (it.toDouble() / max.toDouble()).toFloat() }
}
