package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PlayLimitLogicTest {

    @Test fun `remaining counts down and never goes negative`() {
        assertEquals(3, playsRemaining(3, 0))
        assertEquals(1, playsRemaining(3, 2))
        assertEquals(0, playsRemaining(3, 3))
        assertEquals(0, playsRemaining(3, 9))
    }

    @Test fun `a null cap means unlimited`() {
        assertNull(playsRemaining(null, 100))
        assertTrue(isPlayable(null, 100))
    }

    @Test fun `playable until the cap is reached`() {
        assertTrue(isPlayable(2, 0))
        assertTrue(isPlayable(2, 1))
        assertFalse(isPlayable(2, 2))
        assertFalse(isPlayable(2, 3))
    }

    @Test fun `a cap of zero is never playable`() {
        assertFalse(isPlayable(0, 0))
    }

    @Test fun `counts survive within the same day`() {
        val stored = mapOf("WORDLE" to 2)
        assertEquals(stored, countsForDay(storedDay = 20_000L, today = 20_000L, stored = stored))
    }

    @Test fun `counts reset once the day rolls over`() {
        val stored = mapOf("WORDLE" to 2)
        assertEquals(emptyMap(), countsForDay(storedDay = 20_000L, today = 20_001L, stored = stored))
    }

    @Test fun `availableGames drops the capped-out ones`() {
        val available = availableGames(
            enabled = setOf("WORDLE", "GAME_2048", "BREATHING"),
            caps = mapOf("WORDLE" to 2, "GAME_2048" to 3, "BREATHING" to null),
            playsToday = mapOf("WORDLE" to 2, "GAME_2048" to 1),
        )
        assertEquals(setOf("GAME_2048", "BREATHING"), available)
    }

    @Test fun `availableGames can come back empty`() {
        val available = availableGames(
            enabled = setOf("WORDLE"),
            caps = mapOf("WORDLE" to 1),
            playsToday = mapOf("WORDLE" to 1),
        )
        assertTrue(available.isEmpty())
    }

    @Test fun `a game with no recorded plays is available`() {
        val available = availableGames(
            enabled = setOf("CHESS_PUZZLE"),
            caps = mapOf("CHESS_PUZZLE" to 3),
            playsToday = emptyMap(),
        )
        assertEquals(setOf("CHESS_PUZZLE"), available)
    }
}
