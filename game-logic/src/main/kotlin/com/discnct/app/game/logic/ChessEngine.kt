package com.discnct.app.game.logic

/**
 * Just enough chess to prove a mate.
 *
 * The puzzles used to be a board plus a hard-coded pair of squares, and "mate in one" was a claim
 * in a comment rather than something anything checked. That is fine until you want mate in three,
 * where the position, the opponent's replies and the length of the line all have to agree — and it
 * was already quietly wrong: one shipped puzzle had two different mating moves and the player was
 * told the second one was incorrect.
 *
 * So moves are generated properly here, and [ChessPuzzleLogic] asks this whether a position is
 * still winning rather than comparing strings. A test walks every shipped puzzle through it and
 * fails the build if a claimed mate isn't one.
 *
 * Deliberately incomplete: no castling, no en passant, no promotion. None of the puzzles need them,
 * and each is a rule that could only ever produce a wrong answer here, never a needed one.
 */

/** A move as the board describes it: algebraic square to algebraic square, e.g. `g2` to `c2`. */
data class ChessMove(val from: String, val to: String)

/** Piece placement by square. Uppercase is White, lowercase is Black — the whole board state. */
typealias ChessBoard = Map<String, Char>

private const val FILES = "abcdefgh"

private val ROOK_STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val BISHOP_STEPS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
private val QUEEN_STEPS = ROOK_STEPS + BISHOP_STEPS
private val KNIGHT_STEPS =
    listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)

private fun square(file: Int, rank: Int) = "${FILES[file]}$rank"

private fun fileOf(square: String) = FILES.indexOf(square[0])

private fun rankOf(square: String) = square[1] - '0'

private fun onBoard(file: Int, rank: Int) = file in 0..7 && rank in 1..8

/** True for a square name this engine can read at all. */
fun isChessSquare(name: String): Boolean =
    name.length == 2 && name[0] in FILES && name[1] in '1'..'8'

/**
 * Every move the side to play could make if it didn't have to care about its own king.
 *
 * Kept separate from [chessLegalMoves] because check detection is defined in terms of it: a king is
 * in check when the *other* side could capture it next move, and asking that question of legal
 * moves instead would recurse forever.
 */
private fun pseudoMoves(board: ChessBoard, white: Boolean): List<ChessMove> {
    val moves = ArrayList<ChessMove>()
    for ((from, piece) in board) {
        if (piece.isUpperCase() != white) continue
        val file = fileOf(from)
        val rank = rankOf(from)
        when (piece.uppercaseChar()) {
            'R', 'B', 'Q' -> {
                val steps = when (piece.uppercaseChar()) {
                    'R' -> ROOK_STEPS
                    'B' -> BISHOP_STEPS
                    else -> QUEEN_STEPS
                }
                for ((df, dr) in steps) {
                    var f = file + df
                    var r = rank + dr
                    while (onBoard(f, r)) {
                        val to = square(f, r)
                        val occupant = board[to]
                        if (occupant != null) {
                            if (occupant.isUpperCase() != white) moves.add(ChessMove(from, to))
                            break
                        }
                        moves.add(ChessMove(from, to))
                        f += df
                        r += dr
                    }
                }
            }
            'N' -> for ((df, dr) in KNIGHT_STEPS) {
                val f = file + df
                val r = rank + dr
                if (!onBoard(f, r)) continue
                val to = square(f, r)
                if (board[to]?.isUpperCase() != white) moves.add(ChessMove(from, to))
            }
            'K' -> for ((df, dr) in QUEEN_STEPS) {
                val f = file + df
                val r = rank + dr
                if (!onBoard(f, r)) continue
                val to = square(f, r)
                if (board[to]?.isUpperCase() != white) moves.add(ChessMove(from, to))
            }
            'P' -> {
                // One square only. No double step, so no en passant to answer for either.
                val step = if (white) 1 else -1
                val r = rank + step
                if (onBoard(file, r) && square(file, r) !in board) {
                    moves.add(ChessMove(from, square(file, r)))
                }
                for (df in intArrayOf(-1, 1)) {
                    val f = file + df
                    if (!onBoard(f, r)) continue
                    val to = square(f, r)
                    val occupant = board[to] ?: continue
                    if (occupant.isUpperCase() != white) moves.add(ChessMove(from, to))
                }
            }
        }
    }
    return moves
}

/** The position after [move]. Captures are implicit: whatever was on the target square is gone. */
fun chessApply(board: ChessBoard, move: ChessMove): ChessBoard {
    val piece = board[move.from] ?: return board
    val next = HashMap(board)
    next.remove(move.from)
    next[move.to] = piece
    return next
}

/** True while the side's king is attacked. A board with no such king is never in check. */
fun chessInCheck(board: ChessBoard, white: Boolean): Boolean {
    val king = if (white) 'K' else 'k'
    val kingSquare = board.entries.firstOrNull { it.value == king }?.key ?: return false
    return pseudoMoves(board, !white).any { it.to == kingSquare }
}

/** Every move that is actually available: pseudo-legal, and doesn't leave your own king attacked. */
fun chessLegalMoves(board: ChessBoard, white: Boolean): List<ChessMove> =
    pseudoMoves(board, white).filter { !chessInCheck(chessApply(board, it), white) }

/** In check with nothing to do about it. */
fun chessIsCheckmate(board: ChessBoard, white: Boolean): Boolean =
    chessInCheck(board, white) && chessLegalMoves(board, white).isEmpty()

/**
 * The line by which White forces mate in exactly [depth] of its own moves, or null if it can't.
 *
 * "Forces" is the strong reading: every Black reply is answered, so the returned line is only the
 * principal one. What it costs to be sure of that is a full search — but the puzzles are four or
 * five pieces and [depth] never exceeds 3, so the tree is small enough to walk on a phone between
 * one tap and the next.
 *
 * The returned line alternates White, Black, White… and always ends on White's mating move.
 */
fun chessForcedMate(board: ChessBoard, depth: Int): List<ChessMove>? {
    if (depth <= 0) return null
    for (move in chessLegalMoves(board, true)) {
        val after = chessApply(board, move)
        if (chessIsCheckmate(after, false)) {
            if (depth == 1) return listOf(move)
            continue
        }
        if (depth == 1) continue
        val replies = chessLegalMoves(after, false)
        if (replies.isEmpty()) continue // Stalemate: not a win, and not what we were asked for.
        var principal: List<ChessMove>? = null
        var everyReplyAnswered = true
        for (reply in replies) {
            val continuation = chessForcedMate(chessApply(after, reply), depth - 1)
            if (continuation == null) {
                everyReplyAnswered = false
                break
            }
            if (principal == null) principal = listOf(reply) + continuation
        }
        if (everyReplyAnswered && principal != null) return listOf(move) + principal
    }
    return null
}

/** True if White mates in [depth] and no faster — what "mate in three" is supposed to mean. */
fun chessIsMateIn(board: ChessBoard, depth: Int): Boolean =
    chessForcedMate(board, depth) != null && (1 until depth).none { chessForcedMate(board, it) != null }
