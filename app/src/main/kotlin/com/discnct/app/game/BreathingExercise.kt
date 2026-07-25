package com.discnct.app.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.components.SegmentedProgressBar
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private const val TOTAL_CYCLES = 4
private const val PHASE_MS = 4000

/** Unscaled diameter of the breathing circle at full inhale. */
private val CIRCLE = 180.dp

/**
 * The clock the session is bounded by. Four cycles of four 4s phases is 64s, so in normal use the
 * cycles finish first and this never fires — it's the backstop for a phase animation that stalls
 * (a backgrounded activity, a frozen frame clock) so the game can't sit open indefinitely.
 */
private val BREATHING_SECONDS = GameType.BREATHING.timeLimitSeconds ?: 64

private enum class Phase(val label: String, val targetScale: Float) {
    INHALE("Breathe in", 1f),
    HOLD_IN("Hold", 1f),
    EXHALE("Breathe out", 0.45f),
    HOLD_OUT("Hold", 0.45f),
}

/**
 * The calm alternative to a puzzle: no score, just box breathing. Reward scales with cycles
 * actually completed — leaving early via "Claim Now" still pays out, just less.
 */
@Composable
fun BreathingExercise(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val breath = remember { Animatable(0.45f) }
    var phaseLabel by remember { mutableStateOf("Get ready") }
    var cyclesCompleted by remember { mutableStateOf(0) }
    var secondsLeft by remember { mutableStateOf(BREATHING_SECONDS) }
    var finished by remember { mutableStateOf(false) }

    fun finish(minutes: Int, label: String) {
        if (finished) return
        finished = true
        onFinished(GameOutcome(minutes, label))
    }

    LaunchedEffect(Unit) {
        while (cyclesCompleted < TOTAL_CYCLES && !finished) {
            for (phase in Phase.entries) {
                phaseLabel = phase.label
                breath.animateTo(phase.targetScale, animationSpec = tween(PHASE_MS, easing = LinearEasing))
            }
            cyclesCompleted += 1
        }
        finish(TOTAL_CYCLES + 1, "Completed all $TOTAL_CYCLES cycles")
    }

    LaunchedEffect(Unit) {
        val startedAt = withFrameNanos { it }
        while (!finished) {
            val elapsedSeconds = ((withFrameNanos { it } - startedAt) / 1_000_000_000L).toInt()
            val remaining = (BREATHING_SECONDS - elapsedSeconds).coerceAtLeast(0)
            if (remaining != secondsLeft) secondsLeft = remaining
            if (remaining == 0) finish(cyclesCompleted.coerceAtLeast(1), "Session ended after $cyclesCompleted cycle(s)")
        }
    }

    val scale = gameScaleFactor(base = CIRCLE, columns = 1)
    val circle = CIRCLE * scale

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAME_SIDE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Cycle ${cyclesCompleted.coerceAtMost(TOTAL_CYCLES)}/$TOTAL_CYCLES — ${secondsLeft}s left",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.size(circle), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(circle * breath.value)
                    .clip(CircleShape)
                    .background(colors.accentSubtle),
            )
            Text(phaseLabel, style = DiscnctType.subheading.scaledBy(scale), color = colors.textDisplay)
        }

        Spacer(modifier = Modifier.height(24.dp))
        SegmentedProgressBar(totalSegments = TOTAL_CYCLES, filledCount = cyclesCompleted, fillColor = colors.accent)

        Spacer(modifier = Modifier.height(24.dp))
        PillButton(
            label = "Claim Now",
            onClick = { finish(cyclesCompleted.coerceAtLeast(1), "Left early after $cyclesCompleted cycle(s)") },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
