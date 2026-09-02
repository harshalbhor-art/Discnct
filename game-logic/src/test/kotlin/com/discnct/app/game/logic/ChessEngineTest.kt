package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ChessEngineTest {

    @Test fun `a rook sweeps its rank and file until something blocks it`() {
        val board = mapOf("a1" to 'R', "a4" to 'P', "d1" to 'p')
        val moves = chessLegalMoves(board, white = true).filter { it.from == "a1" }.map { it.to }.toSet()
        assertTrue("a3" in moves)
        assertTrue("d1" in moves, "it can capture the black pawn it runs into")
        assertFalse("a4" in moves, "it can't capture its own pawn")
        assertFalse("a5" in moves, "and it can't jump over it")
        assertFalse("e1" in moves, "or carry on past a capture")
    }

    @Test fun `a knight jumps and does not care what is in between`() {
        val board = mapOf("d4" to 'N', "d5" to 'P', "e5" to 'P')
        val moves = chessLegalMoves(board, white = true).filter { it.from == "d4" }.map { it.to }.toSet()
        assertEquals(setOf("e6", "f5", "f3", "e2", "c2", "b3", "b5", "c6"), moves)
    }

    @Test fun `a pinned piece cannot move out of the pin`() {
        // White rook on e2 is the only thing between the black rook and the white king.
        val board = mapOf("e1" to 'K', "e2" to 'R', "e8" to 'r')
        val moves = chessLegalMoves(board, white = true).filter { it.from == "e2" }.map { it.to }
        assertTrue(moves.all { it[0] == 'e' }, "it may only travel along the pin, but it did: $moves")
    }

    @Test fun `a king may not walk into check`() {
        val board = mapOf("e1" to 'K', "a2" to 'r')
        val moves = chessLegalMoves(board, white = true).map { it.to }
        assertFalse("e2" in moves)
        assertFalse("d2" in moves)
        assertTrue("d1" in moves)
    }

    @Test fun `check, checkmate and merely losing are told apart`() {
        val backRank = mapOf("a8" to 'R', "g1" to 'K', "g8" to 'k', "f7" to 'p', "g7" to 'p', "h7" to 'p')
        assertTrue(chessInCheck(backRank, white = false))
        assertTrue(chessIsCheckmate(backRank, white = false))

        // Same idea, but the h-pawn has moved and the king has a hole to run to.
        val escapable = mapOf("a8" to 'R', "g1" to 'K', "g8" to 'k', "f7" to 'p', "g7" to 'p', "h6" to 'p')
        assertTrue(chessInCheck(escapable, white = false))
        assertFalse(chessIsCheckmate(escapable, white = false))
    }

    @Test fun `stalemate is not checkmate`() {
        // The white king takes a7 and b7, the rook takes the b-file, and nothing gives check.
        val board = mapOf("a8" to 'k', "a6" to 'K', "b1" to 'R')
        assertFalse(chessInCheck(board, white = false))
        assertTrue(chessLegalMoves(board, white = false).isEmpty())
        assertFalse(chessIsCheckmate(board, white = false))
    }

    @Test fun `a forced mate in two is found, and is not claimed to be a mate in one`() {
        val ladder = mapOf("c1" to 'R', "c6" to 'R', "f3" to 'K', "h7" to 'k')
        assertNull(chessForcedMate(ladder, 1))
        val line = chessForcedMate(ladder, 2)
        assertTrue(line != null && line.size == 3, "expected White, Black, White but got $line")
        assertTrue(chessIsMateIn(ladder, 2))
        assertFalse(chessIsMateIn(ladder, 3), "a mate in two is not also a mate in three")
    }

    @Test fun `a position with no win reports none`() {
        assertNull(chessForcedMate(mapOf("e1" to 'K', "e8" to 'k'), 3))
    }

    @Test fun `a search asked for zero moves finds nothing rather than looping`() {
        assertNull(chessForcedMate(mapOf("a1" to 'R', "g1" to 'K', "g8" to 'k', "g7" to 'p', "f7" to 'p', "h7" to 'p'), 0))
    }
}
