package com.discnct.app.ui.applist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.settings.PinPromptDialog
import com.discnct.app.ui.settings.PinPromptMode
import com.discnct.app.ui.settings.StrictModeStore
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

/**
 * Section 1 — "Reel Blocker", the level to reach for first. It's the least disruptive thing
 * Discnct can do: the app you're worried about keeps working, and only the parts of it built to
 * hold you get interrupted.
 *
 * Two per-app switches, because they're genuinely different habits and plenty of people want one
 * without the other. **Reels & Shorts** bounces you out of the full-screen viewer. **Main feed**
 * covers the scrolling timeline where it sits, leaving DMs, search, posting and profiles alone.
 *
 * No games and no timer live here. There's nothing to dismiss or play through, so there's nothing
 * to configure beyond which apps each switch applies to.
 */
@Composable
fun ReelBlockerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { BlockerGamesStore(context.applicationContext) }
    val reelEnabled by store.reelBlockingEnabled.collectAsStateWithLifecycle(initialValue = true)

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingMasterUnlock by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val guardableApps = state.rows.filter { it.isReelHost || it.isFeedHost }.filteredBy(query)

    // Read once per composition rather than collected: the user leaves the app to grant this, and
    // coming back re-composes, so a snapshot is enough and there's nothing to keep in sync.
    val overlayGranted = PermissionStatus.isOverlayGranted(context)
    val feedBlockOn = state.rows.any { it.isFeedBlocked }

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "Reel Blocker",
            onBack = onBack,
            trailing = {
                SearchIconButton(
                    active = searchOpen,
                    onToggle = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                )
            },
        )

        if (searchOpen) {
            AppSearchField(query = query, onQueryChange = { query = it })
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    text = "Take out the endless parts of an app and leave the rest working. " +
                        "Reels and Shorts bounce you straight back to where you came from; a " +
                        "blocked feed is covered over where it sits. Messages, search, posting " +
                        "and profiles are untouched either way. There's no way to earn your way " +
                        "in — to get them back, turn the switch off.",
                    style = DiscnctType.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
                )
            }

            item {
                MasterToggleCard(
                    enabled = reelEnabled,
                    onToggle = { newValue ->
                        if (!newValue && strictEnabled) {
                            pendingMasterUnlock = true
                        } else {
                            scope.launch { store.setReelBlockingEnabled(newValue) }
                        }
                    },
                )
            }

            item {
                Text(
                    text = "Shortcut: long-press the Discnct icon on your home screen, or add the " +
                        "“Reel Blocker” tile to Quick Settings, to flip this switch without opening " +
                        "the app.",
                    style = DiscnctType.label,
                    color = colors.textDisabled,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                )
            }

            if (feedBlockOn && !overlayGranted) {
                item { OverlayPermissionNotice() }
            }

            item { SubHeader("Apps you can guard") }
            if (guardableApps.isEmpty()) {
                item {
                    Text(
                        text = when {
                            state.isLoading -> "Loading apps…"
                            query.isNotBlank() -> "No guardable apps match “${query.trim()}”."
                            else -> "No apps with a Reels/Shorts feed were found on this device."
                        },
                        style = DiscnctType.bodySmall,
                        color = colors.textDisabled,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(guardableApps, key = { it.packageName }) { row ->
                    GuardedAppRow(
                        row = row,
                        masterEnabled = reelEnabled,
                        onToggleReels = { viewModel.setReelBlocked(row.packageName, it) },
                        onToggleFeed = { viewModel.setFeedBlocked(row.packageName, it) },
                    )
                    HorizontalDivider(color = colors.border, thickness = 1.dp)
                }
            }
        }
    }

    if (pendingMasterUnlock) {
        PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to turn off the reel blocker",
            onConfirmed = {
                scope.launch { store.setReelBlockingEnabled(false) }
                pendingMasterUnlock = false
            },
            onDismiss = { pendingMasterUnlock = false },
        )
    }
}

@Composable
private fun MasterToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.card)
                .background(colors.surface)
                .border(1.dp, if (enabled) colors.borderVisible else colors.border, DiscnctShapes.card)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Reel & Shorts Blocker", style = DiscnctType.subheading, color = colors.textDisplay)
                Text(
                    text = if (enabled) {
                        "On — opening a Reels/Shorts feed in a guarded app bounces you back out."
                    } else {
                        "Paused — no reels are blocked until you turn this back on."
                    },
                    style = DiscnctType.bodySmall,
                    color = if (enabled) colors.textSecondary else colors.warning,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            DiscnctToggle(checked = enabled, onCheckedChange = onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * One app, with a switch for each surface Discnct knows how to guard inside it. The switches are
 * stacked rather than sat side by side: two bare toggles on one line give no clue which is which,
 * and these two do quite different things to the app.
 */
@Composable
private fun GuardedAppRow(
    row: AppRow,
    masterEnabled: Boolean,
    onToggleReels: (Boolean) -> Unit,
    onToggleFeed: (Boolean) -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                bitmap = row.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(DiscnctShapes.cardCompact),
            )
            Text(text = row.label, style = DiscnctType.body, color = colors.textPrimary)
        }

        if (row.isReelHost) {
            SurfaceToggleRow(
                title = "Reels & Shorts",
                subtitle = when {
                    !masterEnabled -> "Blocker paused"
                    row.isReelBlocked -> "Bounced out of the viewer"
                    else -> "Allowed"
                },
                highlighted = masterEnabled && row.isReelBlocked,
                dimmed = !masterEnabled,
                checked = row.isReelBlocked,
                onCheckedChange = onToggleReels,
            )
        }

        if (row.isFeedHost) {
            SurfaceToggleRow(
                title = "Main feed",
                subtitle = if (row.isFeedBlocked) {
                    "Feed and stories covered over"
                } else {
                    "Allowed"
                },
                highlighted = row.isFeedBlocked,
                dimmed = false,
                checked = row.isFeedBlocked,
                onCheckedChange = onToggleFeed,
            )
        }
    }
}

@Composable
private fun SurfaceToggleRow(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    dimmed: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = DiscnctType.body,
                color = if (dimmed) colors.textDisabled else colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = DiscnctType.label,
                color = when {
                    dimmed -> colors.textDisabled
                    highlighted -> colors.accent
                    else -> colors.textSecondary
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DiscnctToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Covering a feed means drawing over another app, which needs a permission that can be revoked
 * long after onboarding granted it. Without this notice the switch would read as on while nothing
 * happened on screen, which is the worst way for a blocker to fail.
 */
@Composable
private fun OverlayPermissionNotice() {
    val colors = LocalDiscnctColors.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.accent, DiscnctShapes.card)
            .clickable {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            .padding(16.dp),
    ) {
        Text("Feed blocking needs permission", style = DiscnctType.subheading, color = colors.textDisplay)
        Text(
            text = "Discnct can't draw over other apps yet, so blocked feeds will stay visible. " +
                "Tap to grant “Display over other apps”.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Small all-caps run-in heading the section screens use to break their lists apart. */
@Composable
internal fun SubHeader(text: String) {
    val colors = LocalDiscnctColors.current
    Text(
        text = text.uppercase(),
        style = DiscnctType.label,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp),
    )
}
