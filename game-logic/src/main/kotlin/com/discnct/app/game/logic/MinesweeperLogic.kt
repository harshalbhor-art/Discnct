package com.discnct.app.game.logic

const val MINESWEEPER_COLS = 6
const val MINESWEEPER_ROWS = 6
const val MINESWEEPER_MINES = 6
const val MINESWEEPER_SAFE_CELLS = MINESWEEPER_COLS * MINESWEEPER_ROWS - MINESWEEPER_MINES

data class MineCell(val isMine: Boolean = false, val adjacent: Int = 0, val revealed: Boolean = false)

fun minesweeperNeighborsOf(index: Int): List<Int> {
    val row = index / MINESWEEPER_COLS
    val col = index % MINESWEEPER_COLS
    val result = mutableListOf<Int>()
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr
            val nc = col + dc
            if (nr in 0 until MINESWEEPER_ROWS && nc in 0 until MINESWEEPER_COLS) result += nr * MINESWEEPER_COLS + nc
        }
    }
    return result
}

fun minesweeperGenerateBoard(): List<MineCell> {
    val mineIndices = (0 until MINESWEEPER_COLS * MINESWEEPER_ROWS).shuffled().take(MINESWEEPER_MINES).toSet()
    return List(MINESWEEPER_COLS * MINESWEEPER_ROWS) { i ->
        val isMine = i in mineIndices
        val adjacent = if (isMine) 0 else minesweeperNeighborsOf(i).count { it in mineIndices }
        MineCell(isMine = isMine, adjacent = adjacent)
    }
}

fun minesweeperRevealFrom(cells: List<MineCell>, start: Int): List<MineCell> {
    val result = cells.toMutableList()
    val queue = ArrayDeque<Int>()
    queue.add(start)
    val visited = mutableSetOf<Int>()
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        if (!visited.add(i)) continue
        val cell = result[i]
        if (cell.revealed) continue
        result[i] = cell.copy(revealed = true)
        if (cell.adjacent == 0 && !cell.isMine) {
            for (n in minesweeperNeighborsOf(i)) if (!result[n].revealed) queue.add(n)
        }
    }
    return result
}

fun minesweeperRewardForLoss(safeRevealed: Int): Int =
    (1 + (5 * safeRevealed.toFloat() / MINESWEEPER_SAFE_CELLS).toInt()).coerceIn(1, 6)
