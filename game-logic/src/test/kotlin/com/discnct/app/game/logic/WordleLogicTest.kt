package com.discnct.app.game.logic

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WordleLogicTest {
    @Test fun `scoreGuess marks all correct for an exact match`() {
        val result = wordleScoreGuess("BRAVE", "BRAVE")
        assertEquals(List(5) { WordleLetterState.CORRECT }, result)
    }

    @Test fun `scoreGuess marks absent letters not in the target`() {
        val result = wordleScoreGuess("HOUSE", "BRAVE")
        assertEquals(
            listOf(
                WordleLetterState.ABSENT, WordleLetterState.ABSENT, WordleLetterState.ABSENT,
                WordleLetterState.ABSENT, WordleLetterState.CORRECT,
            ),
            result,
        )
    }

    @Test fun `scoreGuess does not double count a duplicate guessed letter beyond its target occurrences`() {
        val target = "STORM" // exactly one 'S', at index 0
        val guess = "SASSY" // three S's at indices 0, 2, 3
        val scored = wordleScoreGuess(guess, target)
        assertEquals(WordleLetterState.CORRECT, scored[0])
        assertEquals(WordleLetterState.ABSENT, scored[2])
        assertEquals(WordleLetterState.ABSENT, scored[3])
    }

    @Test fun `rewardForGuesses pays out most for guessing fast and least for failing`() {
        assertEquals(7, wordleRewardForGuesses(1))
        assertEquals(7, wordleRewardForGuesses(2))
        assertEquals(6, wordleRewardForGuesses(3))
        assertEquals(5, wordleRewardForGuesses(4))
        assertEquals(4, wordleRewardForGuesses(5))
        assertEquals(3, wordleRewardForGuesses(6))
        assertEquals(1, wordleRewardForGuesses(7))
    }
}
