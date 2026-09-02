package com.discnct.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Pill-bordered tag, no fill (components.md #7). */
@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val colors = LocalDiscnctColors.current
    val borderColor = if (active) colors.textDisplay else colors.borderVisible
    val textColor = if (active) colors.textDisplay else colors.textSecondary

    Text(
        text = label.uppercase(),
        style = DiscnctType.caption.copy(color = textColor),
        modifier = modifier
            .clip(DiscnctShapes.pill)
            .border(BorderStroke(1.dp, borderColor), DiscnctShapes.pill)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
