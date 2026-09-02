package com.discnct.app.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.logic.DINO_HEIGHT
import com.discnct.app.game.logic.DINO_WIDTH
import com.discnct.app.game.logic.DINO_X
import com.discnct.app.game.logic.DinoState
import com.discnct.app.game.logic.WORLD_HEIGHT
import com.discnct.app.game.logic.WORLD_WIDTH
import com.discnct.app.game.logic.dinoJump
import com.discnct.app.game.logic.dinoReward
import com.discnct.app.game.logic.dinoScore
import com.discnct.app.game.logic.stepDino
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Seconds a run can last before the clock ends it, however well it's going. */
private val RUN_SECONDS = GameType.DINO_RUN.timeLimitSeconds ?: 60

/**
 * The endless runner: tap to jump, don't hit anything.
 *
 * All the physics lives in `DinoRunLogic` in world units, and this only turns those into pixels and
 * draws them. That split is what lets a jump that clears a cactus be a tested fact rather than
 * something that happens to look right on the phone it was written on.
 *
 * The whole play area is the button. A runner needs the jump to land the instant you decide to
 * jump, and hunting for a control at the bottom of the screen costs more than the reaction time
 * the game is asking for.
 *
 * The art is drawn here from rectangles — a body, a head, a leg, a scatter of cacti. Nothing is
 * lifted from Chrome's runner; this is the same idea, not the same asset.
 */
@Composable
fun DinoRunGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var state by remember { mutableStateOf(DinoState()) }
    var secondsLeft by remember { mutableStateOf(RUN_SECONDS) }
    var over by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap anywhere to jump.") }

    fun finish(label: String) {
        if (over) return
        over = true
        val score = dinoScore(state.distance)
        status = "$label — ${score}m"
        onFinished(GameOutcome(dinoReward(score), status))
    }

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        var elapsed = 0.0
        while (!over) {
            val now = withFrameNanos { it }
            // A dropped frame or a backgrounded app must not teleport the world past the runner,
            // so one step can never represent more than a few frames of movement.
            val dt = ((now - lastFrameNanos) / 1_000_000_000.0).coerceAtMost(0.05)
            lastFrameNanos = now

            state = stepDino(state, dt)
            elapsed += dt

            val remaining = (RUN_SECONDS - elapsed).toInt().coerceAtLeast(0)
            if (remaining != secondsLeft) secondsLeft = remaining

            if (state.crashed) finish("Crashed")
            if (elapsed >= RUN_SECONDS) finish("Time's up")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (over) status else "${secondsLeft}s left — ${dinoScore(state.distance)}m",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((WORLD_WIDTH / WORLD_HEIGHT).toFloat())
                .border(1.dp, colors.borderVisible)
                .background(colors.surface)
                .clickable(enabled = !over) { state = dinoJump(state) },
        ) {
            val unit = size.width / WORLD_WIDTH.toFloat()
            val ground = size.height

            fun box(x: Double, yBottom: Double, width: Double, height: Double, color: Color) {
                drawRect(
                    color = color,
                    // World y counts up from the ground; the canvas counts down from the top.
                    topLeft = Offset(x.toFloat() * unit, ground - (yBottom + height).toFloat() * unit),
                    size = Size(width.toFloat() * unit, height.toFloat() * unit),
                )
            }

            drawRect(
                color = colors.borderVisible,
                topLeft = Offset(0f, ground - unit * 0.6f),
                size = Size(size.width, unit * 0.6f),
            )

            for (obstacle in state.obstacles) {
                box(obstacle.x, 0.0, obstacle.width, obstacle.height, colors.accent)
                // Two arms, so a cactus reads as a cactus rather than a post.
                val armY = obstacle.height * 0.45
                box(obstacle.x - obstacle.width * 0.5, armY, obstacle.width * 0.5, obstacle.width * 0.4, colors.accent)
                box(obstacle.x + obstacle.width, armY, obstacle.width * 0.5, obstacle.width * 0.4, colors.accent)
            }

            val runner = if (over) colors.textDisabled else colors.textDisplay
            box(DINO_X, state.y, DINO_WIDTH, DINO_HEIGHT * 0.62, runner)
            box(DINO_X + DINO_WIDTH * 0.45, state.y + DINO_HEIGHT * 0.62, DINO_WIDTH * 0.55, DINO_HEIGHT * 0.38, runner)
            // Tail, behind the body rather than on top of it.
            box(DINO_X - DINO_WIDTH * 0.3, state.y + DINO_HEIGHT * 0.2, DINO_WIDTH * 0.3, DINO_HEIGHT * 0.18, runner)
        }

        Spacer(modifier = Modifier.height(16.dp))
        PillButton(
            label = "Give Up",
            onClick = { finish("Stopped") },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
