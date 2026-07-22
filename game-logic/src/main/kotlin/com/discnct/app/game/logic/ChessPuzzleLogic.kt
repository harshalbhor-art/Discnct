package com.discnct.app.game.logic

data class ChessPuzzle(
    val board: Map<String, Char>,
    val solutionFrom: String,
    val solutionTo: String,
    val description: String,
)

// Hand-verified mate-in-1 positions. Uppercase = white, lowercase = black.
val CHESS_PUZZLES = listOf(
    ChessPuzzle(
        board = mapOf("a1" to 'R', "g1" to 'K', "g8" to 'k', "f7" to 'p', "g7" to 'p', "h7" to 'p'),
        solutionFrom = "a1",
        solutionTo = "a8",
        description = "White to move — back-rank mate.",
    ),
    ChessPuzzle(
        board = mapOf("d6" to 'N', "a1" to 'K', "h8" to 'k', "g8" to 'r', "g7" to 'p', "h7" to 'p'),
        solutionFrom = "d6",
        solutionTo = "f7",
        description = "White to move — smothered mate.",
    ),
    ChessPuzzle(
        board = mapOf("h7" to 'Q', "b6" to 'K', "a8" to 'k'),
        solutionFrom = "h7",
        solutionTo = "a7",
        description = "White to move — corner the king.",
    ),
)

fun chessPieceGlyph(piece: Char): String = when (piece) {
    'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
}

fun chessIsSolution(puzzle: ChessPuzzle, from: String, to: String): Boolean =
    from == puzzle.solutionFrom && to == puzzle.solutionTo

fun chessRewardForAttempts(wrongAttempts: Int): Int = when (wrongAttempts) {
    0 -> 7
    1 -> 4
    else -> 1
}
