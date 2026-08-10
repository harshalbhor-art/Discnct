package com.discnct.app.paywall

/** Length of the free trial for Level 2 (App Blocker + Games) and Level 3 (Total Disconnect). */
const val TRIAL_DAYS = 12

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val TRIAL_MILLIS = TRIAL_DAYS * DAY_MILLIS

/**
 * Whether the trial that started at [trialStartMillis] is still running at [nowMillis].
 *
 * A null start means the trial has never begun — Level 2+ hasn't been opened yet. That counts as
 * active rather than expired, so the very first open isn't blocked; the caller records the start
 * timestamp at that same moment, which is what actually starts the clock.
 */
fun isTrialActive(trialStartMillis: Long?, nowMillis: Long): Boolean =
    trialStartMillis == null || nowMillis - trialStartMillis < TRIAL_MILLIS

/**
 * Whole days left in the trial. Never negative, and a null start reads as the full length.
 *
 * Counts by whole days *elapsed*, not time *remaining* — so it stays at [TRIAL_DAYS] for the
 * entire first day and only ticks down at each 24-hour mark, instead of dropping the instant a
 * single second passes (which flooring the remaining time would do).
 */
fun trialDaysRemaining(trialStartMillis: Long?, nowMillis: Long): Int {
    if (trialStartMillis == null) return TRIAL_DAYS
    val elapsedDays = ((nowMillis - trialStartMillis) / DAY_MILLIS).toInt()
    return (TRIAL_DAYS - elapsedDays).coerceAtLeast(0)
}
