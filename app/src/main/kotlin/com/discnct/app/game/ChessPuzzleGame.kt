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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.logic.CHESS_PUZZLES
import com.discnct.app.game.logic.chessIsSolution
import com.discnct.app.game.logic.chessPieceGlyph
import com.discnct.app.game.logic.chessRewardForAttempts
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val FILES = "abcdefgh"

/** Unscaled square size; eight of these plus padding is already most of a phone's width. */
private val SQUARE = 38.dp

@Composable
fun ChessPuzzleGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val puzzle = remember { CHESS_PUZZLES.random() }
    var selected by remember { mutableStateOf<String?>(null) }
    var wrongAttempts by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(puzzle.description) }

    fun onSquareTap(square: String) {
        if (over) return
        val current = selected
        if (current == null) {
            val piece = puzzle.board[square]
            if (piece != null && piece.isUpperCase()) selected = square
            return
        }
        if (current == square) {
            selected = null
            return
        }
        if (chessIsSolution(puzzle, current, square)) {
            over = true
            val reward = chessRewardForAttempts(wrongAttempts)
            status = "Mate! Solved in ${wrongAttempts + 1} attempt(s)"
            onFinished(GameOutcome(reward, status))
        } else {
            wrongAttempts += 1
            selected = null
            status = if (wrongAttempts >= 3) {
                over = true
                val solved = "The move was ${puzzle.solutionFrom}–${puzzle.solutionTo}"
                onFinished(GameOutcome(1, "Out of attempts"))
                solved
            } else {
                "Not the mate — try again (${3 - wrongAttempts} left)"
            }
        }
    }

    val scale = gameScaleFactor(base = SQUARE, columns = 8)
    val square = SQUARE * scale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (rank in 8 downTo 1) {
                Row {
                    for (fileIndex in 0..7) {
                        val name = "${FILES[fileIndex]}$rank"
                        val piece = puzzle.board[name]
                        val isLight = (fileIndex + rank) % 2 == 0
                        val isSelected = selected == name
                        Box(
                            modifier = Modifier
                                .size(square)
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.borderVisible)
                                .background(if (isLight) colors.surfaceRaised else colors.surface)
                                .clickable(enabled = !over) { onSquareTap(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != null) {
                                Text(
                                    text = chessPieceGlyph(piece),
                                    style = DiscnctType.heading.scaledBy(scale),
                                    color = if (piece.isUpperCase()) colors.textDisplay else colors.accent,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        PillButton(
            label = "Give Up",
            onClick = {
                if (!over) {
                    over = true
                    status = "The move was ${puzzle.solutionFrom}–${puzzle.solutionTo}"
                    onFinished(GameOutcome(1, "Gave up"))
                }
            },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
