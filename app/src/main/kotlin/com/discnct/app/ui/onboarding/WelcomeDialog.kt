package com.discnct.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * One-time popup shown on first launch. Its whole reason to exist is the thing that silently
 * breaks most sideloaded installs: on Android 13+ the Accessibility toggle for an app installed
 * outside the Play Store is greyed out ("Restricted setting") until the user explicitly allows it
 * from the app's three-dot menu. Explaining that up front is the difference between "the app does
 * nothing" and "the app works".
 */
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onSetUpPermissions: () -> Unit,
) {
    val colors = LocalDiscnctColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.card)
                .background(colors.surface)
                .border(1.dp, colors.borderVisible, DiscnctShapes.card)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("WELCOME TO DISCNCT", style = DiscnctType.label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "One quick setting",
                style = DiscnctType.heading,
                color = colors.textDisplay,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Discnct works by watching which app is in front so it can step in when you open " +
                    "something you've blocked. That needs Android's Accessibility permission.",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Because this build is installed outside the Play Store, Android may grey out that " +
                    "toggle and call it a “Restricted setting.” That's normal — here's how to unlock it:",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))
            RestrictedSettingsSteps()
            Spacer(modifier = Modifier.height(22.dp))

            PillButton(
                label = "Set Up Permissions",
                onClick = onSetUpPermissions,
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            PillButton(
                label = "I'll do it later",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RestrictedSettingsSteps() {
    val colors = LocalDiscnctColors.current
    val steps = listOf(
        "Open Settings › Apps › Discnct",
        "Tap the ⋮ menu (top-right)",
        "Choose “Allow restricted settings”",
        "Come back and turn on Accessibility",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.cardCompact)
            .background(colors.surfaceRaised)
            .padding(16.dp),
    ) {
        steps.forEachIndexed { i, step ->
            Text(
                text = "${i + 1}.  $step",
                style = DiscnctType.bodySmall,
                color = colors.textPrimary,
            )
            if (i != steps.lastIndex) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
