package com.discnct.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.ui.applist.BlockerSettingsScreen
import com.discnct.app.ui.applist.ReelGamesScreen
import com.discnct.app.ui.applist.TotalDisconnectScreen
import com.discnct.app.ui.home.HomeScreen
import com.discnct.app.ui.home.Section
import com.discnct.app.ui.onboarding.FirstRunStore
import com.discnct.app.ui.onboarding.OnboardingScreen
import com.discnct.app.ui.onboarding.WelcomeDialog
import com.discnct.app.ui.theme.DiscnctTheme
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
        data object Blocker : Screen
        data object ReelGames : Screen
        data object TotalDisconnect : Screen
        data object Permissions : Screen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiscnctTheme {
                val scope = rememberCoroutineScope()
                val firstRunStore = remember { FirstRunStore(applicationContext) }
                // Start "seen = true" so returning users never get a flash of the popup before the
                // stored value loads; a genuine first run flips it false a tick later and shows it.
                val welcomeSeen by firstRunStore.welcomeSeen.collectAsStateWithLifecycle(initialValue = true)

                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

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
                    )
                    Screen.Blocker -> BlockerSettingsScreen(onBack = { screen = Screen.Home })
                    Screen.ReelGames -> ReelGamesScreen(onBack = { screen = Screen.Home })
                    Screen.TotalDisconnect -> TotalDisconnectScreen(onBack = { screen = Screen.Home })
                    Screen.Permissions -> OnboardingScreen(onComplete = { screen = Screen.Home })
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
