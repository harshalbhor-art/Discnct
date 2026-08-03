package com.discnct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.ui.applist.AllowedAppsScreen
import com.discnct.app.ui.applist.AppBlockerScreen
import com.discnct.app.ui.applist.CoverImageScreen
import com.discnct.app.ui.applist.FinancialAppsScreen
import com.discnct.app.ui.applist.ReelBlockerScreen
import com.discnct.app.ui.applist.TotalDisconnectScreen
import com.discnct.app.ui.components.DiscnctSnackbarHost
import com.discnct.app.ui.components.LocalSnackbarHostState
import com.discnct.app.ui.home.BottomNav
import com.discnct.app.ui.home.BottomTab
import com.discnct.app.ui.home.HomeScreen
import com.discnct.app.ui.home.Section
import com.discnct.app.ui.onboarding.FirstRunStore
import com.discnct.app.ui.onboarding.OnboardingScreen
import com.discnct.app.ui.onboarding.WelcomeDialog
import com.discnct.app.ui.settings.SettingsScreen
import com.discnct.app.ui.stats.StatsScreen
import com.discnct.app.ui.theme.DiscnctTheme
import com.discnct.app.ui.theme.ThemeMode
import com.discnct.app.ui.theme.ThemeStore
import kotlinx.coroutines.launch

/**
 * Single-activity host. Rather than gating the whole app behind permission onboarding, the home
 * screen is always the root — you can configure block lists before granting anything, and a banner
 * on Home nudges you to finish setup. Navigation is a plain screen enum with a system-back handler
 * — still not enough destinations to justify a nav library.
 */
class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Home : Screen
        data object Stats : Screen
        data object Settings : Screen
        data object ReelBlocker : Screen
        data object AppBlocker : Screen

        /** Reached from a single app's row on [ReelBlocker], so it carries which app it's editing. */
        data class CoverImage(val packageName: String) : Screen
        data object TotalDisconnect : Screen
        data object AllowedApps : Screen
        data object FinancialApps : Screen
        data object Permissions : Screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeStore = remember { ThemeStore(applicationContext) }
            val themeMode by themeStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            DiscnctTheme(darkTheme = darkTheme) {
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val firstRunStore = remember { FirstRunStore(applicationContext) }
                // Start "seen = true" so returning users never get a flash of the popup before the
                // stored value loads; a genuine first run flips it false a tick later and shows it.
                val welcomeSeen by firstRunStore.welcomeSeen.collectAsStateWithLifecycle(initialValue = true)

                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                // Allowed Apps is the one screen reached from another section rather than from
                // Home, so back has to put you where you came from.
                BackHandler(enabled = screen != Screen.Home) {
                    screen = when (screen) {
                        Screen.AllowedApps -> Screen.TotalDisconnect
                        Screen.FinancialApps -> Screen.Settings
                        is Screen.CoverImage -> Screen.ReelBlocker
                        else -> Screen.Home
                    }
                }

                // Home, Stats and Settings are the top-level tabs and share the bottom nav bar;
                // every other screen is reached by drilling in and keeps its own back button.
                val isTopLevel = screen == Screen.Home || screen == Screen.Stats || screen == Screen.Settings

                // Everything sits in one Box so the snackbar has somewhere to land. It is provided
                // rather than passed because the only thing that raises one — the support card — is
                // several screens down, and threading a host state through every signature to reach
                // it would cost more than it explains.
                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isTopLevel) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    when (screen) {
                                        Screen.Home -> HomeScreen(
                                            onOpenSection = { section ->
                                                screen = when (section) {
                                                    Section.ReelBlocker -> Screen.ReelBlocker
                                                    Section.AppBlockerGames -> Screen.AppBlocker
                                                    Section.TotalDisconnect -> Screen.TotalDisconnect
                                                }
                                            },
                                            onOpenPermissions = { screen = Screen.Permissions },
                                            darkTheme = darkTheme,
                                            onToggleTheme = {
                                                val next = if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                                                scope.launch { themeStore.setThemeMode(next) }
                                            },
                                        )
                                        Screen.Stats -> StatsScreen()
                                        else -> SettingsScreen(
                                            onOpenFinancialApps = { screen = Screen.FinancialApps },
                                        )
                                    }
                                }
                                BottomNav(
                                    selected = when (screen) {
                                        Screen.Stats -> BottomTab.Stats
                                        Screen.Settings -> BottomTab.Settings
                                        else -> BottomTab.Home
                                    },
                                    onSelect = { tab ->
                                        screen = when (tab) {
                                            BottomTab.Home -> Screen.Home
                                            BottomTab.Stats -> Screen.Stats
                                            BottomTab.Settings -> Screen.Settings
                                        }
                                    },
                                )
                            }
                        } else {
                            when (screen) {
                                Screen.ReelBlocker -> ReelBlockerScreen(
                                    onBack = { screen = Screen.Home },
                                    onOpenCoverImage = { screen = Screen.CoverImage(it) },
                                )
                                is Screen.CoverImage -> CoverImageScreen(
                                    packageName = (screen as Screen.CoverImage).packageName,
                                    onBack = { screen = Screen.ReelBlocker },
                                )
                                Screen.AppBlocker -> AppBlockerScreen(onBack = { screen = Screen.Home })
                                Screen.TotalDisconnect -> TotalDisconnectScreen(
                                    onBack = { screen = Screen.Home },
                                    onOpenAllowedApps = { screen = Screen.AllowedApps },
                                )
                                Screen.AllowedApps -> AllowedAppsScreen(onBack = { screen = Screen.TotalDisconnect })
                                Screen.FinancialApps -> FinancialAppsScreen(onBack = { screen = Screen.Settings })
                                Screen.Permissions -> OnboardingScreen(onComplete = { screen = Screen.Home })
                                else -> Unit
                            }
                        }

                        if (!welcomeSeen) {
                            WelcomeDialog(
                                onDismiss = { scope.launch { firstRunStore.setWelcomeSeen() } },
                                onSetUpPermissions = {
                                    scope.launch { firstRunStore.setWelcomeSeen() }
                                    screen = Screen.Permissions
                                },
                            )
                        }

                        // Above the bottom nav rather than behind it, so a message is never half
                        // hidden by the tab bar on the screens that have one.
                        DiscnctSnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 80.dp),
                        )
                    }
                }
            }
        }
    }
}
