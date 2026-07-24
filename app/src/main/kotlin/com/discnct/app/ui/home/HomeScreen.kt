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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** The three things the home screen routes into. */
enum class Section { Blocker, ReelGames, TotalDisconnect }

@Composable
fun HomeScreen(
    onOpenSection: (Section) -> Unit,
    onOpenPermissions: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current

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
            text = "Break the scroll. Block the apps that hook you — or keep them and\nkill just the Reels and Shorts.",
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
            title = "Blocker",
            subtitle = "Block whole apps completely. Open one and you hit a wall instead.",
            onClick = { onOpenSection(Section.Blocker) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard(
            index = "02",
            title = "Blocker + Games",
            subtitle = "Keep the app, block only its Reels/Shorts. Earn time back by playing a quick game.",
            onClick = { onOpenSection(Section.ReelGames) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard(
            index = "03",
            title = "Total Disconnect",
            subtitle = "Replace your home screen with a calm launcher that guards every tap.",
            wip = true,
            onClick = { onOpenSection(Section.TotalDisconnect) },
        )
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
