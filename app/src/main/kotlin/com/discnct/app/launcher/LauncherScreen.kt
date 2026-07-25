package com.discnct.app.launcher

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.ui.applist.AppRow
import com.discnct.app.ui.blockscreen.BlockActivity
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * Discnct's Home-screen replacement. With the restriction off it behaves like an ordinary
 * launcher and shows everything; with it on (Level 3) it does two things:
 *
 *  - narrows the grid to the handful of apps you allowed, so the phone stops being a place to
 *    wander, and
 *  - routes a tap on a blocked app through the same [BlockActivity] the accessibility service
 *    uses, so turning that service off doesn't get you past a block from here.
 */
@Composable
fun LauncherScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LauncherViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "DISCNCT HOME",
                style = DiscnctType.label,
                color = colors.textSecondary,
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.textDisplay)
            }
            return@Column
        }

        if (state.isMinimalGrid) {
            Text(
                text = if (state.rows.isEmpty()) {
                    "No allowed apps yet. Open Discnct → Total Disconnect to choose some."
                } else {
                    "Showing your allowed apps only."
                },
                style = DiscnctType.caption,
                color = colors.textDisabled,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.rows, key = { it.packageName }) { row ->
                LauncherIcon(
                    row = row,
                    locked = state.restrictedModeEnabled && row.isBlocked,
                    onClick = {
                        if (state.restrictedModeEnabled && row.isBlocked) {
                            context.startActivity(
                                Intent(context, BlockActivity::class.java).apply {
                                    putExtra(BlockActivity.EXTRA_PACKAGE_NAME, row.packageName)
                                },
                            )
                        } else {
                            context.packageManager.getLaunchIntentForPackage(row.packageName)?.let {
                                context.startActivity(it)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LauncherIcon(row: AppRow, locked: Boolean, onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Image(
                bitmap = row.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(DiscnctShapes.cardCompact),
            )
            if (locked) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(DiscnctShapes.pill)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "•", style = DiscnctType.caption.copy(color = colors.black, fontWeight = FontWeight.Bold))
                }
            }
        }
        Text(
            text = row.label,
            style = DiscnctType.caption,
            color = if (locked) colors.textDisabled else colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
