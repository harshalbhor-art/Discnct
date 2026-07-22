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

private const val COLS = 6
private const val ROWS = 6
private const val MINES = 6
private const val SAFE_CELLS = COLS * ROWS - MINES

private data class MineCell(val isMine: Boolean = false, val adjacent: Int = 0, val revealed: Boolean = false)

private fun neighborsOf(index: Int): List<Int> {
    val row = index / COLS
    val col = index % COLS
    val result = mutableListOf<Int>()
    for (dr in -1..1) {
        for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr
            val nc = col + dc
            if (nr in 0 until ROWS && nc in 0 until COLS) result += nr * COLS + nc
        }
    }
    return result
}

private fun generateBoard(): List<MineCell> {
    val mineIndices = (0 until COLS * ROWS).shuffled().take(MINES).toSet()
    return List(COLS * ROWS) { i ->
        val isMine = i in mineIndices
        val adjacent = if (isMine) 0 else neighborsOf(i).count { it in mineIndices }
        MineCell(isMine = isMine, adjacent = adjacent)
    }
}

private fun revealFrom(cells: List<MineCell>, start: Int): List<MineCell> {
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
            for (n in neighborsOf(i)) if (!result[n].revealed) queue.add(n)
        }
    }
    return result
}

@Composable
fun MinesweeperGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var cells by remember { mutableStateOf(generateBoard()) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Clear the board — $MINES mines hidden") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))

        Column {
            for (row in 0 until ROWS) {
                Row {
                    for (col in 0 until COLS) {
                        val index = row * COLS + col
                        val cell = cells[index]
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .padding(2.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(if (cell.revealed) colors.surface else colors.surfaceRaised)
                                .clickable(enabled = !over && !cell.revealed) {
                                    val revealed = revealFrom(cells, index)
                                    cells = revealed
                                    val tapped = revealed[index]
                                    when {
                                        tapped.isMine -> {
                                            over = true
                                            val safeRevealed = revealed.count { it.revealed && !it.isMine }
                                            val fraction = safeRevealed.toFloat() / SAFE_CELLS
                                            val minutes = (1 + (5 * fraction).toInt()).coerceIn(1, 6)
                                            status = "Hit a mine — $safeRevealed/$SAFE_CELLS safe cells found"
                                            onFinished(GameOutcome(minutes, status))
                                        }
                                        revealed.count { it.revealed && !it.isMine } == SAFE_CELLS -> {
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
                                style = DiscnctType.bodySmall,
                                color = if (cell.isMine) colors.accent else colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
