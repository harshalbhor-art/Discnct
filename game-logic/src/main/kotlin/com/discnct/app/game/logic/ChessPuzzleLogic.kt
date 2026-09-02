package com.discnct.app.game.logic

/**
 * A position White wins from, and how long it takes.
 *
 * There is no stored "correct answer", and that is the point of the rewrite. A puzzle used to be a
 * board plus one from-to pair, which made every *other* mating move wrong — one of the three
 * shipped positions had two mates and the player was told the second was a mistake. What is stored
 * now is the position and its length; whether a move is right is a question for [ChessEngine], and
 * any move that still forces mate in the moves remaining is accepted.
 *
 * [line] is kept only so giving up can show what the answer was. It is the engine's principal line,
 * not a definition, and a test re-derives it rather than trusting it.
 */
data class ChessPuzzle(
    val board: ChessBoard,
    /** White moves needed. 1 is a mate in one; 3 is the deepest anything here goes. */
    val movesToMate: Int,
    val description: String,
    /** The principal line, White first, alternating, ending on the mate. For the give-up hint. */
    val line: List<ChessMove>,
)

/**
 * Hand-picked positions, every one certified by [ChessEngineTest] and [ChessPuzzleLogicTest].
 *
 * The deeper ones were chosen with a further constraint: at every turn Black has exactly one legal
 * move. That is what makes a scripted-feeling puzzle honest — the reply the player sees isn't a
 * choice the engine made on Black's behalf, it's the only move on the board.
 */
val CHESS_PUZZLES: List<ChessPuzzle> = listOf(
    ChessPuzzle(
        board = mapOf("a1" to 'R', "g1" to 'K', "g8" to 'k', "f7" to 'p', "g7" to 'p', "h7" to 'p'),
        movesToMate = 1,
        description = "White to move — mate in one. The back rank is the weakness.",
        line = listOf(ChessMove("a1", "a8")),
    ),
    ChessPuzzle(
        board = mapOf("d6" to 'N', "a1" to 'K', "h8" to 'k', "g8" to 'r', "g7" to 'p', "h7" to 'p'),
        movesToMate = 1,
        description = "White to move — mate in one. His own pieces are in the way.",
        line = listOf(ChessMove("d6", "f7")),
    ),
    ChessPuzzle(
        board = mapOf("h7" to 'Q', "b6" to 'K', "a8" to 'k'),
        movesToMate = 1,
        description = "White to move — mate in one. The king does the holding.",
        line = listOf(ChessMove("h7", "b7")),
    ),
    ChessPuzzle(
        board = mapOf("c1" to 'R', "c6" to 'R', "f3" to 'K', "h7" to 'k'),
        movesToMate = 2,
        description = "White to move — mate in two. Two rooks, one rung at a time.",
        line = listOf(ChessMove("c1", "g1"), ChessMove("h7", "h8"), ChessMove("c6", "h6")),
    ),
    ChessPuzzle(
        board = mapOf("g2" to 'Q', "g3" to 'K', "e1" to 'k'),
        movesToMate = 2,
        description = "White to move — mate in two. Take away the squares first.",
        line = listOf(ChessMove("g2", "c2"), ChessMove("e1", "f1"), ChessMove("c2", "f2")),
    ),
    ChessPuzzle(
        board = mapOf("b6" to 'R', "b8" to 'K', "e8" to 'k', "f2" to 'R'),
        movesToMate = 2,
        description = "White to move — mate in two. Cut the rank, then close the file.",
        line = listOf(ChessMove("b6", "b7"), ChessMove("e8", "d8"), ChessMove("f2", "f8")),
    ),
    ChessPuzzle(
        board = mapOf("c5" to 'Q', "d1" to 'K', "e6" to 'N', "h3" to 'k'),
        movesToMate = 3,
        description = "White to move — mate in three. Drive him to the corner.",
        line = listOf(
            ChessMove("c5", "g5"),
            ChessMove("h3", "h2"),
            ChessMove("e6", "f4"),
            ChessMove("h2", "h1"),
            ChessMove("g5", "g2"),
        ),
    ),
    ChessPuzzle(
        board = mapOf("a3" to 'R', "g7" to 'R', "b7" to 'K', "b5" to 'k'),
        movesToMate = 3,
        description = "White to move — mate in three. The ladder, rung by rung.",
        line = listOf(
            ChessMove("g7", "g4"),
            ChessMove("b5", "c5"),
            ChessMove("a3", "d3"),
            ChessMove("c5", "b5"),
            ChessMove("d3", "d5"),
        ),
    ),
    ChessPuzzle(
        board = mapOf("b8" to 'R', "d2" to 'R', "e6" to 'k', "e8" to 'K'),
        movesToMate = 3,
        description = "White to move — mate in three. He has one square, and it runs out.",
        line = listOf(
            ChessMove("b8", "b5"),
            ChessMove("e6", "f6"),
            ChessMove("d2", "g2"),
            ChessMove("f6", "e6"),
            ChessMove("g2", "g6"),
        ),
    ),
)

/** What a player's move did to the position. */
sealed interface ChessAttempt {
    /** Mate. The puzzle is over and won. */
    data object Solved : ChessAttempt

    /**
     * Right move, more to play. [reply] is Black's answer — always forced in the puzzles here —
     * and [board] is the position after both moves, with [movesToMate] White moves still to find.
     */
    data class Continues(
        val board: ChessBoard,
        val reply: ChessMove,
        val movesToMate: Int,
    ) : ChessAttempt

    /** Legal, but it throws the win away. Nothing changes; the player tries again. */
    data object Wrong : ChessAttempt

    /** Not a move at all — piece can't go there, or it would leave White's own king in check. */
    data object Illegal : ChessAttempt
}

/**
 * Play [move] in a position with [movesToMate] White moves left, and say what happened.
 *
 * A move is *right* when it still forces mate in the moves remaining — not when it matches a
 * stored answer. Two different mating moves are both mates, and a puzzle that only accepted one of
 * them was lying to the player.
 */
fun chessPlay(board: ChessBoard, movesToMate: Int, move: ChessMove): ChessAttempt {
    if (move !in chessLegalMoves(board, white = true)) return ChessAttempt.Illegal
    val after = chessApply(board, move)
    if (chessIsCheckmate(after, white = false)) return ChessAttempt.Solved
    if (movesToMate <= 1) return ChessAttempt.Wrong

    val replies = chessLegalMoves(after, white = false)
    if (replies.isEmpty()) return ChessAttempt.Wrong // Stalemate throws the win away too.

    // Note the ply. After White moves it is *Black* to play, and chessForcedMate always reads the
    // position it's given as White to move — so the question has to be asked one move further on,
    // once for each reply. Asking it of `after` directly would be answering a different position.
    val outcomes = replies.associateWith { chessForcedMate(chessApply(after, it), movesToMate - 1) }
    if (outcomes.values.any { it == null }) return ChessAttempt.Wrong

    // Black defends as stubbornly as it can, so the puzzle can't be beaten by a convenient reply.
    val reply = outcomes.maxByOrNull { it.value!!.size }?.key ?: return ChessAttempt.Wrong
    return ChessAttempt.Continues(
        board = chessApply(after, reply),
        reply = reply,
        movesToMate = movesToMate - 1,
    )
}

fun chessPieceGlyph(piece: Char): String = when (piece) {
    'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
}

/**
 * Minutes earned, by how deep the puzzle was and how cleanly it was solved.
 *
 * A mate in three is worth roughly twice a mate in one because it takes roughly that much longer to
 * see, and the whole trade this app offers is attention for time.
 */
fun chessRewardForAttempts(wrongAttempts: Int, movesToMate: Int = 1): Int {
    val perfect = 4 + movesToMate * 3
    return when (wrongAttempts) {
        0 -> perfect
        1 -> perfect / 2
        else -> maxOf(1, perfect / 5)
    }
}

/** How the give-up hint reads: `g2–c2, Kf1, c2–f2`. */
fun chessLineNotation(line: List<ChessMove>): String =
    line.joinToString(", ") { "${it.from}–${it.to}" }
