package com.discnct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.ui.applist.BlockerSettingsScreen
import com.discnct.app.ui.applist.ReelGamesScreen
import com.discnct.app.ui.applist.TotalDisconnectScreen
import com.discnct.app.ui.home.BottomNav
import com.discnct.app.ui.home.BottomTab
import com.discnct.app.ui.home.HomeScreen
import com.discnct.app.ui.home.Section
import com.discnct.app.ui.onboarding.FirstRunStore
import com.discnct.app.ui.onboarding.OnboardingScreen
import com.discnct.app.ui.onboarding.WelcomeDialog
import com.discnct.app.ui.settings.SettingsScreen
import com.discnct.app.ui.theme.DiscnctTheme
import com.discnct.app.ui.theme.ThemeMode
import com.discnct.app.ui.theme.ThemeStore
import kotlinx.coroutines.launch

/**
 * Single-activity host. Rather than gating the whole app behind permission onboarding, the home
 * screen is always the root — you can configure block lists before granting anything, and a banner
 * on Home nudges you to finish setup. Navigation is a plain screen enum with a system-back handler
 * (no nav library needed for four flat destinations).
 */
class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Home : Screen
        data object Settings : Screen
        data object Blocker : Screen
        data object ReelGames : Screen
        data object TotalDisconnect : Screen
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
                val firstRunStore = remember { FirstRunStore(applicationContext) }
                // Start "seen = true" so returning users never get a flash of the popup before the
                // stored value loads; a genuine first run flips it false a tick later and shows it.
                val welcomeSeen by firstRunStore.welcomeSeen.collectAsStateWithLifecycle(initialValue = true)

                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                // Home and Settings are the two top-level tabs and share the bottom nav bar;
                // every other screen is reached by drilling in and keeps its own back button.
                val isTopLevel = screen == Screen.Home || screen == Screen.Settings
                if (isTopLevel) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (screen) {
                                Screen.Home -> HomeScreen(
                                    onOpenSection = { section ->
                                        screen = when (section) {
                                            Section.Blocker -> Screen.Blocker
                                            Section.ReelGames -> Screen.ReelGames
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
                                else -> SettingsScreen()
                            }
                        }
                        BottomNav(
                            selected = if (screen == Screen.Home) BottomTab.Home else BottomTab.Settings,
                            onSelect = { tab ->
                                screen = if (tab == BottomTab.Home) Screen.Home else Screen.Settings
                            },
                        )
                    }
                } else {
                    when (screen) {
                        Screen.Blocker -> BlockerSettingsScreen(onBack = { screen = Screen.Home })
                        Screen.ReelGames -> ReelGamesScreen(onBack = { screen = Screen.Home })
                        Screen.TotalDisconnect -> TotalDisconnectScreen(onBack = { screen = Screen.Home })
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
            }
        }
    }
}
