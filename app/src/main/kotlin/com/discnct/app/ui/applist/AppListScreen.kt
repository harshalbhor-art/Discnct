package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.launcher.LauncherModeStore
import com.discnct.app.launcher.LauncherStatus
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

@Composable
fun AppListScreen(modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Block List", style = DiscnctType.heading.copy(fontWeight = FontWeight.Bold), color = colors.textDisplay)
            Text(
                "Pick which apps are blocked. Opening one shows the block screen — hold for 30 seconds to get in for 5 minutes.",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Level3Section()
        HorizontalDivider(color = colors.border, thickness = 1.dp)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textDisplay)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.rows, key = { it.packageName }) { row ->
                AppRowItem(row = row, onToggle = { viewModel.setBlocked(row.packageName, it) })
                HorizontalDivider(color = colors.border, thickness = 1.dp)
            }
        }
    }
}

/**
 * Level 3 toggle: enables/disables restriction enforcement in [com.discnct.app.launcher.LauncherScreen]
 * independently of whether Discnct has actually been picked as the OS default Home app — that
 * choice can only be made in system settings, so this section also surfaces current status and
 * a shortcut to make it.
 */
@Composable
private fun Level3Section(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current
    val scope = rememberCoroutineScope()
    val store = remember { LauncherModeStore(context) }
    val restrictedEnabled by store.restrictedModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    var isDefaultHome by remember { mutableStateOf(LauncherStatus.isDefaultHome(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefaultHome = LauncherStatus.isDefaultHome(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Level 3 — Restricted Launcher", style = DiscnctType.body, color = colors.textDisplay)
                Text(
                    "Replaces your home screen. While on, tapping a blocked app from Discnct Home routes through the block screen instead of opening it.",
                    style = DiscnctType.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            DiscnctToggle(
                checked = restrictedEnabled,
                onCheckedChange = { enabled -> scope.launch { store.setRestrictedModeEnabled(enabled) } },
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
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
    }
}

@Composable
private fun AppRowItem(row: AppRow, onToggle: (Boolean) -> Unit) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            bitmap = row.icon,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(DiscnctShapes.cardCompact),
        )
        Text(
            text = row.label,
            style = DiscnctType.body,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        DiscnctToggle(checked = row.isBlocked, onCheckedChange = onToggle)
    }
}
