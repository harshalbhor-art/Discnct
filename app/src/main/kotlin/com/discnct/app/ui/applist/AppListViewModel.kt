package com.discnct.app.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isBlocked: Boolean,
)

data class AppListUiState(
    val rows: List<AppRow> = emptyList(),
    val isLoading: Boolean = true,
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val store = BlockListStore(application)

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private var installedApps: List<InstalledApp> = emptyList()

    init {
        viewModelScope.launch {
            installedApps = repository.loadLaunchableApps()
            store.blockedPackages.collectLatest { blocked ->
                _uiState.value = AppListUiState(
                    rows = installedApps.map { app ->
                        AppRow(app.packageName, app.label, app.icon, app.packageName in blocked)
                    },
                    isLoading = false,
                )
            }
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { store.setBlocked(packageName, blocked) }
    }
}
