package com.discnct.app.game.logic

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val FRAME = 1.0 / 60.0

/**
 * Hold the runner clear of the ground for a test that isn't about crashing.
 *
 * Setting `y` once isn't enough — gravity pulls it straight back down and the runner walks into the
 * first cactus, which quietly ends the run and makes a test about spawning or speed pass for the
 * wrong reason.
 */
private fun DinoState.flying() = copy(y = WORLD_HEIGHT, velocityY = 0.0)

class DinoRunLogicTest {

    @Test fun `a jump clears the tallest obstacle the game can spawn`() {
        // The constraint the whole game rests on. Tuning JUMP_VELOCITY or GRAVITY for feel without
        // checking this ships obstacles that physically cannot be jumped.
        assertTrue(
            dinoJumpApex() > OBSTACLE_MAX_HEIGHT,
            "apex ${dinoJumpApex()} does not clear a ${OBSTACLE_MAX_HEIGHT} obstacle",
        )
    }

    @Test fun `two obstacles at the closest possible gap can both be jumped`() {
        // At top speed a jump covers speed × airtime. If that exceeds the minimum gap, the runner
        // lands on the second cactus with no chance to jump again.
        val reach = MAX_SPEED * dinoAirtimeSeconds()
        assertTrue(reach < OBSTACLE_MIN_GAP, "a jump covers $reach but obstacles can be $OBSTACLE_MIN_GAP apart")
    }

    @Test fun `the runner falls back to the ground and stays there`() {
        var state = dinoJump(DinoState())
        assertTrue(state.airborne || state.velocityY > 0)
        repeat(200) { state = stepDino(state, FRAME, Random(1)) }
        assertEquals(0.0, state.y)
        assertEquals(0.0, state.velocityY)
    }

    @Test fun `a jump reaches roughly the apex the maths predicts`() {
        var state = dinoJump(DinoState())
        var highest = 0.0
        repeat(120) {
            state = stepDino(state, FRAME, Random(1))
            highest = maxOf(highest, state.y)
        }
        assertTrue(abs(highest - dinoJumpApex()) < 1.0, "reached $highest, expected about ${dinoJumpApex()}")
    }

    @Test fun `jumping in mid-air does nothing`() {
        val airborne = stepDino(dinoJump(DinoState()), FRAME, Random(1))
        assertEquals(airborne, dinoJump(airborne), "a double jump would make every obstacle trivial")
    }

    @Test fun `the world scrolls and the runner does not`() {
        val start = DinoState(obstacles = listOf(Obstacle(x = 90.0, width = 5.0, height = 10.0)))
        val next = stepDino(start, FRAME, Random(1))
        assertTrue(next.obstacles.first().x < 90.0)
        assertTrue(next.distance > 0.0)
    }

    @Test fun `obstacles that have gone past are forgotten`() {
        var state = DinoState(obstacles = listOf(Obstacle(x = 5.0, width = 5.0, height = 10.0)))
        repeat(120) { state = stepDino(state.flying(), FRAME, Random(2)) }
        assertTrue(
            state.obstacles.none { it.x + it.width <= -OBSTACLE_MAX_WIDTH },
            "obstacles are piling up off-screen: ${state.obstacles}",
        )
    }

    @Test fun `speed climbs but is capped`() {
        var state = DinoState()
        repeat(60 * 120) { state = stepDino(state.flying(), FRAME, Random(3)) }
        assertTrue(state.speed <= MAX_SPEED)
        assertTrue(state.speed > START_SPEED)
    }

    @Test fun `running into a cactus is a crash`() {
        val state = DinoState(obstacles = listOf(Obstacle(x = DINO_X + 2.0, width = 5.0, height = 12.0)))
        assertTrue(dinoHasCrashed(state))
    }

    @Test fun `jumping over the same cactus is not`() {
        val state = DinoState(
            y = OBSTACLE_MAX_HEIGHT + 1.0,
            obstacles = listOf(Obstacle(x = DINO_X + 2.0, width = 5.0, height = 12.0)),
        )
        assertFalse(dinoHasCrashed(state))
    }

    @Test fun `a cactus the runner has not reached yet is not a crash`() {
        assertFalse(dinoHasCrashed(DinoState(obstacles = listOf(Obstacle(x = 60.0, width = 5.0, height = 12.0)))))
    }

    @Test fun `a crashed run stops accumulating distance`() {
        val crashed = DinoState(distance = 500.0, crashed = true)
        assertEquals(crashed, stepDino(crashed, FRAME, Random(4)))
    }

    @Test fun `a zero or negative timestep changes nothing`() {
        val state = DinoState(distance = 12.0)
        assertEquals(state, stepDino(state, 0.0, Random(5)))
        assertEquals(state, stepDino(state, -1.0, Random(5)))
    }

    @Test fun `obstacles keep arriving`() {
        var state = DinoState()
        var spawned = 0
        var previous = 0
        val random = Random(6)
        repeat(60 * 30) {
            state = stepDino(state.flying(), FRAME, random)
            if (state.obstacles.size > previous) spawned++
            previous = state.obstacles.size
        }
        assertTrue(spawned >= 5, "only $spawned obstacles in thirty seconds")
    }

    @Test fun `the same seed runs the same course`() {
        fun run(): DinoState {
            var state = DinoState()
            val random = Random(77)
            repeat(600) { state = stepDino(state.flying(), FRAME, random) }
            return state
        }
        assertEquals(run(), run())
    }

    @Test fun `the score is distance in whole metres`() {
        assertEquals(0, dinoScore(9.0))
        assertEquals(1, dinoScore(10.0))
        assertEquals(42, dinoScore(429.9))
    }

    @Test fun `a longer run is worth more, and a short one still pays`() {
        assertTrue(dinoReward(200) > dinoReward(60))
        assertTrue(dinoReward(60) > dinoReward(20))
        assertEquals(1, dinoReward(0))
    }
}
