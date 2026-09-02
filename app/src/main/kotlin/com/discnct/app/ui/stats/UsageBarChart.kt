package com.discnct.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.discnct.app.stats.UsageBucket
import com.discnct.app.stats.barFractions
import com.discnct.app.stats.formatDurationMillis
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Screen time per bucket as square-ended columns — the same discrete-block language as
 * [com.discnct.app.ui.components.SegmentedProgressBar], turned on its side.
 *
 * Bars are scaled against the tallest bar in the window rather than an absolute ceiling, so the
 * shape of a light week is as readable as a heavy one. The final bucket is the one in progress
 * (today, or this week) and is drawn in the accent colour to say so.
 */
@Composable
fun UsageBarChart(
    buckets: List<UsageBucket>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 132.dp,
) {
    val colors = LocalDiscnctColors.current
    if (buckets.isEmpty()) return

    val fractions = barFractions(buckets.map { it.millis })
    val peak = buckets.maxOf { it.millis }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEachIndexed { index, bucket ->
                val isCurrent = index == buckets.lastIndex
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (bucket.millis > 0L) formatDurationMillis(bucket.millis) else "",
                        style = DiscnctType.label,
                        color = if (isCurrent) colors.accent else colors.textDisabled,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // A day with any usage at all keeps a visible stub, so "barely used" and
                    // "not used" stay tellable apart at a glance.
                    val fraction = if (bucket.millis > 0L) fractions[index].coerceAtLeast(0.04f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .background(if (isCurrent) colors.accent else colors.textDisplay),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            buckets.forEachIndexed { index, bucket ->
                Text(
                    text = bucket.label,
                    style = DiscnctType.label,
                    color = if (index == buckets.lastIndex) colors.accent else colors.textDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (peak == 0L) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No screen time recorded for this window yet.",
                style = DiscnctType.bodySmall,
                color = colors.textDisabled,
            )
        }
    }
}
