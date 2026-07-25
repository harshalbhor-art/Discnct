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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.logic.WORDLE_MAX_GUESSES
import com.discnct.app.game.logic.WORDLE_WORD_LENGTH
import com.discnct.app.game.logic.WORDLE_WORD_LIST
import com.discnct.app.game.logic.WordleLetterState
import com.discnct.app.game.logic.wordleRewardForGuesses
import com.discnct.app.game.logic.wordleScoreGuess
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val TILE = 40.dp
private val KEY_WIDTH = 28.dp
private val KEY_HEIGHT = 40.dp
private val KEY_GAP = 4.dp

/** DEL and GO are this many letter-keys wide. */
private const val WIDE_KEY_RATIO = 1.6f

@Composable
fun WordleGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val target = remember { WORDLE_WORD_LIST.random() }
    var guesses by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableStateOf("") }
    var over by remember { mutableStateOf(false) }
    var keyStates by remember { mutableStateOf(mapOf<Char, WordleLetterState>()) }

    fun bestOf(a: WordleLetterState, b: WordleLetterState): WordleLetterState {
        val rank = { s: WordleLetterState -> when (s) { WordleLetterState.CORRECT -> 2; WordleLetterState.PRESENT -> 1; else -> 0 } }
        return if (rank(a) >= rank(b)) a else b
    }

    fun submit() {
        if (current.length != WORDLE_WORD_LENGTH || over) return
        val scored = wordleScoreGuess(current, target)
        val updatedKeys = keyStates.toMutableMap()
        current.forEachIndexed { i, ch -> updatedKeys[ch] = bestOf(scored[i], updatedKeys[ch] ?: WordleLetterState.EMPTY) }
        keyStates = updatedKeys
        guesses = guesses + current
        val won = current == target
        val guessCount = guesses.size
        current = ""
        if (won) {
            over = true
            onFinished(GameOutcome(wordleRewardForGuesses(guessCount), "Solved in $guessCount"))
        } else if (guessCount >= WORDLE_MAX_GUESSES) {
            over = true
            onFinished(GameOutcome(1, "Out of guesses — it was $target"))
        }
    }

    // The tiles and the keyboard scale independently: five tiles have room to grow, ten keys in a
    // row do not, so one shared factor would hold the grid back to what the keyboard can manage.
    val tileScale = gameScaleFactor(base = TILE, columns = WORDLE_WORD_LENGTH)
    val tile = TILE * tileScale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Guess the 5-letter word — $WORDLE_MAX_GUESSES tries",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (row in 0 until WORDLE_MAX_GUESSES) {
                Row {
                    for (col in 0 until WORDLE_WORD_LENGTH) {
                        val letter: Char?
                        val state: WordleLetterState
                        when {
                            row < guesses.size -> {
                                letter = guesses[row][col]
                                state = wordleScoreGuess(guesses[row], target)[col]
                            }
                            row == guesses.size && col < current.length -> {
                                letter = current[col]
                                state = WordleLetterState.EMPTY
                            }
                            else -> {
                                letter = null
                                state = WordleLetterState.EMPTY
                            }
                        }
                        val bg = when (state) {
                            WordleLetterState.CORRECT -> colors.success
                            WordleLetterState.PRESENT -> colors.warning
                            WordleLetterState.ABSENT -> colors.surfaceRaised
                            WordleLetterState.EMPTY -> colors.black
                        }
                        Box(
                            modifier = Modifier
                                .size(tile)
                                .padding(2.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = letter?.toString() ?: "",
                                style = DiscnctType.subheading.scaledBy(tileScale),
                                color = if (state == WordleLetterState.EMPTY) colors.textPrimary else colors.black,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        WordleKeyboard(keyStates = keyStates, enabled = !over) { key ->
            when (key) {
                "ENTER" -> submit()
                "DEL" -> if (current.isNotEmpty()) current = current.dropLast(1)
                else -> if (current.length < WORDLE_WORD_LENGTH) current += key
            }
        }
    }
}

@Composable
private fun WordleKeyboard(keyStates: Map<Char, WordleLetterState>, enabled: Boolean, onKey: (String) -> Unit) {
    val colors = LocalDiscnctColors.current
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    val scale = keyboardScale()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                if (rowIndex == 2) {
                    KeyboardKey(label = "DEL", enabled = enabled, background = colors.surfaceRaised, scale = scale, wide = true) { onKey("DEL") }
                }
                row.forEach { ch ->
                    val state = keyStates[ch] ?: WordleLetterState.EMPTY
                    val bg = when (state) {
                        WordleLetterState.CORRECT -> colors.success
                        WordleLetterState.PRESENT -> colors.warning
                        WordleLetterState.ABSENT -> colors.border
                        WordleLetterState.EMPTY -> colors.surfaceRaised
                    }
                    KeyboardKey(label = ch.toString(), enabled = enabled, background = bg, scale = scale) { onKey(ch.toString()) }
                }
                if (rowIndex == 2) {
                    KeyboardKey(label = "GO", enabled = enabled, background = colors.accent, scale = scale, wide = true) { onKey("ENTER") }
                }
            }
            Spacer(modifier = Modifier.height(KEY_GAP))
        }
    }
}

/**
 * The largest the keys can be drawn. Two rows compete for the width — ten letters on the top row,
 * and seven letters flanked by DEL and GO on the bottom — and the gaps between keys don't scale,
 * so both have to be measured rather than assumed. On a phone this lands just above 1.0; the
 * keyboard is the one part of the game that was already as big as it could be.
 */
@Composable
private fun keyboardScale(): Float {
    val usable = LocalConfiguration.current.screenWidthDp.dp - GAME_SIDE_PADDING * 2
    val topRow = (usable - KEY_GAP * 9) / (KEY_WIDTH * 10f)
    val bottomRow = (usable - KEY_GAP * 8) / (KEY_WIDTH * (7f + 2f * WIDE_KEY_RATIO))
    return minOf(GAME_SCALE, topRow, bottomRow)
}

@Composable
private fun KeyboardKey(
    label: String,
    enabled: Boolean,
    background: Color,
    scale: Float,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Box(
        modifier = Modifier
            .size(
                width = (if (wide) KEY_WIDTH * WIDE_KEY_RATIO else KEY_WIDTH) * scale,
                height = KEY_HEIGHT * scale,
            )
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = DiscnctType.caption.scaledBy(scale).copy(color = colors.textDisplay))
    }
}
