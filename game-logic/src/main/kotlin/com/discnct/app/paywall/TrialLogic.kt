package com.discnct.app.paywall

/** Length of the free trial for Level 2 (App Blocker + Games) and Level 3 (Total Disconnect). */
const val TRIAL_DAYS = 12

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val TRIAL_MILLIS = TRIAL_DAYS * DAY_MILLIS

/**
 * How far [nowMillis] is allowed to sit *before* [trialStartMillis] and still be trusted. Ordinary
 * clock jitter (NTP corrections, timezone/DST changes) can nudge the wall clock back by seconds or
 * minutes; setting the date back by hours or days to make an expired trial look freshly started
 * cannot be told apart from that unless somewhere draws a line, so this is it.
 */
private const val CLOCK_ROLLBACK_GRACE_MILLIS = 60L * 60 * 1000

/**
 * Whether the trial that started at [trialStartMillis] is still running at [nowMillis].
 *
 * A null start means the trial has never begun — Level 2+ hasn't been opened yet. That counts as
 * active rather than expired, so the very first open isn't blocked; the caller records the start
 * timestamp at that same moment, which is what actually starts the clock.
 *
 * If [nowMillis] is earlier than [trialStartMillis] by more than [CLOCK_ROLLBACK_GRACE_MILLIS], the
 * trial reads as expired rather than freshly active: a legitimate clock can't land meaningfully
 * before a timestamp it wrote itself moments ago, so a bigger gap means the device clock was set
 * back — most plausibly to make an already-used-up trial look new again.
 */
fun isTrialActive(trialStartMillis: Long?, nowMillis: Long): Boolean {
    if (trialStartMillis == null) return true
    val elapsed = nowMillis - trialStartMillis
    if (elapsed < -CLOCK_ROLLBACK_GRACE_MILLIS) return false
    return elapsed < TRIAL_MILLIS
}

/**
 * Whole days left in the trial. Never negative, never more than [TRIAL_DAYS], and a null start
 * reads as the full length.
 *
 * Counts by whole days *elapsed*, not time *remaining* — so it stays at [TRIAL_DAYS] for the
 * entire first day and only ticks down at each 24-hour mark, instead of dropping the instant a
 * single second passes (which flooring the remaining time would do). A clock set back past
 * [CLOCK_ROLLBACK_GRACE_MILLIS] before the start reads as 0 days left, matching [isTrialActive],
 * rather than counting up past [TRIAL_DAYS].
 */
fun trialDaysRemaining(trialStartMillis: Long?, nowMillis: Long): Int {
    if (trialStartMillis == null) return TRIAL_DAYS
    val elapsed = nowMillis - trialStartMillis
    if (elapsed < -CLOCK_ROLLBACK_GRACE_MILLIS) return 0
    val elapsedDays = (elapsed / DAY_MILLIS).toInt()
    return (TRIAL_DAYS - elapsedDays).coerceIn(0, TRIAL_DAYS)
}
