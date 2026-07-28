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

    @Test fun `escalating to Home resets the streak and stands down`() {
        var state = BounceState()
        var now = 0L
        repeat(MAX_CONSECUTIVE_BACKS + 1) {
            now += BOUNCE_COOLDOWN_MS
            state = nextBounce(state, now).state
        }
        assertEquals(0, state.consecutiveBacks)
        val escalatedAt = now

        // Home is the last thing we try. Anything still detected right after it is far more likely
        // to be us misreading the screen than a reel that survived being sent home, so we stop.
        now += BOUNCE_COOLDOWN_MS
        assertEquals(BounceAction.None, nextBounce(state, now).action)
        assertEquals(BounceAction.None, nextBounce(state, escalatedAt + BOUNCE_BACKOFF_MS - 1).action)

        // Once the backoff expires the blocker is armed again, from a clean streak.
        val resumed = nextBounce(state, escalatedAt + BOUNCE_BACKOFF_MS)
        assertEquals(BounceAction.Back, resumed.action)
        assertEquals(1, resumed.state.consecutiveBacks)
    }

    @Test fun `the backoff holds even while sightings keep arriving`() {
        // A stuck detection produces an unbroken stream of sightings, so the backoff has to be
        // measured from the escalation and not from the last thing we looked at.
        var state = nextBounce(BounceState(), nowMs = 0).state
        var now = 0L
        repeat(MAX_CONSECUTIVE_BACKS) {
            now += BOUNCE_COOLDOWN_MS
            state = nextBounce(state, now).state
        }
        val escalatedAt = now

        var now2 = escalatedAt + 100
        while (now2 < escalatedAt + BOUNCE_BACKOFF_MS) {
            val decision = nextBounce(state, now2)
            assertEquals(BounceAction.None, decision.action)
            state = decision.state
            now2 += 100
        }
        assertEquals(BounceAction.Back, nextBounce(state, escalatedAt + BOUNCE_BACKOFF_MS).action)
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

    @Test fun `an unbounded run of reel sightings cannot hold the user hostage`() {
        // The failure this guards against is a detection we are simply wrong about, which then
        // never stops: every event throws the user out of whatever they were doing. Over 36
        // seconds of solid misfiring the user must be left alone for the clear majority of it.
        var state = BounceState()
        var now = 0L
        var homes = 0
        var actions = 0
        repeat(40) {
            now += BOUNCE_COOLDOWN_MS
            val decision = nextBounce(state, now)
            if (decision.action == BounceAction.Home) homes++
            if (decision.action != BounceAction.None) actions++
            state = decision.state
        }
        assertEquals(1, homes)
        assertEquals(7, actions)
    }
}
