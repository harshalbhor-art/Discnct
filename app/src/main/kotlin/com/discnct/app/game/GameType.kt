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

/** The pool the "play instead of wait" choice draws from. */
object GamePool {
    fun random(): GameType = GameType.entries.random()

    /**
     * Pick uniformly from the user's chosen games (see BlockerGamesStore). Falls back to the full
     * set if the caller somehow passes an empty selection, so the block screen always has a game.
     */
    fun randomFrom(enabled: Set<GameType>): GameType =
        enabled.ifEmpty { GameType.entries.toSet() }.random()
}
