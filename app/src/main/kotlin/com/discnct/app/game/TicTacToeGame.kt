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
import com.discnct.app.game.logic.ticTacToeAiMove
import com.discnct.app.game.logic.ticTacToeWinnerOf
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val CELL = 88.dp

@Composable
fun TicTacToeGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var board by remember { mutableStateOf<List<Char?>>(List(9) { null }) }
    var status by remember { mutableStateOf("Your move — you're X") }
    var over by remember { mutableStateOf(false) }

    fun evaluate(b: List<Char?>): Boolean {
        val winner = ticTacToeWinnerOf(b)
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

    val scale = gameScaleFactor(base = CELL, columns = 3)
    val cell = CELL * scale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
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
                                .size(cell)
                                .padding(4.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(colors.surface)
                                .clickable(enabled = !over && board[index] == null) {
                                    val afterPlayer = board.toMutableList().also { it[index] = 'X' }
                                    board = afterPlayer
                                    if (!evaluate(afterPlayer)) {
                                        val aiIndex = ticTacToeAiMove(afterPlayer)
                                        val afterAi = afterPlayer.toMutableList().also { it[aiIndex] = 'O' }
                                        board = afterAi
                                        evaluate(afterAi)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = board[index]?.toString() ?: "",
                                style = DiscnctType.displayMd.scaledBy(scale),
                                color = if (board[index] == 'X') colors.textDisplay else colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
