package com.discnct.app.game.logic

/**
 * Per-day play budgets for the block screen's mini-games. Kept here — string-keyed and free of
 * any Android or GameType import — so the rollover and exhaustion rules can be unit-tested
 * without an emulator, same arrangement as the other game logic in this module.
 *
 * A `null` cap means "not limited by play count": the timed games (Breathing, Fidget Spinner)
 * are bounded by a clock instead, so they stay available all day.
 */

/** How many plays are left today, or null when the game isn't play-count limited. */
fun playsRemaining(cap: Int?, playedToday: Int): Int? =
    if (cap == null) null else (cap - playedToday).coerceAtLeast(0)

fun isPlayable(cap: Int?, playedToday: Int): Boolean = cap == null || playedToday < cap

/**
 * Read stored counts through the day they were written on. Once the epoch-day rolls over the
 * stored numbers belong to yesterday, so everyone starts at zero again — this is a pure read-side
 * reset, which means a device that sat idle overnight doesn't need a background job to clear it.
 */
fun countsForDay(storedDay: Long, today: Long, stored: Map<String, Int>): Map<String, Int> =
    if (storedDay == today) stored else emptyMap()

/** The games the block screen may actually offer: enabled by the user and still under cap. */
fun availableGames(
    enabled: Set<String>,
    caps: Map<String, Int?>,
    playsToday: Map<String, Int>,
): Set<String> = enabled.filterTo(mutableSetOf()) { name ->
    isPlayable(caps[name], playsToday[name] ?: 0)
}
