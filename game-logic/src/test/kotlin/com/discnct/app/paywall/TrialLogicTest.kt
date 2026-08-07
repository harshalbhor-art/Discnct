package com.discnct.app.paywall

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TrialLogicTest {

    private val dayMillis = 24L * 60 * 60 * 1000

    @Test fun `a trial that never started is active`() {
        assertTrue(isTrialActive(trialStartMillis = null, nowMillis = 0L))
    }

    @Test fun `a trial that never started has the full length remaining`() {
        assertEquals(TRIAL_DAYS, trialDaysRemaining(trialStartMillis = null, nowMillis = 0L))
    }

    @Test fun `the trial is active right after it starts`() {
        val start = 1_000_000L
        assertTrue(isTrialActive(start, nowMillis = start))
        assertTrue(isTrialActive(start, nowMillis = start + dayMillis))
    }

    @Test fun `the trial is still active one millisecond before it expires`() {
        val start = 0L
        assertTrue(isTrialActive(start, nowMillis = TRIAL_DAYS * dayMillis - 1))
    }

    @Test fun `the trial has expired exactly at the boundary`() {
        val start = 0L
        assertFalse(isTrialActive(start, nowMillis = TRIAL_DAYS * dayMillis))
    }

    @Test fun `the trial stays expired well past the boundary`() {
        val start = 0L
        assertFalse(isTrialActive(start, nowMillis = TRIAL_DAYS * dayMillis * 10))
    }

    @Test fun `days remaining counts down as time passes`() {
        val start = 0L
        assertEquals(TRIAL_DAYS, trialDaysRemaining(start, nowMillis = start))
        assertEquals(TRIAL_DAYS - 1, trialDaysRemaining(start, nowMillis = start + dayMillis))
        assertEquals(1, trialDaysRemaining(start, nowMillis = start + (TRIAL_DAYS - 1) * dayMillis))
    }

    @Test fun `days remaining never goes negative once the trial has expired`() {
        val start = 0L
        assertEquals(0, trialDaysRemaining(start, nowMillis = start + TRIAL_DAYS * dayMillis))
        assertEquals(0, trialDaysRemaining(start, nowMillis = start + TRIAL_DAYS * dayMillis * 5))
    }
}
