package com.discnct.app.game.logic

/** Degrees per second the spinner sheds every second it's coasting. */
const val SPINNER_FRICTION_DEG_PER_SEC2 = 220.0

/** A fling can't spin faster than this, so one violent swipe can't earn the whole reward. */
const val SPINNER_MAX_SPEED_DEG_PER_SEC = 2400.0

data class SpinnerState(
    val angleDeg: Double = 0.0,
    val velocityDegPerSec: Double = 0.0,
    /** Total absolute rotation since the game started, in degrees. */
    val totalDegrees: Double = 0.0,
)

/**
 * Advance the spinner by [dtSeconds]. Friction pulls the speed toward zero without ever pushing
 * it past — a spinner that stops must stay stopped rather than reverse direction.
 */
fun stepSpin(state: SpinnerState, dtSeconds: Double): SpinnerState {
    if (dtSeconds <= 0.0) return state

    val speed = kotlin.math.abs(state.velocityDegPerSec)
    val decayed = (speed - SPINNER_FRICTION_DEG_PER_SEC2 * dtSeconds).coerceAtLeast(0.0)
    val newVelocity = if (state.velocityDegPerSec < 0) -decayed else decayed

    // Average the before/after speed so a step that brakes to a halt doesn't over-travel.
    val travelled = (speed + decayed) / 2.0 * dtSeconds
    return SpinnerState(
        angleDeg = normalizeAngle(state.angleDeg + (if (state.velocityDegPerSec < 0) -travelled else travelled)),
        velocityDegPerSec = newVelocity,
        totalDegrees = state.totalDegrees + travelled,
    )
}

/** Add a swipe's worth of spin, clamped so the reward has to be earned over the whole session. */
fun applyFling(state: SpinnerState, deltaDegPerSec: Double): SpinnerState {
    val combined = (state.velocityDegPerSec + deltaDegPerSec)
        .coerceIn(-SPINNER_MAX_SPEED_DEG_PER_SEC, SPINNER_MAX_SPEED_DEG_PER_SEC)
    return state.copy(velocityDegPerSec = combined)
}

fun normalizeAngle(angleDeg: Double): Double {
    val wrapped = angleDeg % 360.0
    return if (wrapped < 0) wrapped + 360.0 else wrapped
}

fun revolutionsFrom(totalDegrees: Double): Int = (totalDegrees / 360.0).toInt()

/**
 * Deliberately the shallowest reward curve of any game — spinning is nearly effortless, so it
 * tops out below what a puzzle pays. Keeping something is still better than nothing, hence the
 * floor of 1.
 */
fun spinnerReward(revolutions: Int): Int = when {
    revolutions >= 60 -> 5
    revolutions >= 40 -> 4
    revolutions >= 25 -> 3
    revolutions >= 12 -> 2
    else -> 1
}
