package com.discnct.app.game

/** What a finished mini-game hands back to the block screen: minutes earned, and why. */
data class GameOutcome(val earnedMinutes: Int, val resultLabel: String)

enum class GameType(val displayName: String) {
    CHESS_PUZZLE("Chess Puzzle"),
    TIC_TAC_TOE("Tic-Tac-Toe"),
    MINESWEEPER("Minesweeper"),
    WORDLE("Wordle"),
    GAME_2048("2048"),
    BREATHING("Breathing"),
}

/** The pool Level 1's "play instead of wait" choice draws from — every entry, uniformly. */
object GamePool {
    fun random(): GameType = GameType.entries.random()
}
