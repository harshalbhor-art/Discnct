package com.discnct.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

/**
 * One dialog, two jobs, picked by [mode]:
 *  - [PinPromptMode.Create] asks for a new PIN twice and hands back the confirmed value.
 *  - [PinPromptMode.Verify] asks for the existing PIN and only calls back once it matches.
 * Used anywhere Strict Mode needs to gate an action — setting the PIN, and later checking it.
 */
sealed interface PinPromptMode {
    data object Create : PinPromptMode
    data class Verify(val checkPin: suspend (String) -> Boolean) : PinPromptMode
}

@Composable
fun PinPromptDialog(
    mode: PinPromptMode,
    title: String,
    onConfirmed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDiscnctColors.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.card)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.borderVisible, DiscnctShapes.card)
                .padding(20.dp),
        ) {
            Text(text = title, style = DiscnctType.subheading, color = colors.textDisplay)
            Spacer(modifier = Modifier.height(14.dp))

            PinField(label = "PIN", value = pin, onValueChange = { pin = it; error = null })

            if (mode is PinPromptMode.Create) {
                Spacer(modifier = Modifier.height(10.dp))
                PinField(
                    label = "Confirm PIN",
                    value = confirmPin,
                    onValueChange = { confirmPin = it; error = null },
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = error!!, style = DiscnctType.bodySmall, color = colors.accent)
            }

            Spacer(modifier = Modifier.height(18.dp))

            PillButton(
                label = if (checking) "Checking…" else "Confirm",
                enabled = !checking,
                onClick = {
                    when (mode) {
                        is PinPromptMode.Create -> {
                            when {
                                pin.length != PIN_LENGTH -> error = "PIN must be $PIN_LENGTH digits"
                                pin != confirmPin -> error = "PINs don't match"
                                else -> onConfirmed(pin)
                            }
                        }
                        is PinPromptMode.Verify -> {
                            if (pin.length != PIN_LENGTH) {
                                error = "PIN must be $PIN_LENGTH digits"
                            } else {
                                checking = true
                                scope.launch {
                                    if (mode.checkPin(pin)) {
                                        onConfirmed(pin)
                                    } else {
                                        checking = false
                                        error = "Wrong PIN"
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            PillButton(
                label = "Cancel",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = LocalDiscnctColors.current
    Column {
        Text(text = label, style = DiscnctType.label, color = colors.textSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = { new -> if (new.length <= PIN_LENGTH && new.all(Char::isDigit)) onValueChange(new) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(mask = '•'),
            textStyle = DiscnctType.heading.copy(color = colors.textDisplay),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.cardCompact)
                .background(colors.surface)
                .border(1.dp, colors.border, DiscnctShapes.cardCompact)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
