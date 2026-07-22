package com.discnct.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

private fun winnerOf(board: List<Char?>): Char? {
    for (line in LINES) {
        val (a, b, c) = line
        val v = board[a]
        if (v != null && v == board[b] && v == board[c]) return v
    }
    return null
}

private fun findWinningMove(board: List<Char?>, player: Char): Int? {
    for (line in LINES) {
        val values = line.map { board[it] }
        if (values.count { it == player } == 2 && values.count { it == null } == 1) {
            return line[values.indexOf(null)]
        }
    }
    return null
}

/** Not a perfect AI — it can be beaten with a fork, so a win is possible, not just a draw. */
private fun aiMove(board: List<Char?>): Int {
    findWinningMove(board, 'O')?.let { return it }
    findWinningMove(board, 'X')?.let { return it }
    if (board[4] == null) return 4
    val corners = listOf(0, 2, 6, 8).filter { board[it] == null }
    if (corners.isNotEmpty()) return corners.random()
    return board.indices.filter { board[it] == null }.random()
}

@Composable
fun TicTacToeGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var board by remember { mutableStateOf<List<Char?>>(List(9) { null }) }
    var status by remember { mutableStateOf("Your move — you're X") }
    var over by remember { mutableStateOf(false) }

    fun evaluate(b: List<Char?>): Boolean {
        val winner = winnerOf(b)
        return when {
            winner == 'X' -> {
                status = "You win!"
                over = true
                onFinished(GameOutcome(6, "Beat the AI"))
                true
            }
            winner == 'O' -> {
                status = "AI wins."
                over = true
                onFinished(GameOutcome(1, "Lost to the AI"))
                true
            }
            b.none { it == null } -> {
                status = "Draw."
                over = true
                onFinished(GameOutcome(3, "Drew with the AI"))
                true
            }
            else -> false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))

        Column {
            for (row in 0 until 3) {
                Row {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .padding(4.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(colors.surface)
                                .clickable(enabled = !over && board[index] == null) {
                                    val afterPlayer = board.toMutableList().also { it[index] = 'X' }
                                    board = afterPlayer
                                    if (!evaluate(afterPlayer)) {
                                        val aiIndex = aiMove(afterPlayer)
                                        val afterAi = afterPlayer.toMutableList().also { it[aiIndex] = 'O' }
                                        board = afterAi
                                        evaluate(afterAi)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = board[index]?.toString() ?: "",
                                style = DiscnctType.displayMd,
                                color = if (board[index] == 'X') colors.textDisplay else colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
