package com.discnct.app.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.launcher.AllowedAppsStore
import com.discnct.app.launcher.LauncherModeStore
import com.discnct.app.launcher.LauncherStatus
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

/**
 * Section 3 — "Total Disconnect", the deepest level: everything Section 2 blocks, plus Discnct
 * standing in as your home screen with only a handful of apps left on it. Still under
 * construction, so the screen leads with a clear WIP banner — the controls below it (restriction
 * on/off, allowed apps, setting Discnct as the OS Home app) all work, but the polished experience
 * is deferred to a later milestone.
 */
@Composable
fun TotalDisconnectScreen(
    onBack: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current
    val scope = rememberCoroutineScope()
    val store = remember { LauncherModeStore(context) }
    val allowedStore = remember { AllowedAppsStore(context.applicationContext) }
    val restrictedEnabled by store.restrictedModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val allowedPackages by allowedStore.allowedPackages.collectAsStateWithLifecycle(initialValue = null)
    var isDefaultHome by remember { mutableStateOf(LauncherStatus.isDefaultHome(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefaultHome = LauncherStatus.isDefaultHome(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTopBar(title = "Total Disconnect", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            WipBanner()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The strongest level. Discnct becomes your home screen, stripped back to the " +
                    "apps you allow, and everything blocked in App Blocker stays blocked — routed " +
                    "through the block screen even if the accessibility service is turned off. " +
                    "We're still building this out, so expect rough edges until a later update.",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Restricted Launcher", style = DiscnctType.subheading, color = colors.textDisplay)
                    Text(
                        text = "While on, Discnct Home shows only your allowed apps, and tapping a " +
                            "blocked one routes through the block screen instead of opening it.",
                        style = DiscnctType.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                DiscnctToggle(
                    checked = restrictedEnabled,
                    onCheckedChange = { enabled -> scope.launch { store.setRestrictedModeEnabled(enabled) } },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            AllowedAppsRow(
                selectedCount = allowedPackages?.size,
                onClick = onOpenAllowedApps,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(label = if (isDefaultHome) "Discnct is Home" else "Not set as Home", active = isDefaultHome)
                if (!isDefaultHome) {
                    PillButton(
                        label = "Set as Home",
                        onClick = { context.startActivity(LauncherStatus.homeSettingsIntent()) },
                        variant = ButtonVariant.Secondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * A null [selectedCount] means the user hasn't picked yet, so the launcher is running on the
 * essentials fallback — worth saying out loud rather than showing a count they never chose.
 */
@Composable
private fun AllowedAppsRow(selectedCount: Int?, onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, DiscnctShapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Allowed Apps", style = DiscnctType.subheading, color = colors.textDisplay)
            Text(
                text = when (selectedCount) {
                    null -> "Not set — using your phone, messages, camera, clock and settings"
                    1 -> "1 app on your home screen"
                    else -> "$selectedCount apps on your home screen"
                },
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(text = "›", style = DiscnctType.heading, color = colors.textSecondary)
    }
}

@Composable
private fun WipBanner() {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .border(1.dp, colors.warning, DiscnctShapes.card)
            .padding(16.dp),
    ) {
        Text("WORK IN PROGRESS", style = DiscnctType.label, color = colors.warning)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This level isn't finished. You can try the controls below, but we'll polish the full experience in a later update.",
            style = DiscnctType.bodySmall,
            color = colors.textPrimary,
        )
    }
}
