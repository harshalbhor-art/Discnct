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
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private data class ChessPuzzle(
    val board: Map<String, Char>,
    val solutionFrom: String,
    val solutionTo: String,
    val description: String,
)

// Hand-verified mate-in-1 positions. Uppercase = white, lowercase = black.
private val PUZZLES = listOf(
    ChessPuzzle(
        board = mapOf("a1" to 'R', "g1" to 'K', "g8" to 'k', "f7" to 'p', "g7" to 'p', "h7" to 'p'),
        solutionFrom = "a1",
        solutionTo = "a8",
        description = "White to move — back-rank mate.",
    ),
    ChessPuzzle(
        board = mapOf("d6" to 'N', "a1" to 'K', "h8" to 'k', "g8" to 'r', "g7" to 'p', "h7" to 'p'),
        solutionFrom = "d6",
        solutionTo = "f7",
        description = "White to move — smothered mate.",
    ),
    ChessPuzzle(
        board = mapOf("h7" to 'Q', "b6" to 'K', "a8" to 'k'),
        solutionFrom = "h7",
        solutionTo = "a7",
        description = "White to move — corner the king.",
    ),
)

private val FILES = "abcdefgh"

private fun pieceGlyph(piece: Char): String = when (piece) {
    'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
}

@Composable
fun ChessPuzzleGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val puzzle = remember { PUZZLES.random() }
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
        if (current == puzzle.solutionFrom && square == puzzle.solutionTo) {
            over = true
            val reward = when (wrongAttempts) { 0 -> 7; 1 -> 4; else -> 1 }
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

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, style = DiscnctType.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (rank in 8 downTo 1) {
                Row {
                    for (fileIndex in 0..7) {
                        val square = "${FILES[fileIndex]}$rank"
                        val piece = puzzle.board[square]
                        val isLight = (fileIndex + rank) % 2 == 0
                        val isSelected = selected == square
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.borderVisible)
                                .background(if (isLight) colors.surfaceRaised else colors.surface)
                                .clickable(enabled = !over) { onSquareTap(square) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != null) {
                                Text(
                                    text = pieceGlyph(piece),
                                    style = DiscnctType.heading,
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
