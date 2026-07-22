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
import com.discnct.app.game.logic.Board2048
import com.discnct.app.game.logic.canMove2048
import com.discnct.app.game.logic.initialBoard2048
import com.discnct.app.game.logic.maxTile2048
import com.discnct.app.game.logic.moveDown2048
import com.discnct.app.game.logic.moveLeft2048
import com.discnct.app.game.logic.moveRight2048
import com.discnct.app.game.logic.moveUp2048
import com.discnct.app.game.logic.rewardForMaxTile2048
import com.discnct.app.game.logic.spawnRandomTile2048
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

@Composable
fun Game2048(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var board by remember { mutableStateOf(initialBoard2048()) }
    var over by remember { mutableStateOf(false) }

    fun applyMove(move: (Board2048) -> Pair<Board2048, Boolean>) {
        if (over) return
        val (moved, changed) = move(board)
        if (!changed) return
        val next = spawnRandomTile2048(moved)
        board = next
        if (!canMove2048(next)) {
            over = true
            onFinished(GameOutcome(rewardForMaxTile2048(maxTile2048(next)), "Reached ${maxTile2048(next)}"))
        }
    }

    fun claim() {
        if (over) return
        over = true
        onFinished(GameOutcome(rewardForMaxTile2048(maxTile2048(board)), "Reached ${maxTile2048(board)}"))
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Best tile: ${maxTile2048(board)} — merge equal tiles",
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
            onUp = { applyMove(::moveUp2048) },
            onDown = { applyMove(::moveDown2048) },
            onLeft = { applyMove(::moveLeft2048) },
            onRight = { applyMove(::moveRight2048) },
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
