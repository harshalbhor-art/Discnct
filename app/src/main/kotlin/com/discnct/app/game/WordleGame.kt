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
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private val WORD_LIST = listOf(
    "BRAVE", "CLOUD", "PLANT", "SNAKE", "TRUST", "FROST", "GLASS", "HOUSE",
    "LEMON", "MUSIC", "OCEAN", "PRIDE", "QUIET", "RIVER", "STORM", "TIGER",
    "UNITY", "VIVID", "WATER", "YOUTH",
)
private const val MAX_GUESSES = 6
private const val WORD_LENGTH = 5

private enum class LetterState { CORRECT, PRESENT, ABSENT, EMPTY }

private fun scoreGuess(guess: String, target: String): List<LetterState> {
    val result = MutableList(WORD_LENGTH) { LetterState.ABSENT }
    val remaining = target.toMutableList()
    for (i in 0 until WORD_LENGTH) {
        if (guess[i] == target[i]) {
            result[i] = LetterState.CORRECT
            remaining[i] = '_'
        }
    }
    for (i in 0 until WORD_LENGTH) {
        if (result[i] == LetterState.CORRECT) continue
        val idx = remaining.indexOf(guess[i])
        if (idx >= 0) {
            result[i] = LetterState.PRESENT
            remaining[idx] = '_'
        }
    }
    return result
}

private fun rewardForGuesses(count: Int): Int = when (count) {
    1, 2 -> 7
    3 -> 6
    4 -> 5
    5 -> 4
    6 -> 3
    else -> 1
}

@Composable
fun WordleGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val target = remember { WORD_LIST.random() }
    var guesses by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableStateOf("") }
    var over by remember { mutableStateOf(false) }
    var keyStates by remember { mutableStateOf(mapOf<Char, LetterState>()) }

    fun bestOf(a: LetterState, b: LetterState): LetterState {
        val rank = { s: LetterState -> when (s) { LetterState.CORRECT -> 2; LetterState.PRESENT -> 1; else -> 0 } }
        return if (rank(a) >= rank(b)) a else b
    }

    fun submit() {
        if (current.length != WORD_LENGTH || over) return
        val scored = scoreGuess(current, target)
        val updatedKeys = keyStates.toMutableMap()
        current.forEachIndexed { i, ch -> updatedKeys[ch] = bestOf(scored[i], updatedKeys[ch] ?: LetterState.EMPTY) }
        keyStates = updatedKeys
        guesses = guesses + current
        val won = current == target
        val guessCount = guesses.size
        current = ""
        if (won) {
            over = true
            onFinished(GameOutcome(rewardForGuesses(guessCount), "Solved in $guessCount"))
        } else if (guessCount >= MAX_GUESSES) {
            over = true
            onFinished(GameOutcome(1, "Out of guesses — it was $target"))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Guess the 5-letter word — $MAX_GUESSES tries",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column {
            for (row in 0 until MAX_GUESSES) {
                Row {
                    for (col in 0 until WORD_LENGTH) {
                        val letter: Char?
                        val state: LetterState
                        when {
                            row < guesses.size -> {
                                letter = guesses[row][col]
                                state = scoreGuess(guesses[row], target)[col]
                            }
                            row == guesses.size && col < current.length -> {
                                letter = current[col]
                                state = LetterState.EMPTY
                            }
                            else -> {
                                letter = null
                                state = LetterState.EMPTY
                            }
                        }
                        val bg = when (state) {
                            LetterState.CORRECT -> colors.success
                            LetterState.PRESENT -> colors.warning
                            LetterState.ABSENT -> colors.surfaceRaised
                            LetterState.EMPTY -> colors.black
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
                                color = if (state == LetterState.EMPTY) colors.textPrimary else colors.black,
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
                else -> if (current.length < WORD_LENGTH) current += key
            }
        }
    }
}

@Composable
private fun WordleKeyboard(keyStates: Map<Char, LetterState>, enabled: Boolean, onKey: (String) -> Unit) {
    val colors = LocalDiscnctColors.current
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rowIndex == 2) {
                    KeyboardKey(label = "DEL", enabled = enabled, background = colors.surfaceRaised, wide = true) { onKey("DEL") }
                }
                row.forEach { ch ->
                    val state = keyStates[ch] ?: LetterState.EMPTY
                    val bg = when (state) {
                        LetterState.CORRECT -> colors.success
                        LetterState.PRESENT -> colors.warning
                        LetterState.ABSENT -> colors.border
                        LetterState.EMPTY -> colors.surfaceRaised
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
