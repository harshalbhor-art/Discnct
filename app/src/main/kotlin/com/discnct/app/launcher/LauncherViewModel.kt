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
    val isLoading: Boolean = true,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val blockListStore = BlockListStore(application)
    private val modeStore = LauncherModeStore(application)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val installedApps = repository.loadLaunchableApps()
            combine(blockListStore.blockedPackages, modeStore.restrictedModeEnabled) { blocked, restricted ->
                LauncherUiState(
                    rows = installedApps.map { app ->
                        AppRow(app.packageName, app.label, app.icon, app.packageName in blocked)
                    },
                    restrictedModeEnabled = restricted,
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
    }
}
