package com.discnct.app.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Shown instead of Level 2 or Level 3 once their trial has run out. Two ways through: buy the
 * one-time unlock (Play Billing — the only rail Play policy allows for a paid feature inside a
 * Play-distributed app), or a tester promo code, which is a soft bypass rather than a real
 * discount and is expected to be handed out by hand, not published anywhere.
 */
@Composable
fun PaywallScreen(
    levelName: String,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = viewModel(),
) {
    val colors = LocalDiscnctColors.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var promoCode by remember { mutableStateOf("") }

    // Re-checks Play's purchase record on resume — the purchase sheet Play opens can complete
    // after the user returns to the app, not while this composable is still on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBilling()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black),
    ) {
        SectionTopBar(title = "Unlock $levelName", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Your free trial of $levelName is over. Unlock it once, forever — " +
                    "no subscription, nothing recurring.",
                style = DiscnctType.body,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.billingUnavailable) {
                Text(
                    text = "Google Play purchases aren't available on this device or account right now.",
                    style = DiscnctType.body,
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PillButton(
                    label = "Retry",
                    onClick = { viewModel.retryBilling() },
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PillButton(
                    label = uiState.priceLabel?.let { "Buy — $it" } ?: "Buy to unlock",
                    onClick = {
                        context.findActivity()?.let { activity -> viewModel.buy(activity) }
                    },
                    enabled = uiState.priceLabel != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Opens Google Play's own purchase screen.",
                    style = DiscnctType.label,
                    color = colors.textDisabled,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(text = "GOT A CODE?", style = DiscnctType.label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            PromoCodeField(
                value = promoCode,
                onValueChange = {
                    promoCode = it
                    viewModel.clearPromoError()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            PillButton(
                label = "Unlock with code",
                onClick = { viewModel.submitPromoCode(promoCode) },
                variant = ButtonVariant.Secondary,
                enabled = promoCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.promoError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "That code isn't right.", style = DiscnctType.label, color = colors.accent)
            }
        }
    }
}

@Composable
private fun PromoCodeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        textStyle = DiscnctType.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .clip(DiscnctShapes.pill)
            .background(colors.black)
            .border(1.dp, colors.borderVisible, DiscnctShapes.pill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(text = "Promo code", style = DiscnctType.body, color = colors.textDisabled)
            }
            innerTextField()
        },
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
