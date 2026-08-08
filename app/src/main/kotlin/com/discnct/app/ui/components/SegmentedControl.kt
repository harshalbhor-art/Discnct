package com.discnct.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Max 2-4 segments (components.md #8). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDiscnctColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.pill)
            .border(BorderStroke(1.dp, colors.borderVisible), DiscnctShapes.pill)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Text(
                text = option.uppercase(),
                style = DiscnctType.label.copy(
                    color = if (active) colors.black else colors.textSecondary,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(DiscnctShapes.pill)
                    .let { if (active) it.background(colors.textDisplay, DiscnctShapes.pill) else it }
                    .clickable { onSelect(index) }
                    .height(36.dp)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
