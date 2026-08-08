package com.discnct.app.game.logic

val TIC_TAC_TOE_LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

fun ticTacToeWinnerOf(board: List<Char?>): Char? {
    for (line in TIC_TAC_TOE_LINES) {
        val (a, b, c) = line
        val v = board[a]
        if (v != null && v == board[b] && v == board[c]) return v
    }
    return null
}

fun ticTacToeFindWinningMove(board: List<Char?>, player: Char): Int? {
    for (line in TIC_TAC_TOE_LINES) {
        val values = line.map { board[it] }
        if (values.count { it == player } == 2 && values.count { it == null } == 1) {
            return line[values.indexOf(null)]
        }
    }
    return null
}

/** Not a perfect AI — it can be beaten with a fork, so a win is possible, not just a draw. */
fun ticTacToeAiMove(board: List<Char?>): Int {
    ticTacToeFindWinningMove(board, 'O')?.let { return it }
    ticTacToeFindWinningMove(board, 'X')?.let { return it }
    if (board[4] == null) return 4
    val corners = listOf(0, 2, 6, 8).filter { board[it] == null }
    if (corners.isNotEmpty()) return corners.random()
    return board.indices.filter { board[it] == null }.random()
}
