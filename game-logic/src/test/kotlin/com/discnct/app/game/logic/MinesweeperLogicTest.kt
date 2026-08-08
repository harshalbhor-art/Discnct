package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MinesweeperLogicTest {
    @Test fun `generateBoard places exactly the configured number of mines`() {
        val board = minesweeperGenerateBoard()
        assertEquals(MINESWEEPER_MINES, board.count { it.isMine })
        assertEquals(MINESWEEPER_COLS * MINESWEEPER_ROWS, board.size)
    }

    @Test fun `generateBoard computes correct adjacent mine counts`() {
        val board = minesweeperGenerateBoard()
        for (i in board.indices) {
            if (board[i].isMine) continue
            val expected = minesweeperNeighborsOf(i).count { board[it].isMine }
            assertEquals(expected, board[i].adjacent)
        }
    }

    @Test fun `neighborsOf excludes out-of-bounds cells for a corner`() {
        val neighbors = minesweeperNeighborsOf(0)
        assertEquals(setOf(1, MINESWEEPER_COLS, MINESWEEPER_COLS + 1), neighbors.toSet())
    }

    @Test fun `revealFrom flood-fills all connected zero-adjacent cells`() {
        val cells = List(MINESWEEPER_COLS * MINESWEEPER_ROWS) { MineCell(isMine = false, adjacent = 0) }
        val revealed = minesweeperRevealFrom(cells, 0)
        assertTrue(revealed.all { it.revealed })
    }

    @Test fun `revealFrom never opens a mine through cascade on a consistent board`() {
        repeat(20) {
            val board = minesweeperGenerateBoard()
            val start = board.indexOfFirst { !it.isMine }
            val revealed = minesweeperRevealFrom(board, start)
            assertTrue(revealed.filter { it.isMine }.none { it.revealed })
        }
    }

    @Test fun `rewardForLoss scales between 1 and 6 with cells found`() {
        assertEquals(1, minesweeperRewardForLoss(0))
        assertEquals(6, minesweeperRewardForLoss(MINESWEEPER_SAFE_CELLS))
        val mid = minesweeperRewardForLoss(MINESWEEPER_SAFE_CELLS / 2)
        assertTrue(mid in 1..6)
    }
}
