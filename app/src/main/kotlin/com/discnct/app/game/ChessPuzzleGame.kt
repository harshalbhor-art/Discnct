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
import com.discnct.app.game.logic.ChessAttempt
import com.discnct.app.game.logic.ChessBoard
import com.discnct.app.game.logic.ChessMove
import com.discnct.app.game.logic.chessApply
import com.discnct.app.game.logic.chessLineNotation
import com.discnct.app.game.logic.chessPieceGlyph
import com.discnct.app.game.logic.chessPlay
import com.discnct.app.game.logic.chessRewardForAttempts
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private const val FILES = "abcdefgh"

/** Unscaled square size; eight of these plus padding is already most of a phone's width. */
private val SQUARE = 38.dp

/** Wrong moves allowed before the puzzle gives up on you. Illegal ones don't count. */
private const val MAX_WRONG = 3

/**
 * Chess puzzles, now up to three moves deep.
 *
 * The board is live rather than a picture: your move is played, Black's forced answer is played
 * back, and the position you're looking at is the one you have to mate from next. Which move is
 * right is decided by the engine, so a second mate is a mate — the previous version compared
 * against a single stored answer and called every other winning move a mistake.
 *
 * Illegal moves are free. Being told "a rook doesn't move like that" isn't a wrong guess, it's the
 * board explaining itself, and charging an attempt for it would punish people for not knowing the
 * rules rather than for not seeing the mate.
 */
@Composable
fun ChessPuzzleGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val puzzle = remember { CHESS_PUZZLES.random() }
    var board: ChessBoard by remember { mutableStateOf(puzzle.board) }
    var movesLeft by remember { mutableIntStateOf(puzzle.movesToMate) }
    var selected by remember { mutableStateOf<String?>(null) }
    var lastReply by remember { mutableStateOf<ChessMove?>(null) }
    var wrongAttempts by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(puzzle.description) }

    fun finish(minutes: Int, label: String) {
        over = true
        onFinished(GameOutcome(minutes, label))
    }

    fun onSquareTap(square: String) {
        if (over) return
        val from = selected
        if (from == null) {
            val piece = board[square]
            if (piece != null && piece.isUpperCase()) selected = square
            return
        }
        if (from == square) {
            selected = null
            return
        }
        // Tapping another of your own pieces re-aims rather than attempting a nonsense move.
        val target = board[square]
        if (target != null && target.isUpperCase()) {
            selected = square
            return
        }
        selected = null

        when (val result = chessPlay(board, movesLeft, ChessMove(from, square))) {
            ChessAttempt.Illegal -> status = "That piece can't move there."
            ChessAttempt.Solved -> {
                board = chessApply(board, ChessMove(from, square))
                lastReply = null
                status = "Checkmate — solved in ${wrongAttempts + 1} attempt(s)."
                finish(chessRewardForAttempts(wrongAttempts, puzzle.movesToMate), status)
            }
            is ChessAttempt.Continues -> {
                board = result.board
                movesLeft = result.movesToMate
                lastReply = result.reply
                status = "Forced: ${result.reply.from}–${result.reply.to}. " +
                    "Mate in ${result.movesToMate}."
            }
            ChessAttempt.Wrong -> {
                wrongAttempts += 1
                if (wrongAttempts >= MAX_WRONG) {
                    status = "Out of attempts. The line was ${chessLineNotation(puzzle.line)}."
                    finish(1, "Out of attempts")
                } else {
                    status = "Legal, but it lets the mate slip — " +
                        "${MAX_WRONG - wrongAttempts} attempt(s) left."
                }
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
                        val piece = board[name]
                        val isLight = (fileIndex + rank) % 2 == 0
                        val isSelected = selected == name
                        // Where Black just came from and went to, so a forced reply isn't a piece
                        // that silently teleported while you were looking at your own move.
                        val isReply = lastReply?.let { name == it.from || name == it.to } == true
                        Box(
                            modifier = Modifier
                                .size(square)
                                .border(
                                    width = if (isSelected || isReply) 2.dp else 1.dp,
                                    color = when {
                                        isSelected -> colors.accent
                                        isReply -> colors.warning
                                        else -> colors.borderVisible
                                    },
                                )
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
                    status = "The line was ${chessLineNotation(puzzle.line)}."
                    finish(1, "Gave up")
                }
            },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
