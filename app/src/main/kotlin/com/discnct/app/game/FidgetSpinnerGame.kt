package com.discnct.app.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.logic.SpinnerState
import com.discnct.app.game.logic.applyFling
import com.discnct.app.game.logic.revolutionsFrom
import com.discnct.app.game.logic.spinnerReward
import com.discnct.app.game.logic.stepSpin
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.components.SegmentedProgressBar
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How much angular speed one degree of drag imparts. Tuned so a brisk flick is ~2 revolutions. */
private const val DRAG_GAIN = 14.0

/** Drags this close to the hub are all noise — the angle they imply swings wildly. */
private const val MIN_DRAG_RADIUS_PX = 24f

private val SPINNER_SECONDS = GameType.FIDGET_SPINNER.timeLimitSeconds ?: 45

private const val PROGRESS_SEGMENTS = 15

/** Unscaled diameter of the spinner. */
private val SPINNER = 220.dp

/**
 * The other idle game, next to Breathing: nothing to solve, just spin. With no win condition to
 * end on, a clock ends it instead, and like Breathing it pays less than a puzzle — the point is to
 * give restless hands somewhere to go for a minute, not an efficient way to buy time back.
 */
@Composable
fun FidgetSpinnerGame(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    var spinner by remember { mutableStateOf(SpinnerState()) }
    var secondsLeft by remember { mutableStateOf(SPINNER_SECONDS) }
    var finished by remember { mutableStateOf(false) }

    fun finish(label: String) {
        if (finished) return
        finished = true
        val revolutions = revolutionsFrom(spinner.totalDegrees)
        onFinished(GameOutcome(spinnerReward(revolutions), "$label — $revolutions spins"))
    }

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        var elapsed = 0.0
        while (!finished) {
            val now = withFrameNanos { it }
            val dt = (now - lastFrameNanos) / 1_000_000_000.0
            lastFrameNanos = now

            spinner = stepSpin(spinner, dt)
            elapsed += dt

            // Only touch the state when the displayed second actually changes, so the countdown
            // isn't forcing a recomposition on every single frame the spinner is already causing.
            val remaining = (SPINNER_SECONDS - elapsed).toInt().coerceAtLeast(0)
            if (remaining != secondsLeft) secondsLeft = remaining
            if (elapsed >= SPINNER_SECONDS) finish("Time's up")
        }
    }

    val scale = gameScaleFactor(base = SPINNER, columns = 1)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${secondsLeft}s left — ${revolutionsFrom(spinner.totalDegrees)} spins",
            style = DiscnctType.body.scaledBy(scale),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Canvas(
            modifier = Modifier
                .size(SPINNER * scale)
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    detectDragGestures { change, dragAmount ->
                        val to = change.position - center
                        val from = to - dragAmount
                        if (hypot(from.x, from.y) >= MIN_DRAG_RADIUS_PX &&
                            hypot(to.x, to.y) >= MIN_DRAG_RADIUS_PX
                        ) {
                            spinner = applyFling(spinner, sweptDegrees(from, to) * DRAG_GAIN)
                        }
                        change.consume()
                    }
                },
        ) {
            val radius = size.minDimension / 2f
            rotate(degrees = spinner.angleDeg.toFloat()) {
                // Three lobes at 120°, the classic silhouette. One is tinted differently: a
                // three-fold symmetric shape looks identical every third of a turn, so without a
                // marked lobe you can't actually tell how far it has gone round.
                repeat(3) { lobe ->
                    val theta = lobe * 2.0 * PI / 3.0
                    drawCircle(
                        color = if (lobe == 0) colors.textDisplay else colors.accent,
                        radius = radius * 0.30f,
                        center = center + Offset(
                            x = (cos(theta) * radius * 0.62f).toFloat(),
                            y = (sin(theta) * radius * 0.62f).toFloat(),
                        ),
                    )
                }
                drawCircle(color = colors.surfaceRaised, radius = radius * 0.26f, center = center)
                drawCircle(color = colors.textDisplay, radius = radius * 0.08f, center = center)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SegmentedProgressBar(
            totalSegments = PROGRESS_SEGMENTS,
            // One segment per second would be 45 slivers wide — coarsen it to a readable bar.
            filledCount = (SPINNER_SECONDS - secondsLeft) * PROGRESS_SEGMENTS / SPINNER_SECONDS,
            fillColor = colors.accent,
        )

        Spacer(modifier = Modifier.height(24.dp))
        PillButton(
            label = "Claim Now",
            onClick = { finish("Stopped early") },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Signed angle swept from [from] to [to] about the hub, normalised into (-180°, 180°]. */
private fun sweptDegrees(from: Offset, to: Offset): Double {
    val delta = (atan2(to.y, to.x) - atan2(from.y, from.x)) * 180.0 / PI
    return when {
        delta > 180.0 -> delta - 360.0
        delta <= -180.0 -> delta + 360.0
        else -> delta
    }
}
