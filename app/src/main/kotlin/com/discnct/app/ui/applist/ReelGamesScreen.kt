package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.discnct.app.game.GameType
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.settings.PinPromptDialog
import com.discnct.app.ui.settings.PinPromptMode
import com.discnct.app.ui.settings.StrictModeStore
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

/**
 * Section 2 — "Blocker + Games". The gentler middle level: you keep the app but its Reels/Shorts
 * feed gets bounced, and when something *is* blocked you can play a quick game to earn time back.
 * Three controls, top to bottom: the master on/off for the reel blocker, which games the block
 * screen may offer, and which apps' reel feeds to guard.
 */
@Composable
fun ReelGamesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { BlockerGamesStore(context.applicationContext) }
    val reelEnabled by store.reelBlockingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val enabledGames by store.enabledGames.collectAsStateWithLifecycle(initialValue = GameType.entries.toSet())

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingMasterUnlock by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val reelApps = state.rows.filter { it.isReelHost }.filteredBy(query)

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "Blocker + Games",
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
                    text = "Block only the Reels/Shorts feed inside an app while the rest stays usable. " +
                        "When something's blocked, play a quick game to earn a few minutes back.",
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

            item { SubHeader("Games on the block screen") }
            item {
                GamePicker(
                    enabledGames = enabledGames,
                    onGameToggle = { type, on -> scope.launch { store.setGameEnabled(type, on) } },
                )
            }

            item { SubHeader("Apps with a Reels / Shorts feed") }
            if (reelApps.isEmpty()) {
                item {
                    Text(
                        text = when {
                            state.isLoading -> "Loading apps…"
                            query.isNotBlank() -> "No reel apps match “${query.trim()}”."
                            else -> "No apps with a Reels/Shorts feed were found on this device."
                        },
                        style = DiscnctType.bodySmall,
                        color = colors.textDisabled,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(reelApps, key = { it.packageName }) { row ->
                    ReelAppRow(
                        row = row,
                        masterEnabled = reelEnabled,
                        onToggle = { viewModel.setReelBlocked(row.packageName, it) },
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
                        "On — opening a Reels/Shorts feed in a guarded app bounces you straight out."
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

@Composable
private fun GamePicker(
    enabledGames: Set<GameType>,
    onGameToggle: (GameType, Boolean) -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Pick which games can show up when you choose to play for time. At least one stays on.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DiscnctShapes.card)
                .background(colors.surface)
                .border(1.dp, colors.border, DiscnctShapes.card),
        ) {
            GameType.entries.forEachIndexed { i, type ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = type.displayName,
                        style = DiscnctType.body,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    DiscnctToggle(
                        checked = type in enabledGames,
                        onCheckedChange = { on -> onGameToggle(type, on) },
                    )
                }
                if (i != GameType.entries.lastIndex) {
                    HorizontalDivider(color = colors.border, thickness = 1.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ReelAppRow(row: AppRow, masterEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            bitmap = row.icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(DiscnctShapes.cardCompact),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, style = DiscnctType.body, color = colors.textPrimary)
            Text(
                text = when {
                    !masterEnabled -> "Blocker paused"
                    row.isReelBlocked -> "Reels blocked"
                    else -> "Reels allowed"
                },
                style = DiscnctType.label,
                color = when {
                    !masterEnabled -> colors.textDisabled
                    row.isReelBlocked -> colors.accent
                    else -> colors.textSecondary
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DiscnctToggle(checked = row.isReelBlocked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SubHeader(text: String) {
    val colors = LocalDiscnctColors.current
    Text(
        text = text.uppercase(),
        style = DiscnctType.label,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp),
    )
}
