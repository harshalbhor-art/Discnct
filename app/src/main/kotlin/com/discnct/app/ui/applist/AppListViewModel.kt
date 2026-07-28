package com.discnct.app.ui.applist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import com.discnct.app.feed.isFeedHostPackage
import com.discnct.app.reel.isReelHostPackage
import com.discnct.app.service.FeedBlockStore
import com.discnct.app.service.ReelBlockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AppRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isBlocked: Boolean,
    /** This app has a Reels/Shorts feed we can block surgically. */
    val isReelHost: Boolean = false,
    /** The reel-only block is enabled for this app. */
    val isReelBlocked: Boolean = false,
    /** This app has a scrolling feed we know how to find and cover. */
    val isFeedHost: Boolean = false,
    /** The feed block is enabled for this app. */
    val isFeedBlocked: Boolean = false,
    /** Foreground time over the recent window (from Usage Access), 0 when unknown. */
    val usageMillis: Long = 0L,
)

data class AppListUiState(
    val rows: List<AppRow> = emptyList(),
    val isLoading: Boolean = true,
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val usageRepository = UsageStatsRepository(application)
    private val store = BlockListStore(application)
    private val reelStore = ReelBlockStore(application)
    private val feedStore = FeedBlockStore(application)

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private var installedApps: List<InstalledApp> = emptyList()

    private var usageMillis: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            val apps = repository.loadLaunchableApps()
            usageMillis = usageRepository.foregroundMillisByPackage()
            // Social apps pinned to the top, then most-used, then alphabetical (see AppOrdering).
            installedApps = apps.sortedWith(appListComparator(usageMillis))
            combine(
                store.blockedPackages,
                reelStore.reelBlockedPackages,
                feedStore.feedBlockedPackages,
            ) { blocked, reelBlocked, feedBlocked ->
                installedApps.map { app ->
                    AppRow(
                        packageName = app.packageName,
                        label = app.label,
                        icon = app.icon,
                        isBlocked = app.packageName in blocked,
                        isReelHost = isReelHostPackage(app.packageName),
                        isReelBlocked = app.packageName in reelBlocked,
                        isFeedHost = isFeedHostPackage(app.packageName),
                        isFeedBlocked = app.packageName in feedBlocked,
                        usageMillis = usageMillis[app.packageName] ?: 0L,
                    )
                }
            }.collect { rows ->
                _uiState.value = AppListUiState(rows = rows, isLoading = false)
            }
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { store.setBlocked(packageName, blocked) }
    }

    fun setReelBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { reelStore.setReelBlocked(packageName, blocked) }
    }

    fun setFeedBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { feedStore.setFeedBlocked(packageName, blocked) }
    }
}
