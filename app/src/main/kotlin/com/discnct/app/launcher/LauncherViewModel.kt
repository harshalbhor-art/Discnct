package com.discnct.app.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.discnct.app.ui.applist.AppRow
import com.discnct.app.ui.applist.BlockListStore
import com.discnct.app.ui.applist.InstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class LauncherUiState(
    val rows: List<AppRow> = emptyList(),
    val restrictedModeEnabled: Boolean = false,
    /** True when [rows] has been narrowed to the allowed list rather than showing everything. */
    val isMinimalGrid: Boolean = false,
    val isLoading: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val blockListStore = BlockListStore(application)
    private val modeStore = LauncherModeStore(application)
    private val allowedAppsStore = AllowedAppsStore(application)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val installedApps = repository.loadLaunchableApps()
            val essentials = EssentialApps.resolve(application)

            combine(
                blockListStore.blockedPackages,
                modeStore.restrictedModeEnabled,
                allowedAppsStore.allowedPackages,
            ) { blocked, restricted, allowed ->
                val allRows = installedApps.map { app ->
                    AppRow(app.packageName, app.label, app.icon, app.packageName in blocked)
                }
                // Restricted mode is what turns this into a minimal launcher. With it off,
                // Discnct Home stays a normal launcher showing everything — the user may have
                // set it as Home before deciding they want the restriction.
                val visible = if (restricted) {
                    val keep = allowed ?: essentials
                    allRows.filter { it.packageName in keep }
                } else {
                    allRows
                }
                LauncherUiState(
                    rows = visible,
                    restrictedModeEnabled = restricted,
                    isMinimalGrid = restricted,
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
    }
}
