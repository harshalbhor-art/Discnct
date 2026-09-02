package com.discnct.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.discnct.app.support.coffeeCheckoutUri
import com.discnct.app.support.upiPaymentUri
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.LocalSnackbarHostState
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

/** Suggested amounts, in rupees. Pay-what-you-like, so these are prompts rather than prices. */
private val PRESET_AMOUNTS = listOf(49, 99, 199, 499)

/**
 * "Buy us a Coffee" — the pay-what-you-like block under the three levels.
 *
 * Tapping "Pay" hands the chosen amount to whichever UPI app the user picks from the system
 * chooser — the default, since it settles instantly with no cut taken. "Pay by card" is the
 * fallback for anyone without a UPI app: it opens the Razorpay Checkout page in a browser instead.
 * Discnct's involvement ends there either way, deliberately:
 *
 *  * **Nothing is verified.** There is no backend to verify against. The "Thank you" appears
 *    because the user came back, not because money moved — a cancelled payment gets the same
 *    message, and that is the honest limit of what an app with no server can know.
 *  * **Nothing is unlocked.** No entitlement, no receipt, no record that a payment was attempted.
 *    Paying changes nothing about how Discnct behaves, which is the point of it being a donation.
 */
@Composable
fun SupportCard(modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = LocalSnackbarHostState.current
    var selected by remember { mutableStateOf(PRESET_AMOUNTS[1]) }
    var custom by remember { mutableStateOf("") }

    // Launched for a result purely to learn when the user comes back. The result code is ignored:
    // UPI apps report cancelled for a completed payment about as often as not, so reading it would
    // mean showing "cancelled" to people who actually paid.
    val payment = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { snackbars.showSnackbar("Thank you") }
    }

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
            onClick = {
                val pay = Intent(Intent.ACTION_VIEW, Uri.parse(upiPaymentUri(amount)))
                // Resolve against the bare intent, not the chooser — a chooser always resolves, so
                // asking it whether anything can handle UPI would always answer yes.
                if (context.packageManager.resolveActivity(pay, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                    scope.launch { snackbars.showSnackbar("No UPI app found") }
                } else {
                    try {
                        payment.launch(Intent.createChooser(pay, "Pay ₹$amount"))
                    } catch (_: ActivityNotFoundException) {
                        // An app can be uninstalled between the check above and the launch below.
                        scope.launch { snackbars.showSnackbar("No UPI app found") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Opens your UPI app. No fees, nothing verified, nothing unlocked.",
            style = DiscnctType.label,
            color = colors.textDisabled,
        )

        Spacer(modifier = Modifier.height(10.dp))
        PillButton(
            label = "Pay by card",
            onClick = {
                val checkout = Intent(Intent.ACTION_VIEW, Uri.parse(coffeeCheckoutUri(amount)))
                try {
                    context.startActivity(checkout)
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbars.showSnackbar("No browser found") }
                }
            },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Opens Razorpay Checkout in your browser — card details never touch this app.",
            style = DiscnctType.label,
            color = colors.textDisabled,
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
