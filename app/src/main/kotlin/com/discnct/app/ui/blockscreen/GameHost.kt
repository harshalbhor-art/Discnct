package com.discnct.app.ui.blockscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.BreathingExercise
import com.discnct.app.game.ChessPuzzleGame
import com.discnct.app.game.DinoRunGame
import com.discnct.app.game.FidgetSpinnerGame
import com.discnct.app.game.Game2048
import com.discnct.app.game.GameOutcome
import com.discnct.app.game.GameType
import com.discnct.app.game.MinesweeperGame
import com.discnct.app.game.TicTacToeGame
import com.discnct.app.game.WordSearchGame
import com.discnct.app.game.WordleGame
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Dispatches to whichever mini-game [GamePool] handed the block screen.
 *
 * The board is centred rather than top-aligned: a `verticalScroll` column measures its content
 * against an unbounded height, so `verticalArrangement` has nothing to centre within — putting the
 * scrolling column inside a Box and aligning *it* is what actually works. Short games sit in the
 * middle of the screen; anything taller than the viewport still scrolls.
 */
@Composable
fun GameHost(gameType: GameType, onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current

    Box(
        modifier = Modifier.fillMaxSize().background(colors.black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 28.dp),
        ) {
            Text(
                text = gameType.displayName.uppercase(),
                style = DiscnctType.label,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(20.dp))

            when (gameType) {
                GameType.CHESS_PUZZLE -> ChessPuzzleGame(onFinished)
                GameType.TIC_TAC_TOE -> TicTacToeGame(onFinished)
                GameType.MINESWEEPER -> MinesweeperGame(onFinished)
                GameType.WORDLE -> WordleGame(onFinished)
                GameType.WORD_SEARCH -> WordSearchGame(onFinished)
                GameType.DINO_RUN -> DinoRunGame(onFinished)
                GameType.GAME_2048 -> Game2048(onFinished)
                GameType.BREATHING -> BreathingExercise(onFinished)
                GameType.FIDGET_SPINNER -> FidgetSpinnerGame(onFinished)
            }
        }
    }
}
