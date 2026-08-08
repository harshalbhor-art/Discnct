package com.discnct.app.stats

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.discnct.app.game.GameType
import com.discnct.app.ui.applist.InstalledApp
import com.discnct.app.ui.applist.InstalledAppsRepository
import com.discnct.app.ui.onboarding.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** How far back the screen-time chart looks, and how the window is cut into bars. */
enum class StatsRange(val label: String, val bucketCount: Int, val daysPerBucket: Int) {
    WEEK("7 days", bucketCount = 7, daysPerBucket = 1),
    MONTH("4 weeks", bucketCount = 4, daysPerBucket = 7),
    ;

    val totalDays: Int get() = bucketCount * daysPerBucket
}

/** One bar: its total, and the short label printed under it. */
data class UsageBucket(val label: String, val millis: Long)

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val millis: Long,
)

data class GameStatRow(
    val type: GameType,
    val plays: Int,
    val minutesEarned: Int,
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val usageAccessGranted: Boolean = true,
    val range: StatsRange = StatsRange.WEEK,
    val buckets: List<UsageBucket> = emptyList(),
    val topApps: List<AppUsageRow> = emptyList(),
    val totalScreenMillis: Long = 0L,
    val dailyAverageMillis: Long = 0L,
    val gameRows: List<GameStatRow> = emptyList(),
    val totalPlays: Int = 0,
    val totalMinutesEarned: Int = 0,
    val favouriteGame: GameType? = null,
)

/**
 * Backs the Stats tab. Two unrelated sources meet here: game history, which is ours and always
 * available, and screen time, which needs Usage Access and may be empty. They're combined into one
 * state rather than one each so the screen never has to render half-loaded.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val gameStatsStore = GameStatsStore(application)
    private val usageHistory = UsageHistoryRepository(application)
    private val installedApps = InstalledAppsRepository(application)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val selectedRange = MutableStateFlow(StatsRange.WEEK)

    /**
     * Bumped to force a re-read. Game stats arrive as a flow and update themselves, but screen
     * time is a one-shot query, so something has to ask for it again.
     */
    private val refreshTick = MutableStateFlow(0)

    /** Package → label/icon, loaded once. Only used to dress up the top-apps list. */
    private var appsByPackage: Map<String, InstalledApp> = emptyMap()

    init {
        viewModelScope.launch {
            appsByPackage = installedApps.loadLaunchableApps().associateBy { it.packageName }
            combine(gameStatsStore.tallies, selectedRange, refreshTick) { tallies, range, _ ->
                tallies to range
            }.collect { (tallies, range) ->
                _uiState.value = buildState(tallies, range)
            }
        }
    }

    fun setRange(range: StatsRange) {
        selectedRange.value = range
    }

    /** Re-reads screen time. Called on resume, since usage keeps accruing while the app is away. */
    fun refresh() {
        refreshTick.value += 1
    }

    private suspend fun buildState(tallies: List<GameTally>, range: StatsRange): StatsUiState {
        val context = getApplication<Application>()
        val granted = PermissionStatus.isUsageAccessGranted(context)

        val boundaries = bucketBoundaries(range)
        val windowStart = boundaries.first()
        val windowEnd = boundaries.last()

        val sessions = if (granted) usageHistory.sessions(windowStart, windowEnd) else emptyList()
        val bucketMillis = millisByBucket(sessions, boundaries)
        val total = bucketMillis.sum()

        // Only apps the user could actually open are listed. Usage events also cover the launcher,
        // system UI and Discnct itself, and a chart topped by "Pixel Launcher" tells you nothing
        // about where your evening went. They still count toward the total — the time was real.
        val topApps = millisByPackage(sessions, windowStart, windowEnd)
            .entries
            .mapNotNull { (packageName, millis) ->
                appsByPackage[packageName]?.let { app ->
                    AppUsageRow(
                        packageName = packageName,
                        label = app.label,
                        icon = app.icon,
                        millis = millis,
                    )
                }
            }
            .sortedByDescending { it.millis }
            .take(TOP_APP_COUNT)

        val favourite = mostPlayed(tallies)?.let { tally ->
            GameType.entries.firstOrNull { it.name == tally.key }
        }

        return StatsUiState(
            isLoading = false,
            usageAccessGranted = granted,
            range = range,
            buckets = bucketMillis.mapIndexed { i, millis ->
                UsageBucket(label = bucketLabel(range, i), millis = millis)
            },
            topApps = topApps,
            totalScreenMillis = total,
            dailyAverageMillis = if (range.totalDays > 0) total / range.totalDays else 0L,
            gameRows = tallies.toRows(),
            totalPlays = totalPlays(tallies),
            totalMinutesEarned = totalMinutesEarned(tallies),
            favouriteGame = favourite,
        )
    }

    /**
     * Bucket edges as epoch millis, oldest first, ending at *now* rather than at midnight tonight —
     * the last bar is today so far, which is the number someone opening this screen wants to see.
     */
    private fun bucketBoundaries(range: StatsRange): List<Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays((range.totalDays - 1).toLong())
        val edges = (0 until range.bucketCount).map { i ->
            firstDay.plusDays((i * range.daysPerBucket).toLong())
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
        return edges + System.currentTimeMillis()
    }

    private fun bucketLabel(range: StatsRange, index: Int): String {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays((range.totalDays - 1).toLong())
        val day = firstDay.plusDays((index * range.daysPerBucket).toLong())
        return if (range.daysPerBucket == 1) {
            // Single letter keeps seven labels legible on a narrow phone: M T W T F S S.
            day.dayOfWeek.name.take(1)
        } else {
            "W${index + 1}"
        }
    }

    private fun List<GameTally>.toRows(): List<GameStatRow> = mapNotNull { tally ->
        GameType.entries.firstOrNull { it.name == tally.key }?.let { type ->
            GameStatRow(type = type, plays = tally.plays, minutesEarned = tally.minutesEarned)
        }
    }

    private companion object {
        const val TOP_APP_COUNT = 6
    }
}
