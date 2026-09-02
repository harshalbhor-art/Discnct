package com.discnct.app.stats

/**
 * A single game's running record. [key] is the [com.discnct.app.game.GameType] name — kept as a
 * string so this stays a plain JVM module with no Android enum to depend on.
 *
 * [minutesEarned] doubles as the points score: a point *is* a minute of unlocked time, which is
 * the only number in the app the user actually spends.
 */
data class GameTally(
    val key: String,
    val plays: Int,
    val minutesEarned: Int,
)

fun totalPlays(tallies: List<GameTally>): Int = tallies.sumOf { it.plays }

fun totalMinutesEarned(tallies: List<GameTally>): Int = tallies.sumOf { it.minutesEarned }

/**
 * The game to call out as the favourite. Ties break on minutes earned, then on key, so the
 * answer is stable across reloads instead of flickering between two equally-played games.
 */
fun mostPlayed(tallies: List<GameTally>): GameTally? =
    tallies.filter { it.plays > 0 }
        .maxWithOrNull(compareBy({ it.plays }, { it.minutesEarned }, { it.key }))

/** [part] as a 0f..1f share of [total]; zero when there's nothing to divide. */
fun share(part: Int, total: Int): Float =
    if (total <= 0 || part <= 0) 0f else (part.toFloat() / total.toFloat()).coerceAtMost(1f)

/**
 * How many of [totalSegments] to fill for a given share, used by the segmented bars. Any
 * non-zero share lights at least one segment — a game you've played once should not read as
 * having been played never.
 */
fun filledSegments(part: Int, total: Int, totalSegments: Int): Int {
    if (totalSegments <= 0) return 0
    val fraction = share(part, total)
    if (fraction <= 0f) return 0
    return Math.round(fraction * totalSegments).coerceIn(1, totalSegments)
}
