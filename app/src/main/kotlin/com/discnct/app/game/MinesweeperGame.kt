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
import com.discnct.app.game.logic.MINESWEEPER_COLS
import com.discnct.app.game.logic.MINESWEEPER_MINES
import com.discnct.app.game.logic.MINESWEEPER_ROWS
import com.discnct.app.game.logic.MINESWEEPER_SAFE_CELLS
import com.discnct.app.game.logic.minesweeperGenerateBoard
import com.discnct.app.game.logic.minesweeperRevealFrom
import com.discnct.app.game.logic.minesweeperRewardForLoss
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val CELL = 42.dp

@Composable
fun MinesweeperGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var cells by remember { mutableStateOf(minesweeperGenerateBoard()) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Clear the board — $MINESWEEPER_MINES mines hidden") }

    val scale = gameScaleFactor(base = CELL, columns = MINESWEEPER_COLS)
    val cellSize = CELL * scale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))

        Column {
            for (row in 0 until MINESWEEPER_ROWS) {
                Row {
                    for (col in 0 until MINESWEEPER_COLS) {
                        val index = row * MINESWEEPER_COLS + col
                        val cell = cells[index]
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .padding(2.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(if (cell.revealed) colors.surface else colors.surfaceRaised)
                                .clickable(enabled = !over && !cell.revealed) {
                                    val revealed = minesweeperRevealFrom(cells, index)
                                    cells = revealed
                                    val tapped = revealed[index]
                                    when {
                                        tapped.isMine -> {
                                            over = true
                                            val safeRevealed = revealed.count { it.revealed && !it.isMine }
                                            val minutes = minesweeperRewardForLoss(safeRevealed)
                                            status = "Hit a mine — $safeRevealed/$MINESWEEPER_SAFE_CELLS safe cells found"
                                            onFinished(GameOutcome(minutes, status))
                                        }
                                        revealed.count { it.revealed && !it.isMine } == MINESWEEPER_SAFE_CELLS -> {
                                            over = true
                                            status = "Board cleared!"
                                            onFinished(GameOutcome(6, status))
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val label = when {
                                !cell.revealed -> ""
                                cell.isMine -> "X"
                                cell.adjacent > 0 -> cell.adjacent.toString()
                                else -> ""
                            }
                            Text(
                                text = label,
                                style = DiscnctType.bodySmall.scaledBy(scale),
                                color = if (cell.isMine) colors.accent else colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
