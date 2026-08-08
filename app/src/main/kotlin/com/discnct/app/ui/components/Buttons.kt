package com.discnct.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

enum class ButtonVariant { Primary, Secondary, Ghost, Destructive }

/**
 * The system's one button shape: pill, Space Mono, all-caps, 44dp minimum touch target.
 * Variants differ only in fill/border/text color (components.md #2).
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = LocalDiscnctColors.current
    val shape: RoundedCornerShape = DiscnctShapes.pill

    val background = when {
        !enabled && variant == ButtonVariant.Primary -> colors.borderVisible
        variant == ButtonVariant.Primary -> colors.textDisplay
        else -> null
    }
    val border = when (variant) {
        ButtonVariant.Secondary -> BorderStroke(1.dp, if (enabled) colors.borderVisible else colors.border)
        ButtonVariant.Destructive -> BorderStroke(1.dp, if (enabled) colors.accent else colors.border)
        else -> null
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        variant == ButtonVariant.Primary -> colors.black
        variant == ButtonVariant.Secondary -> colors.textPrimary
        variant == ButtonVariant.Ghost -> colors.textSecondary
        else -> colors.accent // Destructive
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .let { if (background != null) it.background(background, shape) else it }
            .let { if (border != null) it.border(border, shape) else it }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = DiscnctType.caption.copy(
                fontSize = 13.sp,
                letterSpacing = 0.06f.em,
                color = textColor,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
