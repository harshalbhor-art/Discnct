package com.discnct.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * The system's signature data visualization: discrete square-ended blocks with a
 * 2dp gap, no border radius (components.md #11). [filledCount] segments (from the
 * left) render in [fillColor]; the remainder render in the neutral border color.
 */
@Composable
fun SegmentedProgressBar(
    totalSegments: Int,
    filledCount: Int,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
    fillColor: Color = LocalDiscnctColors.current.textDisplay,
) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(totalSegments) { index ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .background(if (index < filledCount) fillColor else colors.border),
            )
        }
    }
}
