package com.discnct.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.ui.applist.UsageStatsRepository
import com.discnct.app.ui.applist.formatUsage
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.Chip
import com.discnct.app.ui.components.DiscnctToggle
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.components.SegmentedControl
import com.discnct.app.ui.onboarding.PermissionStatus
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import com.discnct.app.ui.theme.ThemeMode
import com.discnct.app.ui.theme.ThemeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class UsageSummary(val totalMillis: Long, val topApps: List<Pair<String, Long>>)

private sealed interface PendingPinAction {
    data object SetUpPin : PendingPinAction
    data object ChangePin : PendingPinAction
    data object DisableStrictMode : PendingPinAction
    data class PauseFor(val minutes: Int) : PendingPinAction
}

/**
 * The app's control room — everything that isn't "block this app" lives here. Modeled on what
 * paid blockers (ScrollGuard, etc.) put behind their Pro tier: Strict Mode with a PIN, time-boxed
 * pausing, and a usage summary, all included here rather than gated. One exception is skipped on
 * purpose: nothing about payment/subscription, since Discnct doesn't have a paid tier.
 */
@Composable
fun SettingsScreen(onOpenFinancialApps: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalDiscnctColors.current
    val scope = rememberCoroutineScope()

    val themeStore = remember { ThemeStore(context.applicationContext) }
    val themeMode by themeStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

    val strictStore = remember { StrictModeStore(context.applicationContext) }
    val strictEnabled by strictStore.enabled.collectAsStateWithLifecycle(initialValue = false)
    val hasPin by strictStore.hasPin.collectAsStateWithLifecycle(initialValue = false)

    val pauseStore = remember { PauseStore(context.applicationContext) }
    val pausedUntil by pauseStore.pausedUntilMillis.collectAsStateWithLifecycle(initialValue = 0L)

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            delay(15_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val isPaused = pausedUntil > nowMillis

    var usageSummary by remember { mutableStateOf<UsageSummary?>(null) }
    LaunchedEffect(Unit) {
        val repo = UsageStatsRepository(context.applicationContext)
        val usage = repo.foregroundMillisByPackage().filterKeys { it != context.packageName }
        val pm = context.packageManager
        val top = usage.entries.sortedByDescending { it.value }.take(3).map { (pkg, millis) ->
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            label to millis
        }
        usageSummary = UsageSummary(totalMillis = usage.values.sum(), topApps = top)
    }

    var permissionsRefreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsRefreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingPinAction by remember { mutableStateOf<PendingPinAction?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 8.dp),
        ) {
            Text(text = "Settings", style = DiscnctType.heading, color = colors.textDisplay)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Everything that isn't blocking a specific app lives here.",
                style = DiscnctType.bodySmall,
                color = colors.textSecondary,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard(title = "Appearance") {
                SegmentedControl(
                    options = listOf("Light", "Dark", "System"),
                    selectedIndex = when (themeMode) {
                        ThemeMode.LIGHT -> 0
                        ThemeMode.DARK -> 1
                        ThemeMode.SYSTEM -> 2
                    },
                    onSelect = { index ->
                        val mode = when (index) { 0 -> ThemeMode.LIGHT; 1 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
                        scope.launch { themeStore.setThemeMode(mode) }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "Strict Mode") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require your PIN to turn off a blocker, disable reel blocking, or pause protection.",
                            style = DiscnctType.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    DiscnctToggle(
                        checked = strictEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                if (hasPin) scope.launch { strictStore.setEnabled(true) } else pendingPinAction = PendingPinAction.SetUpPin
                            } else {
                                pendingPinAction = PendingPinAction.DisableStrictMode
                            }
                        },
                        contentDescription = "Strict Mode",
                    )
                }
                if (hasPin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Change PIN",
                        style = DiscnctType.label,
                        color = colors.interactive,
                        modifier = Modifier
                            .clip(DiscnctShapes.pill)
                            .clickable { pendingPinAction = PendingPinAction.ChangePin }
                            .padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "Financial Apps") {
                Text(
                    text = "Discnct switches itself off while a banking, payments or authenticator " +
                        "app is open, and back on the moment you leave it. Check yours is listed — " +
                        "package names can't be guessed, so the built-in list won't cover every bank.",
                    style = DiscnctType.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                PillButton(
                    label = "Manage List",
                    onClick = onOpenFinancialApps,
                    variant = ButtonVariant.Secondary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "Pause Everything") {
                if (isPaused) {
                    val timeLabel = remember(pausedUntil) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(pausedUntil))
                    }
                    Text(
                        text = "Paused until $timeLabel",
                        style = DiscnctType.bodySmall,
                        color = colors.warning,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PillButton(
                        label = "Resume Now",
                        onClick = { scope.launch { pauseStore.resumeNow() } },
                        variant = ButtonVariant.Secondary,
                    )
                } else {
                    Text(
                        text = "For a planned exception — up to ${PauseStore.MAX_PAUSE_MINUTES} minutes, then blocking resumes on its own.",
                        style = DiscnctType.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(15, 30, 60).forEach { minutes ->
                            PillButton(
                                label = "${minutes}m",
                                variant = ButtonVariant.Secondary,
                                onClick = {
                                    if (strictEnabled) {
                                        pendingPinAction = PendingPinAction.PauseFor(minutes)
                                    } else {
                                        scope.launch { pauseStore.pauseFor(minutes) }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "This Week's Screen Time") {
                val summary = usageSummary
                when {
                    summary == null -> Text("Loading…", style = DiscnctType.bodySmall, color = colors.textDisabled)
                    summary.totalMillis <= 0L -> Text(
                        text = "No usage data yet — turn on Usage Access below to start tracking.",
                        style = DiscnctType.bodySmall,
                        color = colors.textDisabled,
                    )
                    else -> {
                        Text(
                            text = formatUsage(summary.totalMillis),
                            style = DiscnctType.displayMd,
                            color = colors.textDisplay,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        summary.topApps.forEach { (label, millis) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = label, style = DiscnctType.bodySmall, color = colors.textPrimary)
                                Text(text = formatUsage(millis), style = DiscnctType.label, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "Permissions") {
                key(permissionsRefreshTick) {
                    PermissionRow(
                        label = "Accessibility Service",
                        granted = PermissionStatus.isAccessibilityServiceEnabled(context),
                        onFix = { context.startActivity(PermissionStatus.accessibilitySettingsIntent()) },
                    )
                    PermissionRow(
                        label = "Usage Access",
                        granted = PermissionStatus.isUsageAccessGranted(context),
                        onFix = { context.startActivity(PermissionStatus.usageAccessSettingsIntent()) },
                    )
                    PermissionRow(
                        label = "Display Over Other Apps",
                        granted = PermissionStatus.isOverlayGranted(context),
                        onFix = { context.startActivity(PermissionStatus.overlaySettingsIntent(context)) },
                    )
                    PermissionRow(
                        label = "Battery Optimization",
                        granted = PermissionStatus.isIgnoringBatteryOptimizations(context),
                        onFix = { context.startActivity(PermissionStatus.batteryOptimizationIntent(context)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SettingsCard(title = "About") {
                Text(text = "Discnct v0.1.0", style = DiscnctType.bodySmall, color = colors.textSecondary)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    when (val action = pendingPinAction) {
        null -> Unit
        PendingPinAction.SetUpPin -> PinPromptDialog(
            mode = PinPromptMode.Create,
            title = "Set a Strict Mode PIN",
            onConfirmed = { pin ->
                scope.launch { strictStore.setPin(pin); strictStore.setEnabled(true) }
                pendingPinAction = null
            },
            onDismiss = { pendingPinAction = null },
        )
        PendingPinAction.ChangePin -> PinPromptDialog(
            mode = PinPromptMode.Create,
            title = "Change Strict Mode PIN",
            onConfirmed = { pin ->
                scope.launch { strictStore.setPin(pin) }
                pendingPinAction = null
            },
            onDismiss = { pendingPinAction = null },
        )
        PendingPinAction.DisableStrictMode -> PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to turn off Strict Mode",
            onConfirmed = {
                scope.launch { strictStore.setEnabled(false) }
                pendingPinAction = null
            },
            onDismiss = { pendingPinAction = null },
        )
        is PendingPinAction.PauseFor -> PinPromptDialog(
            mode = PinPromptMode.Verify(checkPin = { strictStore.verifyPin(it) }),
            title = "Enter PIN to pause for ${action.minutes} minutes",
            onConfirmed = {
                scope.launch { pauseStore.pauseFor(action.minutes) }
                pendingPinAction = null
            },
            onDismiss = { pendingPinAction = null },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalDiscnctColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiscnctShapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, DiscnctShapes.card)
            .padding(16.dp),
    ) {
        Text(text = title, style = DiscnctType.subheading, color = colors.textDisplay)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = DiscnctType.bodySmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (granted) {
            Chip(label = "On", active = true)
        } else {
            PillButton(label = "Fix", onClick = onFix, variant = ButtonVariant.Secondary)
        }
    }
}
