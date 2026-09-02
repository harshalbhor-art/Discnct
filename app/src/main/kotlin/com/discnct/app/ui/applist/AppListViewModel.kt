package com.discnct.app.ui.applist

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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

    // A StateFlow rather than a plain var: it needs to be part of the combine below so a refresh
    // (a new install/uninstall) actually re-emits rows, not just silently update a field nothing
    // is watching. Null (rather than an empty list) marks "hasn't loaded yet", so the combine below
    // can tell a genuinely empty device apart from the load that hasn't finished.
    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)

    private var usageMillis: Map<String, Long> = emptyMap()

    // Package add/remove/replace is the one thing that can change what belongs in this list after
    // first load; a context-registered receiver catches it live instead of leaving the list stale
    // until the process restarts. System-sent, so RECEIVER_NOT_EXPORTED is correct — nothing but
    // the OS should be able to trigger this.
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadInstalledApps()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            getApplication(),
            packageChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        loadInstalledApps()

        viewModelScope.launch {
            combine(
                installedApps,
                store.blockedPackages,
                reelStore.reelBlockedPackages,
                feedStore.feedBlockedPackages,
            ) { apps, blocked, reelBlocked, feedBlocked ->
                if (apps == null) {
                    AppListUiState(isLoading = true)
                } else {
                    AppListUiState(
                        rows = apps.map { app ->
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
                        },
                        isLoading = false,
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = repository.loadLaunchableApps()
            usageMillis = usageRepository.foregroundMillisByPackage()
            // Social apps pinned to the top, then most-used, then alphabetical (see AppOrdering).
            installedApps.value = apps.sortedWith(appListComparator(usageMillis))
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(packageChangeReceiver)
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
