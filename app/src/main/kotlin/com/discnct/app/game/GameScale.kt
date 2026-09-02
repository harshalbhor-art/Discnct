package com.discnct.app.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much bigger the games are drawn than they were originally laid out. The boards were sized
 * for a settings-screen preview and read as postage stamps once they're the only thing on a
 * full-screen block; this is the multiplier that fixes that.
 */
const val GAME_SCALE = 1.7f

/**
 * Side padding a game board gets. Deliberately tighter than the 20dp the rest of the app uses —
 * every dp here is a dp the board can't have, and the board is the whole screen.
 */
val GAME_SIDE_PADDING = 8.dp

/**
 * [GAME_SCALE], reduced just enough that a [columns]-wide grid of [base] cells still fits the
 * screen. An 8-file chessboard physically cannot grow 1.7x on a phone, so asking for it and
 * clamping beats hard-coding a size that overflows on small displays and looks lost on large ones.
 *
 * Pass `columns = 1` for a game that's one widget rather than a grid — a circle, a spinner.
 *
 * @param gap horizontal space between cells, if the grid has any.
 */
@Composable
fun gameScaleFactor(base: Dp, columns: Int, gap: Dp = 0.dp): Float {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val usable = screenWidth - GAME_SIDE_PADDING * 2 - gap * (columns - 1)
    return minOf(GAME_SCALE, (usable / columns) / base)
}

/** Grow a type style by the same factor as the board it labels, so text doesn't shrink relatively. */
fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
