package com.discnct.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Pill track, circle thumb (components.md #10). 44dp touch target. */
@Composable
fun DiscnctToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDiscnctColors.current
    val trackColor by animateColorAsState(if (checked) colors.textDisplay else colors.borderVisible, label = "track")
    val thumbColor by animateColorAsState(if (checked) colors.black else colors.textDisabled, label = "thumb")

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 44.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(DiscnctShapes.pill)
                .background(trackColor)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(DiscnctShapes.pill)
                    .background(thumbColor),
            )
        }
    }
}
