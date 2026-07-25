package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.game.GameType
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.service.GamePlayCountStore
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
 * The game pool lives on this screen rather than Section 1 because it's the level named after it,
 * but both levels draw from it — a blocked reel feed offers the same games.
 */
@Composable
fun AppBlockerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gamesStore = remember { BlockerGamesStore(context.applicationContext) }
    val playCountStore = remember { GamePlayCountStore(context.applicationContext) }
    val enabledGames by gamesStore.enabledGames
        .collectAsStateWithLifecycle(initialValue = GameType.entries.toSet())
    val playsToday by playCountStore.playsToday
        .collectAsStateWithLifecycle(initialValue = emptyMap<GameType, Int>())

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
                        playsToday = playsToday,
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
 * Which games the block screen may offer, and how much of each is left. The limits themselves
 * aren't editable — the puzzles are rationed per day and the idle games are capped by a clock, so
 * the only lever here is which ones you want to see at all.
 */
@Composable
private fun GamePicker(
    enabledGames: Set<GameType>,
    playsToday: Map<GameType, Int>,
    onGameToggle: (GameType, Boolean) -> Unit,
) {
    val colors = LocalDiscnctColors.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Pick which games can show up when you play for time. At least one stays on.",
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
                val cap = type.dailyPlayCap
                val exhausted = cap != null && (playsToday[type] ?: 0) >= cap
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type.displayName,
                            style = DiscnctType.body,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = type.limitLabel(playsToday[type] ?: 0),
                            style = DiscnctType.label,
                            color = if (exhausted) colors.warning else colors.textDisabled,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
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
