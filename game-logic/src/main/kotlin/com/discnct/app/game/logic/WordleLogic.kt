package com.discnct.app.game.logic

val WORDLE_WORD_LIST = listOf(
    "BRAVE", "CLOUD", "PLANT", "SNAKE", "TRUST", "FROST", "GLASS", "HOUSE",
    "LEMON", "MUSIC", "OCEAN", "PRIDE", "QUIET", "RIVER", "STORM", "TIGER",
    "UNITY", "VIVID", "WATER", "YOUTH",
)
const val WORDLE_MAX_GUESSES = 6
const val WORDLE_WORD_LENGTH = 5

enum class WordleLetterState { CORRECT, PRESENT, ABSENT, EMPTY }

fun wordleScoreGuess(guess: String, target: String): List<WordleLetterState> {
    val result = MutableList(WORDLE_WORD_LENGTH) { WordleLetterState.ABSENT }
    val remaining = target.toMutableList()
    for (i in 0 until WORDLE_WORD_LENGTH) {
        if (guess[i] == target[i]) {
            result[i] = WordleLetterState.CORRECT
            remaining[i] = '_'
        }
    }
    for (i in 0 until WORDLE_WORD_LENGTH) {
        if (result[i] == WordleLetterState.CORRECT) continue
        val idx = remaining.indexOf(guess[i])
        if (idx >= 0) {
            result[i] = WordleLetterState.PRESENT
            remaining[idx] = '_'
        }
    }
    return result
}

fun wordleRewardForGuesses(count: Int): Int = when (count) {
    1, 2 -> 7
    3 -> 6
    4 -> 5
    5 -> 4
    6 -> 3
    else -> 1
}
