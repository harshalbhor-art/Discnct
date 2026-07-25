package com.discnct.app.reel

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ReelBounceTest {

    @Test fun `the first sighting of a reel bounces immediately`() {
        val decision = nextBounce(BounceState(), nowMs = 10_000)
        assertEquals(BounceAction.Back, decision.action)
        assertEquals(1, decision.state.consecutiveBacks)
        assertEquals(10_000, decision.state.lastBounceAtMs)
    }

    @Test fun `a second sighting inside the cooldown does nothing`() {
        val after = nextBounce(BounceState(), nowMs = 10_000).state
        val decision = nextBounce(after, nowMs = 10_000 + BOUNCE_COOLDOWN_MS - 1)
        assertEquals(BounceAction.None, decision.action)
    }

    @Test fun `a suppressed sighting leaves the state untouched`() {
        // Otherwise the burst of scroll events during one swipe would run the streak up to the
        // Home escalation without a single Back having actually been delivered.
        val after = nextBounce(BounceState(), nowMs = 10_000).state
        val decision = nextBounce(after, nowMs = 10_500)
        assertEquals(after, decision.state)
    }

    @Test fun `a sighting once the cooldown has passed bounces again`() {
        val after = nextBounce(BounceState(), nowMs = 10_000).state
        val decision = nextBounce(after, nowMs = 10_000 + BOUNCE_COOLDOWN_MS)
        assertEquals(BounceAction.Back, decision.action)
        assertEquals(2, decision.state.consecutiveBacks)
    }

    @Test fun `backs that keep failing escalate to Home`() {
        var state = BounceState()
        var now = 0L
        repeat(MAX_CONSECUTIVE_BACKS) {
            now += BOUNCE_COOLDOWN_MS
            val decision = nextBounce(state, now)
            assertEquals(BounceAction.Back, decision.action)
            state = decision.state
        }
        now += BOUNCE_COOLDOWN_MS
        assertEquals(BounceAction.Home, nextBounce(state, now).action)
    }

    @Test fun `escalating to Home resets the streak`() {
        var state = BounceState()
        var now = 0L
        repeat(MAX_CONSECUTIVE_BACKS + 1) {
            now += BOUNCE_COOLDOWN_MS
            state = nextBounce(state, now).state
        }
        assertEquals(0, state.consecutiveBacks)

        // And the next reel opened straight afterwards gets a plain Back, not another Home.
        now += BOUNCE_COOLDOWN_MS
        assertEquals(BounceAction.Back, nextBounce(state, now).action)
    }

    @Test fun `a bounce long after the last one starts a fresh streak`() {
        var state = BounceState()
        var now = 0L
        repeat(MAX_CONSECUTIVE_BACKS) {
            now += BOUNCE_COOLDOWN_MS
            state = nextBounce(state, now).state
        }

        // The user went off and did something else, then wandered back into reels.
        now += BOUNCE_STREAK_WINDOW_MS + 1
        val decision = nextBounce(state, now)
        assertEquals(BounceAction.Back, decision.action)
        assertEquals(1, decision.state.consecutiveBacks)
    }

    @Test fun `a bounce at the edge of the streak window still counts as the same attempt`() {
        val after = nextBounce(BounceState(), nowMs = 10_000).state
        val decision = nextBounce(after, nowMs = 10_000 + BOUNCE_STREAK_WINDOW_MS)
        assertEquals(2, decision.state.consecutiveBacks)
    }

    @Test fun `an unbounded run of reel sightings never gets stuck sending Home`() {
        // The failure this guards against is a reel surface that survives Home as well as Back:
        // if the streak never reset we'd send Home on every event from then on, forever.
        var state = BounceState()
        var now = 0L
        var homes = 0
        repeat(40) {
            now += BOUNCE_COOLDOWN_MS
            val decision = nextBounce(state, now)
            if (decision.action == BounceAction.Home) homes++
            state = decision.state
        }
        assertEquals(10, homes)
    }
}
