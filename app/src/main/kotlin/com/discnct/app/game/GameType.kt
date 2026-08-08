package com.discnct.app.game

/** What a finished mini-game hands back to the block screen: minutes earned, and why. */
data class GameOutcome(val earnedMinutes: Int, val resultLabel: String)

/**
 * The mini-games the block screen can offer.
 *
 * Puzzles are unbounded — play as many as you like, as often as you like; the friction is having
 * to finish one at all, not rationing them. The games with no win condition to reach end on a
 * clock instead ([timeLimitSeconds]): the two idle ones, and the runner, which would otherwise go
 * on for as long as somebody kept clearing cacti.
 */
enum class GameType(
    val displayName: String,
    val timeLimitSeconds: Int? = null,
) {
    CHESS_PUZZLE("Chess Puzzle"),
    TIC_TAC_TOE("Tic-Tac-Toe"),
    MINESWEEPER("Minesweeper"),
    WORDLE("Wordle"),
    WORD_SEARCH("Word Search"),
    DINO_RUN("Dino Run", timeLimitSeconds = 60),
    GAME_2048("2048"),
    BREATHING("Breathing", timeLimitSeconds = 64),
    FIDGET_SPINNER("Fidget Spinner", timeLimitSeconds = 45),
    ;

    /** One-line "45s session" / "Solve it to earn time" summary for the game picker. */
    val limitLabel: String
        get() = timeLimitSeconds?.let { "${it}s session" } ?: "Solve it to earn time"
}

/** The pool the "play instead of wait" choice draws from. */
object GamePool {
    /**
     * Pick uniformly from the games the user has switched on. Returns null only when the pool is
     * empty, so the caller can fall back to hold-to-unlock rather than pretending there's a game.
     */
    fun randomFrom(available: Set<GameType>): GameType? = available.randomOrNull()
}
