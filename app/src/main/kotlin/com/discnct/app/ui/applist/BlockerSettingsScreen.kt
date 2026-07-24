package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.settings.PinPromptDialog
import com.discnct.app.ui.settings.PinPromptMode
import com.discnct.app.ui.settings.StrictModeStore
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Section 1 — the whole-app blocker. Every launchable app gets a single toggle: on means opening
 * it drops you onto the block screen. This is the bluntest, strongest level, so it's deliberately
 * the simplest screen: one list, one switch per app. The list is ranked social-first then by how
 * much you use each app (see AppOrdering), with a corner search to jump to anything else.
 */
@Composable
fun BlockerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingUnlockPackage by remember { mutableStateOf<String?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleRows = state.rows.filteredBy(query)

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "Blocker",
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
        } else {
            Text(
                text = "Apps you switch on here are blocked completely — the moment one opens, Discnct " +
                    "covers it with the block screen.",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            )
        }
        HorizontalDivider(color = colors.border, thickness = 1.dp)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textDisplay)
            }
            return@Column
        }

        if (visibleRows.isEmpty()) {
            Text(
                text = "No apps match “${query.trim()}”.",
                style = DiscnctType.bodySmall,
                color = colors.textDisabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
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
