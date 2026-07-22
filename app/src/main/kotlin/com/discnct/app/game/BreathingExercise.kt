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

private enum class Phase(val label: String, val targetScale: Float) {
    INHALE("Breathe in", 1f),
    HOLD_IN("Hold", 1f),
    EXHALE("Breathe out", 0.45f),
    HOLD_OUT("Hold", 0.45f),
}

/**
 * Level 1's calm alternative to a puzzle: no score, just box breathing. Reward scales
 * with cycles actually completed — leaving early via "Claim Now" still pays out, just less.
 */
@Composable
fun BreathingExercise(onFinished: (GameOutcome) -> Unit) {
    val colors = LocalDiscnctColors.current
    val scale = remember { Animatable(0.45f) }
    var phaseLabel by remember { mutableStateOf("Get ready") }
    var cyclesCompleted by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (cyclesCompleted < TOTAL_CYCLES && !finished) {
            for (phase in Phase.entries) {
                phaseLabel = phase.label
                scale.animateTo(phase.targetScale, animationSpec = tween(PHASE_MS, easing = LinearEasing))
            }
            cyclesCompleted += 1
        }
        if (!finished) {
            finished = true
            onFinished(GameOutcome(TOTAL_CYCLES + 1, "Completed all $TOTAL_CYCLES cycles"))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Cycle ${cyclesCompleted.coerceAtMost(TOTAL_CYCLES)}/$TOTAL_CYCLES",
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(180.dp * scale.value)
                    .clip(CircleShape)
                    .background(colors.accentSubtle),
            )
            Text(phaseLabel, style = DiscnctType.subheading, color = colors.textDisplay)
        }

        Spacer(modifier = Modifier.height(24.dp))
        SegmentedProgressBar(totalSegments = TOTAL_CYCLES, filledCount = cyclesCompleted, fillColor = colors.accent)

        Spacer(modifier = Modifier.height(24.dp))
        PillButton(
            label = "Claim Now",
            onClick = {
                if (!finished) {
                    finished = true
                    onFinished(GameOutcome(cyclesCompleted.coerceAtLeast(1), "Left early after $cyclesCompleted cycle(s)"))
                }
            },
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
