package com.discnct.app.pause

/**
 * Whether a "Pause Everything" window is still active.
 *
 * Checked against *both* a wall-clock deadline and an elapsed-realtime duration rather than wall
 * time alone: [nowWallMillis] is whatever the device clock currently says, which the user can set
 * backward to make a pause that should have ended look like it's still running. [nowElapsedMillis]
 * tracks real time since boot and can't be wound back short of restarting the phone, so requiring
 * both checks to agree closes the one-tap "set the date back" exploit without changing behaviour
 * for anyone who isn't doing that. A reboot during a pause resets the elapsed side and ends the
 * pause early rather than late — erring toward protection being back on, not off.
 */
fun isPauseActive(
    untilWallMillis: Long,
    atElapsedMillis: Long,
    durationMillis: Long,
    nowWallMillis: Long,
    nowElapsedMillis: Long,
): Boolean {
    if (untilWallMillis == 0L) return false
    val withinWallDeadline = nowWallMillis < untilWallMillis
    val withinElapsedDuration = nowElapsedMillis - atElapsedMillis < durationMillis
    return withinWallDeadline && withinElapsedDuration
}
