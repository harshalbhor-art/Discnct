package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ChessPuzzleLogicTest {

    @Test fun `every shipped puzzle really is a mate in the number of moves it claims`() {
        // The whole reason the engine exists. Before it, "mate in one" was a comment, and a
        // position that quietly wasn't one would have shipped with nothing to catch it.
        for (puzzle in CHESS_PUZZLES) {
            assertTrue(
                chessIsMateIn(puzzle.board, puzzle.movesToMate),
                "not a mate in ${puzzle.movesToMate}: ${puzzle.description}",
            )
        }
    }

    @Test fun `every stored line is the real line, re-derived rather than trusted`() {
        for (puzzle in CHESS_PUZZLES) {
            var board = puzzle.board
            puzzle.line.forEachIndexed { index, move ->
                val whiteToMove = index % 2 == 0
                assertTrue(
                    move in chessLegalMoves(board, whiteToMove),
                    "illegal move ${move.from}-${move.to} in: ${puzzle.description}",
                )
                board = chessApply(board, move)
            }
            assertTrue(chessIsCheckmate(board, white = false), "line doesn't mate: ${puzzle.description}")
            assertEquals(
                puzzle.movesToMate * 2 - 1,
                puzzle.line.size,
                "line length disagrees with movesToMate in: ${puzzle.description}",
            )
        }
    }

    @Test fun `in every multi-move puzzle Black has exactly one legal reply`() {
        // What makes the deeper puzzles fair: the answer the player sees isn't the engine choosing
        // for Black, it's the only move on the board.
        for (puzzle in CHESS_PUZZLES.filter { it.movesToMate > 1 }) {
            var board = puzzle.board
            puzzle.line.forEachIndexed { index, move ->
                if (index % 2 == 1) {
                    assertEquals(
                        1,
                        chessLegalMoves(board, white = false).size,
                        "Black had a choice in: ${puzzle.description}",
                    )
                }
                board = chessApply(board, move)
            }
        }
    }

    @Test fun `the puzzles cover one, two and three moves`() {
        assertEquals(setOf(1, 2, 3), CHESS_PUZZLES.map { it.movesToMate }.toSet())
    }

    @Test fun `a second mating move is accepted, not called wrong`() {
        // The bug this replaces: the queen puzzle has two mates and only one was the "answer".
        val puzzle = CHESS_PUZZLES.first { it.board == mapOf("h7" to 'Q', "b6" to 'K', "a8" to 'k') }
        assertEquals(ChessAttempt.Solved, chessPlay(puzzle.board, puzzle.movesToMate, ChessMove("h7", "b7")))
        assertEquals(ChessAttempt.Solved, chessPlay(puzzle.board, puzzle.movesToMate, ChessMove("h7", "a7")))
    }

    @Test fun `a legal move that throws the win away is wrong, not illegal`() {
        val puzzle = CHESS_PUZZLES.first { it.movesToMate == 2 }
        val idle = chessLegalMoves(puzzle.board, white = true)
            .first { chessPlay(puzzle.board, puzzle.movesToMate, it) == ChessAttempt.Wrong }
        assertEquals(ChessAttempt.Wrong, chessPlay(puzzle.board, puzzle.movesToMate, idle))
    }

    @Test fun `a move the piece cannot make is illegal`() {
        val puzzle = CHESS_PUZZLES.first()
        assertEquals(ChessAttempt.Illegal, chessPlay(puzzle.board, puzzle.movesToMate, ChessMove("a1", "b3")))
        assertEquals(ChessAttempt.Illegal, chessPlay(puzzle.board, puzzle.movesToMate, ChessMove("d4", "d5")))
    }

    @Test fun `a right move in a deep puzzle plays Black's reply and shortens the puzzle`() {
        val puzzle = CHESS_PUZZLES.first { it.movesToMate == 3 }
        val result = chessPlay(puzzle.board, puzzle.movesToMate, puzzle.line[0])
        assertTrue(result is ChessAttempt.Continues, "expected the puzzle to continue, got $result")
        result as ChessAttempt.Continues
        assertEquals(2, result.movesToMate)
        assertEquals(puzzle.line[1], result.reply)
        assertTrue(chessIsMateIn(result.board, 2), "the position handed back must still be won")
    }

    @Test fun `solving a whole line end to end lands on Solved`() {
        for (puzzle in CHESS_PUZZLES) {
            var board = puzzle.board
            var left = puzzle.movesToMate
            var result: ChessAttempt = ChessAttempt.Wrong
            for (index in puzzle.line.indices step 2) {
                result = chessPlay(board, left, puzzle.line[index])
                if (result is ChessAttempt.Continues) {
                    board = result.board
                    left = result.movesToMate
                }
            }
            assertEquals(ChessAttempt.Solved, result, "couldn't play out: ${puzzle.description}")
        }
    }

    @Test fun `a deeper puzzle is worth more, and a clean solve is worth most`() {
        assertTrue(chessRewardForAttempts(0, movesToMate = 3) > chessRewardForAttempts(0, movesToMate = 1))
        assertTrue(chessRewardForAttempts(0, movesToMate = 2) > chessRewardForAttempts(1, movesToMate = 2))
        assertTrue(chessRewardForAttempts(5, movesToMate = 3) >= 1, "a finished puzzle always pays something")
    }

    @Test fun `pieceGlyph maps every supported piece letter to a non-empty glyph`() {
        for (piece in "KQRBNPkqrbnp") {
            assertTrue(chessPieceGlyph(piece).isNotEmpty())
        }
    }

    @Test fun `the give-up hint reads as a list of moves`() {
        assertEquals("g2–c2, e1–f1, c2–f2", chessLineNotation(CHESS_PUZZLES.first { it.board.containsKey("g2") }.line))
    }
}
