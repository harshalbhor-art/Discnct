package com.discnct.app.ui.stats

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.stats.AppUsageRow
import com.discnct.app.stats.GameStatRow
import com.discnct.app.stats.StatsRange
import com.discnct.app.stats.StatsViewModel
import com.discnct.app.stats.filledSegments
import com.discnct.app.stats.formatDurationMillis
import com.discnct.app.stats.formatMinutes
import com.discnct.app.stats.splitDuration
import com.discnct.app.ui.components.SegmentedControl
import com.discnct.app.ui.components.SegmentedProgressBar
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * The third top-level tab: what the blocking has actually amounted to.
 *
 * Two halves that don't share a source. The game record is ours and always available; screen time
 * comes from Usage Access and may be missing, in which case that half explains itself rather than
 * drawing an empty chart.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current
    val context = LocalContext.current

    // Screen time keeps accruing while the app is backgrounded, so the numbers are re-read on
    // every return rather than only when the tab is first built.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize().background(colors.black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.textDisplay)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 32.dp),
    ) {
        Text(text = "STATS", style = DiscnctType.displayMd, color = colors.textDisplay)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "What the blocking has added up to — the time you've won back from games, " +
                "and where the rest of it still goes.",
            style = DiscnctType.body,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(28.dp))
        SubHeader("Game progress")
        GameProgressCard(
            points = state.totalMinutesEarned,
            plays = state.totalPlays,
            favourite = state.favouriteGame?.displayName,
        )

        Spacer(modifier = Modifier.height(12.dp))
        GameBreakdownCard(rows = state.gameRows, totalPlays = state.totalPlays)

        Spacer(modifier = Modifier.height(32.dp))
        SubHeader("Screen time")

        if (!state.usageAccessGranted) {
            UsageAccessCard(onGrant = { openUsageAccessSettings(context) })
        } else {
            SegmentedControl(
                options = StatsRange.entries.map { it.label },
                selectedIndex = StatsRange.entries.indexOf(state.range),
                onSelect = { index -> viewModel.setRange(StatsRange.entries[index]) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            ScreenTimeCard(
                totalMillis = state.totalScreenMillis,
                dailyAverageMillis = state.dailyAverageMillis,
                rangeLabel = state.range.label,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card {
                UsageBarChart(buckets = state.buckets)
            }
            Spacer(modifier = Modifier.height(12.dp))
            TopAppsCard(rows = state.topApps, totalMillis = state.totalScreenMillis)
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = DiscnctType.label,
        color = LocalDiscnctColors.current.textSecondary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** The bordered surface every block on this screen sits in. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, DiscnctShapes.card)
            .padding(18.dp),
        content = content,
    )
}

/** Points, plays and favourite — the three numbers worth leading with. */
@Composable
private fun GameProgressCard(points: Int, plays: Int, favourite: String?) {
    val colors = LocalDiscnctColors.current
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$points", style = DiscnctType.displayLg, color = colors.accent)
            Text(
                text = " pts",
                style = DiscnctType.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "One point is one minute of app time you won back — ${formatMinutes(points)} in total.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile(value = "$plays", label = "Games played", modifier = Modifier.weight(1f))
            StatTile(
                value = favourite ?: "—",
                label = "Most played",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    Column(modifier = modifier) {
        Text(text = value, style = DiscnctType.subheading, color = colors.textDisplay, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label.uppercase(), style = DiscnctType.label, color = colors.textDisabled)
    }
}

/** Per-game rows, ordered most-played first so the list opens with what you actually reach for. */
@Composable
private fun GameBreakdownCard(rows: List<GameStatRow>, totalPlays: Int) {
    val colors = LocalDiscnctColors.current
    Card {
        if (totalPlays == 0) {
            Text(
                text = "No games played yet. Win one from a block screen and it'll show up here.",
                style = DiscnctType.bodySmall,
                color = colors.textDisabled,
            )
            return@Card
        }

        val ordered = rows.sortedWith(
            compareByDescending<GameStatRow> { it.plays }
                .thenByDescending { it.minutesEarned }
                .thenBy { it.type.displayName },
        )
        ordered.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.type.displayName,
                    style = DiscnctType.body,
                    color = if (row.plays > 0) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.plays > 0) {
                        "${row.plays}× · ${formatMinutes(row.minutesEarned)}"
                    } else {
                        "Never played"
                    },
                    style = DiscnctType.label,
                    color = colors.textDisabled,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedProgressBar(
                totalSegments = BREAKDOWN_SEGMENTS,
                filledCount = filledSegments(row.plays, totalPlays, BREAKDOWN_SEGMENTS),
                barHeight = 6.dp,
            )
            if (index != ordered.lastIndex) Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ScreenTimeCard(totalMillis: Long, dailyAverageMillis: Long, rangeLabel: String) {
    val colors = LocalDiscnctColors.current
    val (number, suffix) = splitDuration(totalMillis)
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = number, style = DiscnctType.displayLg, color = colors.textDisplay)
            Text(
                text = " $suffix",
                style = DiscnctType.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Total across the last $rangeLabel · ${formatDurationMillis(dailyAverageMillis)} a day on average.",
            style = DiscnctType.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun TopAppsCard(rows: List<AppUsageRow>, totalMillis: Long) {
    val colors = LocalDiscnctColors.current
    Card {
        Text(
            text = "WHERE IT WENT",
            style = DiscnctType.label,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (rows.isEmpty()) {
            Text(
                text = "Nothing recorded for this window.",
                style = DiscnctType.bodySmall,
                color = colors.textDisabled,
            )
            return@Card
        }

        rows.forEachIndexed { index, row ->
            AppUsageEntry(row = row, totalMillis = totalMillis)
            if (index != rows.lastIndex) Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AppUsageEntry(row: AppUsageRow, totalMillis: Long) {
    val colors = LocalDiscnctColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            bitmap = row.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(DiscnctShapes.cardCompact),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = DiscnctType.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDurationMillis(row.millis),
                    style = DiscnctType.label,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedProgressBar(
                totalSegments = BREAKDOWN_SEGMENTS,
                // Millis fit into an Int once reduced to minutes, which is all the bar needs.
                filledCount = filledSegments(
                    part = (row.millis / 60_000L).toInt(),
                    total = (totalMillis / 60_000L).toInt(),
                    totalSegments = BREAKDOWN_SEGMENTS,
                ),
                barHeight = 6.dp,
            )
        }
    }
}

@Composable
private fun UsageAccessCard(onGrant: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .border(1.dp, colors.accent, DiscnctShapes.card)
            .background(colors.accentSubtle)
            .clickable(onClick = onGrant)
            .padding(16.dp),
    ) {
        Text("USAGE ACCESS OFF", style = DiscnctType.label, color = colors.accent)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Screen time comes from Android's usage data, which Discnct can't read until " +
                "you allow it. Tap to open the setting.",
            style = DiscnctType.bodySmall,
            color = colors.textPrimary,
        )
    }
}

private fun openUsageAccessSettings(context: Context) {
    runCatching { context.startActivity(PermissionStatus.usageAccessSettingsIntent()) }
}

/** Ten blocks reads as a proportion without pretending to a precision the data doesn't have. */
private const val BREAKDOWN_SEGMENTS = 10
