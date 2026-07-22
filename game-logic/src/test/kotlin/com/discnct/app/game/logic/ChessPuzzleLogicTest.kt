package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ChessPuzzleLogicTest {
    @Test fun `every puzzle's solution move starts on a white piece`() {
        for (puzzle in CHESS_PUZZLES) {
            val piece = puzzle.board[puzzle.solutionFrom]
            assertTrue(piece != null && piece.isUpperCase(), "solutionFrom ${puzzle.solutionFrom} must hold a white piece in: $puzzle")
        }
    }

    @Test fun `every puzzle's solution move is not a no-op and targets a valid square`() {
        val files = "abcdefgh"
        for (puzzle in CHESS_PUZZLES) {
            assertNotEquals(puzzle.solutionFrom, puzzle.solutionTo)
            for (square in listOf(puzzle.solutionFrom, puzzle.solutionTo)) {
                assertTrue(square.length == 2 && square[0] in files && square[1] in '1'..'8', "invalid square $square")
            }
        }
    }

    @Test fun `every puzzle's solution does not capture the puzzle's own king`() {
        for (puzzle in CHESS_PUZZLES) {
            val destination = puzzle.board[puzzle.solutionTo]
            assertTrue(destination == null || destination.lowercaseChar() != 'k', "solution ${puzzle.solutionFrom}-${puzzle.solutionTo} captures a king in: $puzzle")
        }
    }

    @Test fun `chessIsSolution accepts only the exact from-to pair`() {
        val puzzle = CHESS_PUZZLES[0]
        assertTrue(chessIsSolution(puzzle, puzzle.solutionFrom, puzzle.solutionTo))
        assertFalse(chessIsSolution(puzzle, puzzle.solutionTo, puzzle.solutionFrom))
        assertFalse(chessIsSolution(puzzle, "a1", "a2"))
    }

    @Test fun `chessRewardForAttempts pays out most for zero wrong attempts`() {
        assertEquals(7, chessRewardForAttempts(0))
        assertEquals(4, chessRewardForAttempts(1))
        assertEquals(1, chessRewardForAttempts(2))
        assertEquals(1, chessRewardForAttempts(5))
    }

    @Test fun `pieceGlyph maps every supported piece letter to a non-empty glyph`() {
        for (p in "KQRBNPkqrbnp") {
            assertTrue(chessPieceGlyph(p).isNotEmpty())
        }
    }
}
