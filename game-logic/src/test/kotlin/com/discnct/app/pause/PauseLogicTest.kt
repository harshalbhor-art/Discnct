package com.discnct.app.pause

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PauseLogicTest {

    private val minuteMillis = 60_000L

    @Test fun `untilWallMillis of zero means not paused`() {
        assertFalse(isPauseActive(0L, 0L, 0L, nowWallMillis = 0L, nowElapsedMillis = 0L))
    }

    @Test fun `active right after pausing, before either deadline`() {
        val untilWall = 1_000_000L + 15 * minuteMillis
        val atElapsed = 500_000L
        val duration = 15 * minuteMillis
        assertTrue(isPauseActive(untilWall, atElapsed, duration, nowWallMillis = 1_000_000L, nowElapsedMillis = atElapsed))
    }

    @Test fun `expires normally once real time passes the duration`() {
        val untilWall = 1_000_000L + 15 * minuteMillis
        val atElapsed = 500_000L
        val duration = 15 * minuteMillis
        val nowElapsed = atElapsed + duration
        assertFalse(
            isPauseActive(
                untilWall,
                atElapsed,
                duration,
                nowWallMillis = 1_000_000L + 5 * minuteMillis,
                nowElapsedMillis = nowElapsed,
            ),
        )
    }

    @Test fun `rolling the wall clock backward cannot keep a pause alive past its real duration`() {
        val untilWall = 1_000_000L + 15 * minuteMillis
        val atElapsed = 500_000L
        val duration = 15 * minuteMillis
        // Real time has moved past the earned duration, even though the wall clock was wound back
        // to well before untilWall.
        val nowElapsed = atElapsed + duration + minuteMillis
        assertFalse(
            isPauseActive(
                untilWall,
                atElapsed,
                duration,
                nowWallMillis = 0L,
                nowElapsedMillis = nowElapsed,
            ),
        )
    }

    @Test fun `the wall deadline still ends a pause even if elapsed time alone would allow it`() {
        val untilWall = 1_000_000L
        val atElapsed = 500_000L
        val duration = 15 * minuteMillis
        assertFalse(
            isPauseActive(
                untilWall,
                atElapsed,
                duration,
                nowWallMillis = 1_000_001L,
                nowElapsedMillis = atElapsed + 1_000L,
            ),
        )
    }
}
