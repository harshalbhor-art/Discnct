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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.service.FinanceAppStore
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
 * The apps Discnct steps aside for: banks, payments, and the authenticators standing in front of
 * them.
 *
 * Discnct ships a list of the ones it recognises, and this screen is where a user corrects it —
 * their bank almost certainly isn't in it, because there are thousands and package names can't be
 * guessed from the outside.
 *
 * Adding an app here is a weakening move, so Strict Mode gates it the same way it gates unblocking
 * one. Removing an app is Discnct doing *more*, and is never gated.
 */
@Composable
fun FinancialAppsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FinanceAppStore(context.applicationContext) }
    val exempt by store.exemptPackages.collectAsStateWithLifecycle(initialValue = emptySet())

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    var pendingExemptPackage by remember { mutableStateOf<String?>(null) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleRows = state.rows.filteredBy(query)
    val exemptOnDevice = state.rows.count { it.packageName in exempt }

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(
            title = "Financial Apps",
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
                text = "Discnct switches itself off while one of these is open, and switches back " +
                    "on the moment you leave it. Being locked out of your bank is worse than a " +
                    "minute of scrolling. Check yours is on the list — $exemptOnDevice installed.",
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
                FinancialAppRow(
                    row = row,
                    exempt = row.packageName in exempt,
                    recognised = store.isSeeded(row.packageName),
                    onToggle = { newValue ->
                        if (newValue && strictEnabled) {
                            pendingExemptPackage = row.packageName
                        } else {
                            scope.launch { store.setExempt(row.packageName, newValue) }
                        }
                    },
                )
                HorizontalDivider(color = colors.border, thickness = 1.dp)
            }
        }
    }

    val exemptPackage = pendingExemptPackage
    if (exemptPackage != null) {
        PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to let this app switch Discnct off",
            onConfirmed = {
                scope.launch { store.setExempt(exemptPackage, true) }
                pendingExemptPackage = null
            },
            onDismiss = { pendingExemptPackage = null },
        )
    }
}

@Composable
private fun FinancialAppRow(
    row: AppRow,
    exempt: Boolean,
    recognised: Boolean,
    onToggle: (Boolean) -> Unit,
) {
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
            Text(
                text = row.label,
                style = DiscnctType.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    exempt && recognised -> "Discnct stands down — recognised"
                    exempt -> "Discnct stands down"
                    recognised -> "Enforced here, though Discnct recognises it"
                    else -> "Enforced here"
                },
                style = DiscnctType.label,
                color = if (exempt) colors.textSecondary else colors.textDisabled,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        DiscnctToggle(checked = exempt, onCheckedChange = onToggle, contentDescription = "Exempt ${row.label} from enforcement")
    }
}
