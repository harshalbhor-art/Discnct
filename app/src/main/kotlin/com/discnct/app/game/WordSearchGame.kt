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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.discnct.app.game.logic.Cell
import com.discnct.app.game.logic.randomWordSearch
import com.discnct.app.game.logic.wordSearchMatch
import com.discnct.app.game.logic.wordSearchReward
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val CELL = 32.dp

/**
 * Word search: nine by nine, six words, eight directions.
 *
 * Selection is two taps — first letter, last letter — rather than a drag. On a grid this small a
 * drag has to be tracked against cells about a fingertip wide, and a selection that keeps slipping
 * onto the wrong row turns a calm game into a fiddly one. Two taps can't miss. The first is
 * outlined while it waits for the second, and a word stays lit once it's found.
 *
 * The puzzle ends when the last word is found; giving up early still pays, in proportion to what
 * was found, so a stuck player has something better to do than stare at it.
 */
@Composable
fun WordSearchGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val grid = remember { randomWordSearch() }
    var found by remember { mutableStateOf(emptySet<String>()) }
    var anchor by remember { mutableStateOf<Cell?>(null) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Find all ${grid.placements.size} — any direction.") }

    // Every square belonging to a word already found, so it can stay lit for the rest of the game.
    val solvedCells = grid.placements.filter { it.word in found }.flatMap { it.cells }.toSet()

    fun onCellTap(cell: Cell) {
        if (over) return
        val start = anchor
        if (start == null) {
            anchor = cell
            return
        }
        if (start == cell) {
            anchor = null
            return
        }
        anchor = null
        val match = wordSearchMatch(grid, start, cell)
        if (match == null) {
            status = "Not one of them — try again."
            return
        }
        if (match.word in found) {
            status = "${match.word} — already found."
            return
        }
        found = found + match.word
        if (found.size == grid.placements.size) {
            over = true
            status = "All ${found.size} found."
            onFinished(GameOutcome(wordSearchReward(found.size, grid.placements.size), status))
        } else {
            status = "${match.word} — ${grid.placements.size - found.size} to go."
        }
    }

    val scale = gameScaleFactor(base = CELL, columns = grid.size)
    val cellSize = CELL * scale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (row in 0 until grid.size) {
                Row {
                    for (col in 0 until grid.size) {
                        val cell = Cell(row, col)
                        val isAnchor = anchor == cell
                        val isSolved = cell in solvedCells
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .padding(1.dp)
                                .border(
                                    width = if (isAnchor) 2.dp else 1.dp,
                                    color = if (isAnchor) colors.accent else colors.borderVisible,
                                )
                                .background(if (isSolved) colors.surfaceRaised else colors.surface)
                                .clickable(enabled = !over) { onCellTap(cell) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = grid.letterAt(row, col).toString(),
                                style = DiscnctType.bodySmall.scaledBy(scale),
                                color = if (isSolved) colors.accent else colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        WordList(words = grid.words, found = found)

        Spacer(modifier = Modifier.height(16.dp))
        PillButton(
            label = "Give Up",
            onClick = {
                if (!over) {
                    over = true
                    status = "Stopped with ${found.size} of ${grid.placements.size}."
                    onFinished(GameOutcome(wordSearchReward(found.size, grid.placements.size), status))
                }
            },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The words to look for, struck through as they turn up. Two rows so a long list still fits. */
@Composable
private fun WordList(words: List<String>, found: Set<String>) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.chunked(3).forEach { line ->
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                line.forEach { word ->
                    val isFound = word in found
                    Text(
                        text = word,
                        style = DiscnctType.bodySmall,
                        color = if (isFound) colors.textDisabled else colors.textPrimary,
                        textDecoration = if (isFound) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
