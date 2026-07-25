package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.game.GameType
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.blockscreen.GameHost
import com.discnct.app.ui.components.Chip
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
 * Section 2 — "App Blocker + Games", the escalation from Section 1. Where the reel blocker leaves
 * the app usable, this walls off the whole thing: open a switched-on app and you hit the block
 * screen. The way back in is either 30 seconds of holding a button or winning one of the games
 * picked here.
 *
 * The game pool is this level's alone. Section 1 doesn't offer games at all — a blocked reel feed
 * is bounced straight back out, with nothing to play through.
 */
@Composable
fun AppBlockerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gamesStore = remember { BlockerGamesStore(context.applicationContext) }
    val enabledGames by gamesStore.enabledGames
        .collectAsStateWithLifecycle(initialValue = GameType.entries.toSet())

    // The game the TRY button is previewing, if any. Its outcome is thrown away — this is a
    // demo, so "winning" here can't be a way to farm unlock minutes from the settings screen.
    var previewGame by remember { mutableStateOf<GameType?>(null) }

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingUnlockPackage by remember { mutableStateOf<String?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleRows = state.rows.filteredBy(query)

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "App Blocker + Games",
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
        HorizontalDivider(color = colors.border, thickness = 1.dp)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textDisplay)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // While searching, the user is hunting for one app — the game settings above the
            // list would just be something to scroll past.
            if (!searchOpen) {
                item {
                    Text(
                        text = "Apps you switch on here are blocked completely — the moment one opens, " +
                            "Discnct covers it with the block screen.",
                        style = DiscnctType.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                item { SubHeader("Games on the block screen") }
                item {
                    GamePicker(
                        enabledGames = enabledGames,
                        onTryGame = { previewGame = it },
                        onGameToggle = { type, on -> scope.launch { gamesStore.setGameEnabled(type, on) } },
                    )
                }
                item { SubHeader("Apps to block completely") }
            }

            if (visibleRows.isEmpty()) {
                item {
                    Text(
                        text = "No apps match “${query.trim()}”.",
                        style = DiscnctType.bodySmall,
                        color = colors.textDisabled,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            } else {
                items(visibleRows, key = { it.packageName }) { row ->
                    WholeAppRow(
                        row = row,
                        onToggle = { newValue ->
                            // Turning a block ON never needs the PIN — only weakening protection does.
                            if (!newValue && strictEnabled) {
                                pendingUnlockPackage = row.packageName
                            } else {
                                viewModel.setBlocked(row.packageName, newValue)
                            }
                        },
                    )
                    HorizontalDivider(color = colors.border, thickness = 1.dp)
                }
            }
        }
    }

    previewGame?.let { type ->
        GamePreviewDialog(gameType = type, onClose = { previewGame = null })
    }

    val unlockPackage = pendingUnlockPackage
    if (unlockPackage != null) {
        PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to turn off this block",
            onConfirmed = {
                viewModel.setBlocked(unlockPackage, false)
                pendingUnlockPackage = null
            },
            onDismiss = { pendingUnlockPackage = null },
        )
    }
}

/**
 * Which games the block screen may offer. Each row can be tried before it's switched on — deciding
 * whether you want to face Wordle at 11pm is much easier once you've actually seen it.
 */
@Composable
private fun GamePicker(
    enabledGames: Set<GameType>,
    onTryGame: (GameType) -> Unit,
    onGameToggle: (GameType, Boolean) -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Pick which games can show up when you play for time. At least one stays on. " +
                "Tap TRY to play one now — a try never counts for anything.",
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type.displayName,
                            style = DiscnctType.body,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = type.limitLabel,
                            style = DiscnctType.label,
                            color = colors.textDisabled,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Chip(
                        label = "Try",
                        modifier = Modifier.clickable { onTryGame(type) },
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

/**
 * A game played from the settings screen, full-bleed so it looks exactly like the real thing.
 * [GameHost] reports an outcome when the game ends; here it just closes the dialog, since minutes
 * earned in a preview would be minutes earned without ever having been blocked.
 */
@Composable
private fun GamePreviewDialog(gameType: GameType, onClose: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(colors.black)) {
            GameHost(gameType = gameType, onFinished = { onClose() })
            Text(
                text = "CLOSE",
                style = DiscnctType.label,
                color = colors.textSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clip(DiscnctShapes.pill)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun WholeAppRow(row: AppRow, onToggle: (Boolean) -> Unit) {
    val colors = LocalDiscnctColors.current
    val usageLabel = formatUsage(row.usageMillis)
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
            if (usageLabel.isNotEmpty()) {
                Text(
                    text = "$usageLabel past week",
                    style = DiscnctType.label,
                    color = colors.textDisabled,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        DiscnctToggle(checked = row.isBlocked, onCheckedChange = onToggle)
    }
}
