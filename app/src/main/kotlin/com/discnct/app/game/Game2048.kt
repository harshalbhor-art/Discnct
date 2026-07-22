package com.discnct.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private typealias Board = List<List<Int>>

private fun List<List<Int>>.transpose(): Board = List(4) { c -> List(4) { r -> this[r][c] } }
private fun List<List<Int>>.reverseRows(): Board = map { it.reversed() }

private fun mergeRowLeft(row: List<Int>): Pair<List<Int>, Boolean> {
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

private fun moveLeft(board: Board): Pair<Board, Boolean> {
    var moved = false
    val result = board.map { row ->
        val (merged, rowMoved) = mergeRowLeft(row)
        if (rowMoved) moved = true
        merged
    }
    return result to moved
}

private fun moveRight(board: Board): Pair<Board, Boolean> {
    val (result, moved) = moveLeft(board.reverseRows())
    return result.reverseRows() to moved
}

private fun moveUp(board: Board): Pair<Board, Boolean> {
    val (result, moved) = moveLeft(board.transpose())
    return result.transpose() to moved
}

private fun moveDown(board: Board): Pair<Board, Boolean> {
    val (result, moved) = moveLeft(board.transpose().reverseRows())
    return result.reverseRows().transpose() to moved
}

private fun spawnRandomTile(board: Board): Board {
    val emptyCells = buildList {
        for (r in 0..3) for (c in 0..3) if (board[r][c] == 0) add(r to c)
    }
    if (emptyCells.isEmpty()) return board
    val (r, c) = emptyCells.random()
    val value = if (Math.random() < 0.9) 2 else 4
    return board.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, v -> if (ci == c) value else v } else row }
}

private fun emptyBoard(): Board = List(4) { List(4) { 0 } }

private fun initialBoard(): Board = spawnRandomTile(spawnRandomTile(emptyBoard()))

private fun canMove(board: Board): Boolean =
    moveLeft(board).second || moveRight(board).second || moveUp(board).second || moveDown(board).second

private fun maxTile(board: Board): Int = board.flatten().maxOrNull() ?: 0

private fun rewardForMaxTile(max: Int): Int = when {
    max >= 512 -> 7
    max >= 256 -> 6
    max >= 128 -> 5
    max >= 64 -> 4
    max >= 32 -> 3
    else -> 1
}

@Composable
fun Game2048(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var board by remember { mutableStateOf(initialBoard()) }
    var over by remember { mutableStateOf(false) }

    fun applyMove(move: (Board) -> Pair<Board, Boolean>) {
        if (over) return
        val (moved, changed) = move(board)
        if (!changed) return
        val next = spawnRandomTile(moved)
        board = next
        if (!canMove(next)) {
            over = true
            onFinished(GameOutcome(rewardForMaxTile(maxTile(next)), "Reached ${maxTile(next)}"))
        }
    }

    fun claim() {
        if (over) return
        over = true
        onFinished(GameOutcome(rewardForMaxTile(maxTile(board)), "Reached ${maxTile(board)}"))
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Best tile: ${maxTile(board)} — merge equal tiles",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (row in board) {
                Row {
                    for (value in row) {
                        val highValue = value >= 128
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .padding(3.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(
                                    when {
                                        value == 0 -> colors.black
                                        highValue -> colors.accent
                                        else -> colors.surfaceRaised
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (value == 0) "" else value.toString(),
                                style = DiscnctType.subheading,
                                color = if (highValue) colors.black else colors.textDisplay,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        DirectionPad(
            onUp = { applyMove(::moveUp) },
            onDown = { applyMove(::moveDown) },
            onLeft = { applyMove(::moveLeft) },
            onRight = { applyMove(::moveRight) },
        )

        Spacer(modifier = Modifier.height(20.dp))
        PillButton(
            label = "Claim Reward",
            onClick = { claim() },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DirectionPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    val colors = LocalDiscnctColors.current

    @Composable
    fun Key(label: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, colors.borderVisible)
                .background(colors.surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = DiscnctType.heading, color = colors.textDisplay)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Key("↑", onUp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("←", onLeft)
            Spacer(modifier = Modifier.size(48.dp))
            Key("→", onRight)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Key("↓", onDown)
    }
}
