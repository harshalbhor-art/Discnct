package com.discnct.app.ui.onboarding

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private data class Step(
    val title: String,
    val body: String,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
)

private val steps = listOf(
    Step(
        title = "Accessibility Service",
        body = "Lets Discnct notice which app is in the foreground, so it can tell a blocked app (or its Reels/Shorts tab) from anything else.",
        isGranted = PermissionStatus::isAccessibilityServiceEnabled,
        settingsIntent = { PermissionStatus.accessibilitySettingsIntent() },
    ),
    Step(
        title = "Usage Access",
        body = "Used to read how long blocked apps have been open, so cooldowns and earned time can be tracked accurately.",
        isGranted = PermissionStatus::isUsageAccessGranted,
        settingsIntent = { PermissionStatus.usageAccessSettingsIntent() },
    ),
    Step(
        title = "Display Over Other Apps",
        body = "The block screen is an overlay drawn on top of whatever you're trying to open — this permission is what allows that.",
        isGranted = PermissionStatus::isOverlayGranted,
        settingsIntent = { PermissionStatus.overlaySettingsIntent(it) },
    ),
    Step(
        title = "Battery Optimization",
        body = "Without this, Android may pause Discnct's blocking service in the background to save power.",
        isGranted = PermissionStatus::isIgnoringBatteryOptimizations,
        settingsIntent = { PermissionStatus.batteryOptimizationIntent(it) },
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex]
    var granted by remember(stepIndex) { mutableStateOf(step.isGranted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, stepIndex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = step.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Text(
            text = "PERMISSION ${stepIndex + 1} / ${steps.size}",
            style = DiscnctType.label,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = step.title,
            style = DiscnctType.displayMd,
            color = colors.textDisplay,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = step.body,
            style = DiscnctType.body,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Chip(label = if (granted) "Granted" else "Not granted", active = granted)

        Spacer(modifier = Modifier.weight(1f))

        PillButton(
            label = "Open Settings",
            onClick = { context.startActivity(step.settingsIntent(context)) },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        PillButton(
            label = if (stepIndex == steps.lastIndex) "Finish" else "Continue",
            onClick = {
                if (stepIndex == steps.lastIndex) onComplete() else stepIndex += 1
            },
            enabled = granted,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Skip for now",
            style = DiscnctType.caption,
            color = colors.textDisabled,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (stepIndex == steps.lastIndex) onComplete() else stepIndex += 1
                },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
