package com.discnct.app.ui.blockscreen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.discnct.app.ui.components.SegmentedProgressBar
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.delay

const val HOLD_TO_UNLOCK_MS = 30_000L
private const val PROGRESS_SEGMENTS = 20

/**
 * The friction for Level 1: hold this down for [HOLD_TO_UNLOCK_MS] without letting go to
 * earn access. Deliberately effortful rather than gamified — Level 2 is where a puzzle
 * replaces the wait.
 */
@Composable
fun HoldToUnlockButton(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            isPressed = interaction is PressInteraction.Press
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val startProgress = progress
            var startTimeNanos = -1L
            while (isPressed && progress < 1f) {
                withFrameNanos { now ->
                    if (startTimeNanos < 0) startTimeNanos = now
                    val elapsedMs = (now - startTimeNanos) / 1_000_000
                    progress = (startProgress + elapsedMs.toFloat() / HOLD_TO_UNLOCK_MS).coerceIn(0f, 1f)
                }
            }
            if (progress >= 1f && !completed) {
                completed = true
                onUnlocked()
            }
        } else if (!completed) {
            while (progress > 0f) {
                progress = (progress - 0.04f).coerceAtLeast(0f)
                delay(16)
            }
        }
    }

    Column(modifier = modifier) {
        SegmentedProgressBar(
            totalSegments = PROGRESS_SEGMENTS,
            filledCount = (progress * PROGRESS_SEGMENTS).toInt(),
            fillColor = colors.accent,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(DiscnctShapes.pill)
                .border(1.dp, colors.accent, DiscnctShapes.pill)
                .clickable(interactionSource = interactionSource, indication = null, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (completed) "UNLOCKED" else "HOLD TO UNLOCK · 30S",
                style = DiscnctType.caption.copy(letterSpacing = 0.06f.em, color = colors.accent),
            )
        }
    }
}
