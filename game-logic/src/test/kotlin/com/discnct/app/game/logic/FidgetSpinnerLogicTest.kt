package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FidgetSpinnerLogicTest {

    @Test fun `friction slows the spinner`() {
        val after = stepSpin(SpinnerState(velocityDegPerSec = 1000.0), dtSeconds = 1.0)
        assertEquals(1000.0 - SPINNER_FRICTION_DEG_PER_SEC2, after.velocityDegPerSec, 0.001)
    }

    @Test fun `a spinner that brakes to a halt never reverses`() {
        val after = stepSpin(SpinnerState(velocityDegPerSec = 100.0), dtSeconds = 5.0)
        assertEquals(0.0, after.velocityDegPerSec, 0.001)
    }

    @Test fun `a stopped spinner stays stopped`() {
        val after = stepSpin(SpinnerState(angleDeg = 42.0), dtSeconds = 1.0)
        assertEquals(0.0, after.velocityDegPerSec, 0.001)
        assertEquals(42.0, after.angleDeg, 0.001)
        assertEquals(0.0, after.totalDegrees, 0.001)
    }

    @Test fun `spinning backwards accumulates travel as a positive total`() {
        val after = stepSpin(SpinnerState(velocityDegPerSec = -720.0), dtSeconds = 0.5)
        assertTrue(after.velocityDegPerSec < 0)
        assertTrue(after.totalDegrees > 0)
    }

    @Test fun `a zero-length step changes nothing`() {
        val before = SpinnerState(angleDeg = 10.0, velocityDegPerSec = 500.0, totalDegrees = 90.0)
        assertEquals(before, stepSpin(before, dtSeconds = 0.0))
    }

    @Test fun `flings stack but clamp at the top speed`() {
        var state = SpinnerState()
        repeat(10) { state = applyFling(state, 500.0) }
        assertEquals(SPINNER_MAX_SPEED_DEG_PER_SEC, state.velocityDegPerSec, 0.001)
    }

    @Test fun `flings clamp in the negative direction too`() {
        var state = SpinnerState()
        repeat(10) { state = applyFling(state, -500.0) }
        assertEquals(-SPINNER_MAX_SPEED_DEG_PER_SEC, state.velocityDegPerSec, 0.001)
    }

    @Test fun `angle wraps into zero until 360`() {
        assertEquals(10.0, normalizeAngle(370.0), 0.001)
        assertEquals(350.0, normalizeAngle(-10.0), 0.001)
        assertEquals(0.0, normalizeAngle(720.0), 0.001)
    }

    @Test fun `revolutions count whole turns only`() {
        assertEquals(0, revolutionsFrom(359.0))
        assertEquals(1, revolutionsFrom(360.0))
        assertEquals(3, revolutionsFrom(1260.0))
    }

    @Test fun `reward climbs with revolutions and floors at one`() {
        assertEquals(1, spinnerReward(0))
        assertEquals(1, spinnerReward(11))
        assertEquals(2, spinnerReward(12))
        assertEquals(3, spinnerReward(25))
        assertEquals(4, spinnerReward(40))
        assertEquals(5, spinnerReward(60))
        assertEquals(5, spinnerReward(500))
    }
}
