package com.discnct.app.game.logic

typealias Board2048 = List<List<Int>>

fun Board2048.transpose(): Board2048 = List(4) { c -> List(4) { r -> this[r][c] } }
fun Board2048.reverseRows(): Board2048 = map { it.reversed() }

fun mergeRowLeft(row: List<Int>): Pair<List<Int>, Boolean> {
    val nonZero = row.filter { it != 0 }
    val merged = mutableListOf<Int>()
    var i = 0
    while (i < nonZero.size) {
        if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
            merged += nonZero[i] * 2
            i += 2
        } else {
            merged += nonZero[i]
            i += 1
        }
    }
    while (merged.size < 4) merged += 0
    return merged to (merged != row)
}

fun moveLeft2048(board: Board2048): Pair<Board2048, Boolean> {
    var moved = false
    val result = board.map { row ->
        val (merged, rowMoved) = mergeRowLeft(row)
        if (rowMoved) moved = true
        merged
    }
    return result to moved
}

fun moveRight2048(board: Board2048): Pair<Board2048, Boolean> {
    val (result, moved) = moveLeft2048(board.reverseRows())
    return result.reverseRows() to moved
}

fun moveUp2048(board: Board2048): Pair<Board2048, Boolean> {
    val (result, moved) = moveLeft2048(board.transpose())
    return result.transpose() to moved
}

fun moveDown2048(board: Board2048): Pair<Board2048, Boolean> {
    val (result, moved) = moveLeft2048(board.transpose().reverseRows())
    return result.reverseRows().transpose() to moved
}

fun spawnRandomTile2048(board: Board2048): Board2048 {
    val emptyCells = buildList {
        for (r in 0..3) for (c in 0..3) if (board[r][c] == 0) add(r to c)
    }
    if (emptyCells.isEmpty()) return board
    val (r, c) = emptyCells.random()
    val value = if (Math.random() < 0.9) 2 else 4
    return board.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, v -> if (ci == c) value else v } else row }
}

fun emptyBoard2048(): Board2048 = List(4) { List(4) { 0 } }

fun initialBoard2048(): Board2048 = spawnRandomTile2048(spawnRandomTile2048(emptyBoard2048()))

fun canMove2048(board: Board2048): Boolean =
    moveLeft2048(board).second || moveRight2048(board).second || moveUp2048(board).second || moveDown2048(board).second

fun maxTile2048(board: Board2048): Int = board.flatten().maxOrNull() ?: 0

fun rewardForMaxTile2048(max: Int): Int = when {
    max >= 512 -> 7
    max >= 256 -> 6
    max >= 128 -> 5
    max >= 64 -> 4
    max >= 32 -> 3
    else -> 1
}
