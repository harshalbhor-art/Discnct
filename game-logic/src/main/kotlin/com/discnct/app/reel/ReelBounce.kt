package com.discnct.app.reel

/**
 * Minimum gap between two bounces. A feed doesn't unmount the instant Back is delivered, so without
 * this the next scroll event sees the same reel surface and fires again — and again — walking the
 * user clean out of the host app in a fraction of a second.
 */
const val BOUNCE_COOLDOWN_MS = 900L

/**
 * How long after a bounce a second one still counts as *the same* failed attempt. Past this the
 * user has plainly been somewhere else in the app and come back, which is a fresh attempt.
 */
const val BOUNCE_STREAK_WINDOW_MS = 4_000L

/** Backs in a row before we conclude Back isn't getting anywhere and go Home instead. */
const val MAX_CONSECUTIVE_BACKS = 3

/**
 * How long the blocker stands down after escalating to Home.
 *
 * Reaching the escalation means three Backs and a Home all failed to clear the surface, and the
 * only two explanations are both bad: either detection is matching something that isn't a reel, or
 * this app doesn't respond to the actions we have. Continuing from there hammers the user out of
 * whatever they're doing every second or so with no way to stop it, which is the one behaviour
 * this app must never have. So we stop, deliberately, and let them use their phone.
 */
const val BOUNCE_BACKOFF_MS = 30_000L

/** What the service should do about a reel surface it just found on screen. */
enum class BounceAction {
    /** Too soon after the last bounce — the feed hasn't had time to come down yet. Do nothing. */
    None,

    /** Pop the feed off the stack; the user lands on whatever screen was underneath it. */
    Back,

    /** Back is going nowhere, so stop hammering it and leave the app entirely. */
    Home,
}

/** Everything the bounce limiter needs to remember between events. */
data class BounceState(
    val lastBounceAtMs: Long? = null,
    val consecutiveBacks: Int = 0,
    /** Nothing fires before this instant. Set when a run of failed bounces trips the backoff. */
    val backoffUntilMs: Long = 0L,
)

data class BounceDecision(val action: BounceAction, val state: BounceState)

/**
 * The rate limiter around Level 1's global Back.
 *
 * Three problems it exists to solve. The first is repetition: reel detection runs off scroll and
 * content events, which arrive in bursts, so the naive version fires Back several times for one
 * swipe. The second is the reel with nothing behind it — one opened from a shared link or a
 * notification, where Back has no previous screen to return to. There, Back either does nothing
 * (and we'd retry forever) or silently closes the app with no explanation. After
 * [maxConsecutiveBacks] failures we go Home deliberately instead, which at least looks intentional.
 *
 * The third is the one that matters most: never trapping the user. If even Home doesn't clear
 * whatever we think we're seeing, the honest conclusion is that we're wrong, not that we should
 * try harder — so the escalation also arms [backoffMs] of silence. Level 1 failing open for half a
 * minute is a bad day; Level 1 ejecting someone from their phone on a loop is an unusable app.
 */
fun nextBounce(
    state: BounceState,
    nowMs: Long,
    cooldownMs: Long = BOUNCE_COOLDOWN_MS,
    streakWindowMs: Long = BOUNCE_STREAK_WINDOW_MS,
    maxConsecutiveBacks: Int = MAX_CONSECUTIVE_BACKS,
    backoffMs: Long = BOUNCE_BACKOFF_MS,
): BounceDecision {
    if (nowMs < state.backoffUntilMs) return BounceDecision(BounceAction.None, state)

    val sinceLast = state.lastBounceAtMs?.let { nowMs - it }
    if (sinceLast != null && sinceLast < cooldownMs) {
        return BounceDecision(BounceAction.None, state)
    }

    val streak = if (sinceLast != null && sinceLast <= streakWindowMs) state.consecutiveBacks + 1 else 1
    return if (streak > maxConsecutiveBacks) {
        // Reset the count as we escalate, so that once the backoff expires the next reel the user
        // opens gets the full run of Backs again rather than being sent straight home.
        BounceDecision(
            BounceAction.Home,
            BounceState(
                lastBounceAtMs = nowMs,
                consecutiveBacks = 0,
                backoffUntilMs = nowMs + backoffMs,
            ),
        )
    } else {
        BounceDecision(BounceAction.Back, BounceState(lastBounceAtMs = nowMs, consecutiveBacks = streak))
    }
}
