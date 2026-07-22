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

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
                                .size(40.dp)
                                .padding(2.dp)
                                .border(1.dp, colors.borderVisible)
                                .background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = letter?.toString() ?: "",
                                style = DiscnctType.subheading,
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rowIndex == 2) {
                    KeyboardKey(label = "DEL", enabled = enabled, background = colors.surfaceRaised, wide = true) { onKey("DEL") }
                }
                row.forEach { ch ->
                    val state = keyStates[ch] ?: WordleLetterState.EMPTY
                    val bg = when (state) {
                        WordleLetterState.CORRECT -> colors.success
                        WordleLetterState.PRESENT -> colors.warning
                        WordleLetterState.ABSENT -> colors.border
                        WordleLetterState.EMPTY -> colors.surfaceRaised
                    }
                    KeyboardKey(label = ch.toString(), enabled = enabled, background = bg) { onKey(ch.toString()) }
                }
                if (rowIndex == 2) {
                    KeyboardKey(label = "GO", enabled = enabled, background = colors.accent, wide = true) { onKey("ENTER") }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun KeyboardKey(label: String, enabled: Boolean, background: Color, wide: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Box(
        modifier = Modifier
            .size(width = if (wide) 44.dp else 28.dp, height = 40.dp)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = DiscnctType.caption.copy(color = colors.textDisplay))
    }
}
