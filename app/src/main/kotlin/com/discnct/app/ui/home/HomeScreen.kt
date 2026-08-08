package com.discnct.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context
import com.discnct.app.paywall.PaywallUiState
import com.discnct.app.paywall.TRIAL_DAYS
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * The three things the home screen routes into, ordered the way they're meant to be tried:
 * each level leaves you less of the phone than the one above it.
 */
enum class Section { ReelBlocker, AppBlockerGames, TotalDisconnect }

@Composable
fun HomeScreen(
    onOpenSection: (Section) -> Unit,
    onOpenPermissions: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    paywallState: PaywallUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current
    var showTrialInfo by remember { mutableStateOf(false) }

    var permissionsReady by remember { mutableStateOf(allPermissionsGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsReady = allPermissionsGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DISCNCT",
                style = DiscnctType.displayMd,
                color = colors.textDisplay,
                modifier = Modifier.weight(1f),
            )
            Chip(
                label = if (darkTheme) "Dark" else "Light",
                active = true,
                modifier = Modifier.clickable(onClick = onToggleTheme),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            // The two-line "what is this app" line the home screen leads with.
            text = "Break the scroll. Kill just the Reels and Shorts — or go further and\nblock the apps that hook you outright.",
            style = DiscnctType.body,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!permissionsReady) {
            PermissionBanner(onClick = onOpenPermissions)
            Spacer(modifier = Modifier.height(20.dp))
        }

        SectionCard(
            index = "01",
            title = "Reel Blocker",
            subtitle = "Keep the app, block only its Reels and Shorts. Everything else still works.",
            onClick = { onOpenSection(Section.ReelBlocker) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        val trialLabel = trialChipLabel(paywallState)
        SectionCard(
            index = "02",
            title = "App Blocker + Games",
            subtitle = "Block whole apps. Hold to unlock, or win a game to earn a few minutes back.",
            onClick = { onOpenSection(Section.AppBlockerGames) },
            badge = if (trialLabel != null) {
                {
                    Chip(
                        label = trialLabel,
                        active = true,
                        modifier = Modifier.clickable { showTrialInfo = true },
                    )
                }
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard(
            index = "03",
            title = "Total Disconnect",
            subtitle = "Blocked apps, plus a home screen stripped back to the handful you allow.",
            wip = true,
            onClick = { onOpenSection(Section.TotalDisconnect) },
        )

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "SUPPORT",
            style = DiscnctType.label,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SupportCard()
    }

    if (showTrialInfo) {
        TrialInfoDialog(state = paywallState, onDismiss = { showTrialInfo = false })
    }
}

/** Label for the Level 2 card's trial badge, or null once it's unlocked for good. */
private fun trialChipLabel(state: PaywallUiState): String? = when {
    state.unlockedPermanently -> null
    !state.isUnlocked -> "Trial Ended"
    !state.trialStarted -> "$TRIAL_DAYS-Day Trial"
    state.trialDaysRemaining >= 2 -> "${state.trialDaysRemaining} Days Left"
    state.trialDaysRemaining == 1 -> "1 Day Left"
    else -> "Last Day"
}

private fun trialInfoCopy(state: PaywallUiState): Pair<String, String> = when {
    !state.isUnlocked -> "Trial ended" to
        "Your $TRIAL_DAYS-day free trial of App Blocker + Games is over. Unlock it once, forever — " +
            "no subscription, nothing recurring."
    !state.trialStarted -> "$TRIAL_DAYS days, free" to
        "App Blocker + Games is free to try. Your $TRIAL_DAYS-day trial starts the moment you open " +
            "it — after that, it's a one-time unlock, not a subscription."
    state.trialDaysRemaining >= 2 -> "${state.trialDaysRemaining} days left" to
        "Your free trial of App Blocker + Games ends in ${state.trialDaysRemaining} days. After " +
            "that, unlock it once — no subscription, nothing recurring."
    state.trialDaysRemaining == 1 -> "1 day left" to
        "Your free trial of App Blocker + Games ends tomorrow. After that, unlock it once — no " +
            "subscription, nothing recurring."
    else -> "Last day" to
        "Today's the last day of your free trial of App Blocker + Games. After that, unlock it " +
            "once — no subscription, nothing recurring."
}

@Composable
private fun TrialInfoDialog(state: PaywallUiState, onDismiss: () -> Unit) {
    val colors = LocalDiscnctColors.current
    val (heading, body) = trialInfoCopy(state)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.card)
                .background(colors.surface)
                .border(1.dp, colors.borderVisible, DiscnctShapes.card)
                .padding(24.dp),
        ) {
            Text("APP BLOCKER + GAMES", style = DiscnctType.label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = heading, style = DiscnctType.heading, color = colors.textDisplay)
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = body, style = DiscnctType.bodySmall, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(22.dp))
            PillButton(
                label = "Got it",
                onClick = onDismiss,
                variant = ButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun allPermissionsGranted(context: Context): Boolean =
    PermissionStatus.isAccessibilityServiceEnabled(context) &&
        PermissionStatus.isUsageAccessGranted(context) &&
        PermissionStatus.isOverlayGranted(context) &&
        PermissionStatus.isIgnoringBatteryOptimizations(context)

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .border(1.dp, colors.accent, DiscnctShapes.card)
            .background(colors.accentSubtle)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text("SETUP INCOMPLETE", style = DiscnctType.label, color = colors.accent)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Discnct can't block anything until its permissions are on. Tap to finish setup.",
            style = DiscnctType.bodySmall,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SectionCard(
    index: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wip: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, DiscnctShapes.card)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = index, style = DiscnctType.caption, color = colors.textDisabled)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = title, style = DiscnctType.subheading, color = colors.textDisplay)
                if (wip) Chip(label = "WIP")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = DiscnctType.bodySmall, color = colors.textSecondary)
        }
        if (badge != null) badge()
        Box(contentAlignment = Alignment.Center) {
            Text(text = "›", style = DiscnctType.heading, color = colors.textSecondary)
        }
    }
}

/**
 * Shared back-bar every section screen puts at its top. Kept here (with [Section]) so the three
 * section screens don't each reinvent a title/back header.
 */
@Composable
fun SectionTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = "‹  BACK",
            style = DiscnctType.label,
            color = colors.textSecondary,
            modifier = Modifier
                .clip(DiscnctShapes.pill)
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp, horizontal = 2.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = DiscnctType.heading.copy(fontWeight = FontWeight.Bold),
                color = colors.textDisplay,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }
}
