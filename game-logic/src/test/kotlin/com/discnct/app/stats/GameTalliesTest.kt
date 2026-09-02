package com.discnct.app.stats

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class GameTalliesTest {

    private val tallies = listOf(
        GameTally("WORDLE", plays = 4, minutesEarned = 20),
        GameTally("CHESS_PUZZLE", plays = 1, minutesEarned = 5),
        GameTally("BREATHING", plays = 0, minutesEarned = 0),
    )

    @Test fun `totals add across games`() {
        assertEquals(5, totalPlays(tallies))
        assertEquals(25, totalMinutesEarned(tallies))
    }

    @Test fun `totals of nothing are zero`() {
        assertEquals(0, totalPlays(emptyList()))
        assertEquals(0, totalMinutesEarned(emptyList()))
    }

    @Test fun `the most-played game wins on play count`() {
        assertEquals("WORDLE", mostPlayed(tallies)?.key)
    }

    @Test fun `a tie on plays breaks on minutes earned`() {
        val tied = listOf(
            GameTally("A", plays = 3, minutesEarned = 9),
            GameTally("B", plays = 3, minutesEarned = 12),
        )
        assertEquals("B", mostPlayed(tied)?.key)
    }

    @Test fun `a tie on plays and minutes breaks on key so the answer is stable`() {
        val tied = listOf(
            GameTally("B", plays = 3, minutesEarned = 9),
            GameTally("A", plays = 3, minutesEarned = 9),
        )
        assertEquals("B", mostPlayed(tied)?.key)
        assertEquals("B", mostPlayed(tied.reversed())?.key)
    }

    @Test fun `games never played are not a favourite`() {
        assertNull(mostPlayed(listOf(GameTally("A", 0, 0), GameTally("B", 0, 0))))
        assertNull(mostPlayed(emptyList()))
    }

    @Test fun `share is a fraction of the total`() {
        assertEquals(0.25f, share(1, 4))
        assertEquals(1f, share(4, 4))
    }

    @Test fun `share of nothing is zero rather than infinite`() {
        assertEquals(0f, share(3, 0))
        assertEquals(0f, share(0, 10))
        assertEquals(0f, share(-1, 10))
    }

    @Test fun `segments round to the nearest whole block`() {
        assertEquals(5, filledSegments(part = 5, total = 10, totalSegments = 10))
        assertEquals(3, filledSegments(part = 1, total = 4, totalSegments = 10))
    }

    @Test fun `one play always lights at least one segment`() {
        assertEquals(1, filledSegments(part = 1, total = 1000, totalSegments = 10))
    }

    @Test fun `nothing played lights nothing`() {
        assertEquals(0, filledSegments(part = 0, total = 10, totalSegments = 10))
        assertEquals(0, filledSegments(part = 5, total = 10, totalSegments = 0))
    }
}
