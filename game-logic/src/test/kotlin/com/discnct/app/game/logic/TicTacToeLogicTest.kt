package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class TicTacToeLogicTest {
    @Test fun `winnerOf detects row win`() {
        val board = listOf('X', 'X', 'X', null, null, null, null, null, null)
        assertEquals('X', ticTacToeWinnerOf(board))
    }

    @Test fun `winnerOf detects column win`() {
        val board = listOf('O', null, null, 'O', null, null, 'O', null, null)
        assertEquals('O', ticTacToeWinnerOf(board))
    }

    @Test fun `winnerOf detects diagonal win`() {
        val board = listOf('X', null, null, null, 'X', null, null, null, 'X')
        assertEquals('X', ticTacToeWinnerOf(board))
    }

    @Test fun `winnerOf returns null with no winner`() {
        val board = listOf('X', 'O', 'X', 'X', 'O', 'O', 'O', 'X', 'X')
        assertNull(ticTacToeWinnerOf(board))
    }

    @Test fun `aiMove blocks an immediate player win`() {
        val board = listOf('X', 'X', null, null, 'O', null, null, null, null)
        assertEquals(2, ticTacToeAiMove(board))
    }

    @Test fun `aiMove takes an immediate winning move over blocking`() {
        val board = listOf('O', 'O', null, 'X', null, null, null, null, null)
        assertEquals(2, ticTacToeAiMove(board))
    }

    @Test fun `aiMove takes center when available and no immediate tactics`() {
        val board = listOf('X', null, null, null, null, null, null, null, null)
        assertEquals(4, ticTacToeAiMove(board))
    }
}
