package com.discnct.app.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Section 1 — the whole-app blocker. Every launchable app gets a single toggle: on means opening
 * it drops you onto the block screen. This is the bluntest, strongest level, so it's deliberately
 * the simplest screen: one list, one switch per app.
 */
@Composable
fun BlockerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(title = "Blocker", onBack = onBack)
        Text(
            text = "Apps you switch on here are blocked completely — the moment one opens, Discnct " +
                "covers it with the block screen.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = colors.border, thickness = 1.dp)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textDisplay)
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.rows, key = { it.packageName }) { row ->
                WholeAppRow(row = row, onToggle = { viewModel.setBlocked(row.packageName, it) })
                HorizontalDivider(color = colors.border, thickness = 1.dp)
            }
        }
    }
}

@Composable
private fun WholeAppRow(row: AppRow, onToggle: (Boolean) -> Unit) {
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
        Text(
            text = row.label,
            style = DiscnctType.body,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        DiscnctToggle(checked = row.isBlocked, onCheckedChange = onToggle)
    }
}
