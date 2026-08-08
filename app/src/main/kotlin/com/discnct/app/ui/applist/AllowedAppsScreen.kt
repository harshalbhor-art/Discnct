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
import androidx.compose.runtime.LaunchedEffect
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
import com.discnct.app.launcher.AllowedAppsStore
import com.discnct.app.launcher.EssentialApps
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
 * Which apps survive on Discnct Home while Level 3's restricted launcher is on.
 *
 * Note the PIN gate runs the opposite way round to the block list: there, switching a block *off*
 * is the weakening move; here, letting one more app back onto your home screen is. Strict Mode
 * should cost you something in whichever direction makes the phone easier to reach for.
 */
@Composable
fun AllowedAppsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AllowedAppsStore(context.applicationContext) }
    val allowed by store.allowedPackages.collectAsStateWithLifecycle(initialValue = null)

    // Opening the picker is the moment the implicit essentials fallback becomes a real, editable
    // selection — otherwise the user would see toggles that don't match what their home screen
    // is actually showing.
    LaunchedEffect(Unit) {
        store.seedIfUnconfigured(EssentialApps.resolve(context.applicationContext))
    }

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingAllowPackage by remember { mutableStateOf<String?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleRows = state.rows.filteredBy(query)
    val allowedPackages = allowed.orEmpty()

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "Allowed Apps",
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
                text = "Only these apps appear on Discnct Home while the restricted launcher is on. " +
                    "${allowedPackages.size} selected.",
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
                AllowedAppRow(
                    row = row,
                    allowed = row.packageName in allowedPackages,
                    onToggle = { newValue ->
                        if (newValue && strictEnabled) {
                            pendingAllowPackage = row.packageName
                        } else {
                            scope.launch { store.setAllowed(row.packageName, newValue) }
                        }
                    },
                )
                HorizontalDivider(color = colors.border, thickness = 1.dp)
            }
        }
    }

    val allowPackage = pendingAllowPackage
    if (allowPackage != null) {
        PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to allow this app on your home screen",
            onConfirmed = {
                scope.launch { store.setAllowed(allowPackage, true) }
                pendingAllowPackage = null
            },
            onDismiss = { pendingAllowPackage = null },
        )
    }
}

@Composable
private fun AllowedAppRow(row: AppRow, allowed: Boolean, onToggle: (Boolean) -> Unit) {
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
                    allowed && row.isBlocked -> "On home screen — still blocked when opened"
                    allowed -> "On home screen"
                    else -> "Hidden"
                },
                style = DiscnctType.label,
                color = if (allowed) colors.textSecondary else colors.textDisabled,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DiscnctToggle(checked = allowed, onCheckedChange = onToggle)
    }
}
