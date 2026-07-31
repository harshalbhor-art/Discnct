package com.discnct.app.game.logic

import kotlin.random.Random

/**
 * The endless runner: gravity, obstacles, and the moment you clip one.
 *
 * Everything is in abstract world units rather than pixels or dp, and the composable scales them to
 * whatever the screen turns out to be. That's what makes the physics testable — a jump that clears
 * a cactus has to clear it on a small phone and a tablet alike, and it would not if the numbers
 * were tied to a screen. [WORLD_WIDTH] units across, ground at y = 0, up is positive.
 *
 * The one tuning constraint worth writing down: [JUMP_VELOCITY] has to give an apex comfortably
 * above [OBSTACLE_MAX_HEIGHT], or the game ships with obstacles that cannot be jumped. A test
 * checks exactly that, because it is the kind of thing a later "make it feel snappier" would break
 * without anyone noticing until a player was stuck.
 *
 * The artwork is the composable's business and is drawn from scratch — nothing here is taken from
 * anyone else's runner.
 */

const val WORLD_WIDTH = 100.0

/** How far up the screen the world reaches. Nothing is drawn above this. */
const val WORLD_HEIGHT = 40.0

/** The runner never moves horizontally; the world moves past it. */
const val DINO_X = 12.0
const val DINO_WIDTH = 8.0
const val DINO_HEIGHT = 12.0

/**
 * World units per second squared, pulling down.
 *
 * Heavier than it first looks, and deliberately. Apex depends on `v²/2g` while airtime depends on
 * `2v/g`, so raising both together buys the same jump height in much less time — and airtime is
 * what decides how close two obstacles are allowed to be. A floatier jump covered eighty units of
 * ground, which is most of the visible world, and made a fair gap impossible.
 */
const val GRAVITY = 440.0

/** Upward speed a jump starts with. Apex is this squared over twice [GRAVITY]. */
const val JUMP_VELOCITY = 132.0

const val START_SPEED = 40.0
const val MAX_SPEED = 95.0

/** Units per second added to the scroll speed for every second survived. */
const val SPEED_GAIN = 1.6

const val OBSTACLE_MIN_WIDTH = 4.0
const val OBSTACLE_MAX_WIDTH = 7.0
const val OBSTACLE_MIN_HEIGHT = 9.0
const val OBSTACLE_MAX_HEIGHT = 16.0

/** Horizontal gap between obstacles, in world units. The lower bound is the one that must be
 *  jumpable: a runner in the air travels `speed * airtime`, and two obstacles closer than that
 *  cannot both be cleared. */
const val OBSTACLE_MIN_GAP = 64.0
const val OBSTACLE_MAX_GAP = 110.0

/** A cactus, sitting on the ground. Height is how far up it reaches. */
data class Obstacle(val x: Double, val width: Double, val height: Double)

data class DinoState(
    /** Height of the runner's feet above the ground. Zero means standing. */
    val y: Double = 0.0,
    val velocityY: Double = 0.0,
    val obstacles: List<Obstacle> = emptyList(),
    /** Total world units travelled — the score. */
    val distance: Double = 0.0,
    val speed: Double = START_SPEED,
    val crashed: Boolean = false,
    /** Seconds until the next obstacle enters from the right. */
    val untilNextObstacle: Double = 1.2,
) {
    val airborne: Boolean get() = y > 0.0
}

/** Start a jump, if there's ground to push off. Jumping in mid-air does nothing. */
fun dinoJump(state: DinoState): DinoState =
    if (state.crashed || state.airborne) state else state.copy(velocityY = JUMP_VELOCITY)

/**
 * Advance the world by [dtSeconds].
 *
 * A crashed state is returned untouched, so a caller that keeps its animation loop running for one
 * more frame can't accumulate distance the player never ran.
 */
fun stepDino(state: DinoState, dtSeconds: Double, random: Random = Random.Default): DinoState {
    if (state.crashed || dtSeconds <= 0.0) return state

    // Exact for constant acceleration: the ½at² term is the difference between a jump that reaches
    // the height the constants promise and one that sails a unit over it, which is enough to make
    // "this obstacle is jumpable" mean something different from what the tuning says.
    var y = state.y + state.velocityY * dtSeconds - 0.5 * GRAVITY * dtSeconds * dtSeconds
    var velocityY = state.velocityY - GRAVITY * dtSeconds
    if (y <= 0.0) {
        y = 0.0
        velocityY = 0.0
    }

    val speed = (state.speed + SPEED_GAIN * dtSeconds).coerceAtMost(MAX_SPEED)
    val travelled = speed * dtSeconds

    // Everything scrolls left, and anything fully past the runner is gone for good.
    val moved = state.obstacles
        .map { it.copy(x = it.x - travelled) }
        .filter { it.x + it.width > -OBSTACLE_MAX_WIDTH }

    var untilNext = state.untilNextObstacle - dtSeconds
    val obstacles = if (untilNext <= 0.0) {
        val gap = OBSTACLE_MIN_GAP + random.nextDouble() * (OBSTACLE_MAX_GAP - OBSTACLE_MIN_GAP)
        untilNext = gap / speed
        moved + Obstacle(
            x = WORLD_WIDTH,
            width = OBSTACLE_MIN_WIDTH + random.nextDouble() * (OBSTACLE_MAX_WIDTH - OBSTACLE_MIN_WIDTH),
            height = OBSTACLE_MIN_HEIGHT + random.nextDouble() * (OBSTACLE_MAX_HEIGHT - OBSTACLE_MIN_HEIGHT),
        )
    } else {
        moved
    }

    val next = state.copy(
        y = y,
        velocityY = velocityY,
        obstacles = obstacles,
        distance = state.distance + travelled,
        speed = speed,
        untilNextObstacle = untilNext,
    )
    return next.copy(crashed = dinoHasCrashed(next))
}

/** Box against box. The runner's feet are at [DinoState.y]; a cactus stands on the ground. */
fun dinoHasCrashed(state: DinoState): Boolean = state.obstacles.any { obstacle ->
    val overlapsHorizontally = DINO_X < obstacle.x + obstacle.width && DINO_X + DINO_WIDTH > obstacle.x
    overlapsHorizontally && state.y < obstacle.height
}

/** How high a jump from standing reaches, in world units. */
fun dinoJumpApex(): Double = JUMP_VELOCITY * JUMP_VELOCITY / (2.0 * GRAVITY)

/** How long a jump from standing keeps the runner off the ground, in seconds. */
fun dinoAirtimeSeconds(): Double = 2.0 * JUMP_VELOCITY / GRAVITY

/** The score as a player reads it: whole metres rather than fractional world units. */
fun dinoScore(distance: Double): Int = (distance / 10.0).toInt()

/**
 * Minutes earned for a run.
 *
 * Steeper at the start than the idle games, because surviving at all takes attention, and flatter
 * at the top so a long lucky run isn't a cheap way to buy back an evening.
 */
fun dinoReward(score: Int): Int = when {
    score >= 120 -> 8
    score >= 80 -> 6
    score >= 50 -> 5
    score >= 30 -> 4
    score >= 15 -> 2
    else -> 1
}
