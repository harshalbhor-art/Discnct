package com.discnct.app.ui.blockscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Shared full-screen frame every "you can't have this right now" moment renders
 * inside of. Level 1 (Phase 1) fills [content] with a countdown + disable button;
 * Level 2 (Phase 2) will fill it with the wait-or-play choice and puzzle instead —
 * the frame itself (status bar, app identity, BLOCKED heading) never changes.
 */
@Composable
fun BlockScreen(
    appName: String,
    modifier: Modifier = Modifier,
    statusLabel: String = "Blocked",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalDiscnctColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("9:41", style = DiscnctType.caption, color = colors.textSecondary)
            SignalDots()
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            LockGlyph()
        }

        Text(
            text = appName,
            style = DiscnctType.label,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text(
            text = statusLabel.uppercase(),
            style = DiscnctType.heading.copy(fontWeight = FontWeight.Bold),
            color = colors.textDisplay,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp),
        )

        content()
    }
}

@Composable
private fun SignalDots() {
    val colors = LocalDiscnctColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.textSecondary),
            )
        }
    }
}

@Composable
private fun LockGlyph() {
    val colors = LocalDiscnctColors.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(1.5.dp, colors.accent, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = Stroke(width = 1.6.dp.toPx())
            val bodyTop = size.height * 0.42f
            drawRoundRect(
                color = colors.accent,
                topLeft = androidx.compose.ui.geometry.Offset(0f, bodyTop),
                size = Size(size.width, size.height - bodyTop),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = stroke,
            )
            val shackleRadius = size.width * 0.28f
            drawArc(
                color = colors.accent,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - shackleRadius, bodyTop - shackleRadius * 2),
                size = Size(shackleRadius * 2, shackleRadius * 2),
                style = stroke,
            )
        }
    }
}
