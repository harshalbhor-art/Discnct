package com.discnct.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Suggested amounts, in rupees. Pay-what-you-like, so these are prompts rather than prices. */
private val PRESET_AMOUNTS = listOf(49, 99, 199, 499)

/**
 * "Buy us a Coffee" — the pay-what-you-like block under the three levels.
 *
 * **Front-end only.** There is no payment provider, no order id, and no network call behind any of
 * this; the button is deliberately inert and says so. It's here to settle what the flow should
 * look like before anything real is wired to it, so nothing in this file should be read as
 * evidence that money can actually change hands yet.
 */
@Composable
fun SupportCard(modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    var selected by remember { mutableStateOf(PRESET_AMOUNTS[1]) }
    var custom by remember { mutableStateOf("") }

    // A typed amount always wins over a tapped chip — the keyboard was the later intent.
    val customAmount = custom.trim().toIntOrNull()?.takeIf { it > 0 }
    val amount = customAmount ?: selected

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, DiscnctShapes.card)
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Buy us a Coffee", style = DiscnctType.subheading, color = colors.textDisplay)
            Chip(label = "WIP")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Discnct is free, and it stays free. If it handed you back an evening, " +
                "pay whatever that evening was worth to you.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_AMOUNTS.forEach { preset ->
                Chip(
                    label = "₹$preset",
                    active = customAmount == null && preset == selected,
                    modifier = Modifier.clickable {
                        selected = preset
                        custom = ""
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        AmountField(value = custom, onValueChange = { entry ->
            // Digits only: the field feeds an amount, and filtering here beats validating later.
            custom = entry.filter { it.isDigit() }.take(6)
        })

        Spacer(modifier = Modifier.height(14.dp))
        PillButton(
            label = "Pay ₹$amount",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Payments aren't connected yet — this is the screen, not the checkout.",
            style = DiscnctType.label,
            color = colors.textDisabled,
        )

        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            label = "Share Discnct instead",
            onClick = {},
            enabled = false,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalDiscnctColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = DiscnctType.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.pill)
            .background(colors.black)
            .border(1.dp, colors.borderVisible, DiscnctShapes.pill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        // Placeholder and field share one Box so the decoration layout gets a single child.
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "Or enter your own amount",
                        style = DiscnctType.body,
                        color = colors.textDisabled,
                    )
                }
                innerTextField()
            }
        },
    )
}
