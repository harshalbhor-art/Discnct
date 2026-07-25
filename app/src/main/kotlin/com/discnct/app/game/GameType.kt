package com.discnct.app.game

/** What a finished mini-game hands back to the block screen: minutes earned, and why. */
data class GameOutcome(val earnedMinutes: Int, val resultLabel: String)

/**
 * Every game is bounded in exactly one way, and the two fields are mutually exclusive:
 *
 *  - **Puzzles** ([dailyPlayCap]) are limited by how many times a day you may open them. They end
 *    when you solve or lose them, so a clock would be the wrong lever — the friction has to be
 *    scarcity instead.
 *  - **Idle games** ([timeLimitSeconds]) have no win condition to reach, so they're limited by a
 *    clock and stay available all day. Capping Breathing would mean rationing the one thing in
 *    here that's actually good for you.
 */
enum class GameType(
    val displayName: String,
    val dailyPlayCap: Int? = null,
    val timeLimitSeconds: Int? = null,
) {
    CHESS_PUZZLE("Chess Puzzle", dailyPlayCap = 3),
    TIC_TAC_TOE("Tic-Tac-Toe", dailyPlayCap = 5),
    MINESWEEPER("Minesweeper", dailyPlayCap = 3),
    WORDLE("Wordle", dailyPlayCap = 2),
    GAME_2048("2048", dailyPlayCap = 3),
    BREATHING("Breathing", timeLimitSeconds = 64),
    FIDGET_SPINNER("Fidget Spinner", timeLimitSeconds = 45),
    ;

    /** One-line "3 left today" / "45s session" summary for the game picker. */
    fun limitLabel(playsToday: Int): String = when {
        timeLimitSeconds != null -> "${timeLimitSeconds}s session"
        dailyPlayCap != null -> "${(dailyPlayCap - playsToday).coerceAtLeast(0)} of $dailyPlayCap left today"
        else -> ""
    }
}

/** The pool the "play instead of wait" choice draws from. */
object GamePool {
    /**
     * Pick uniformly from the games the block screen may currently offer. Returns null when the
     * pool is empty — every enabled game capped out for the day — so the caller can fall back to
     * hold-to-unlock rather than pretending there's a game to play.
     */
    fun randomFrom(available: Set<GameType>): GameType? = available.randomOrNull()
}
