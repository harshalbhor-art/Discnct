package com.discnct.app.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.discnct.app.game.GameOutcome
import com.discnct.app.game.GamePool
import com.discnct.app.game.GameType
import com.discnct.app.service.BlockCooldown
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctTheme
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

private sealed interface Stage {
    data object Choice : Stage
    data class Playing(val type: GameType) : Stage
    data class Reward(val outcome: GameOutcome) : Stage
}

/**
 * The overlay-in-spirit block screen: an activity the accessibility service launches on
 * top of a blocked app. Dismissing without earning access always routes home rather than
 * finishing normally — finishing would just reveal the blocked app underneath.
 *
 * Two entry modes, and both start on the same [Stage.Choice] screen. Whole-app blocks (Level 2)
 * wall off the app; reel blocks (Level 1, [EXTRA_REEL_MODE]) wall off just the feed and hand back
 * a shorter window. A game is only ever started by tapping "Play a Game" — nothing auto-launches,
 * because a game appearing unbidden over an app reads as a malfunction, not as a blocker.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (targetPackage == null) {
            finish()
            return
        }
        val reelMode = intent.getBooleanExtra(EXTRA_REEL_MODE, false)
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        }.getOrDefault(targetPackage)

        setContent {
            DiscnctTheme {
                val gamesStore = remember { BlockerGamesStore(applicationContext) }
                // The store's own default is "every game", so seeding with it keeps the
                // "Play a Game" button from popping in a frame after the screen appears.
                val enabledGames by gamesStore.enabledGames
                    .collectAsStateWithLifecycle(initialValue = GameType.entries.toSet())

                var stage by remember { mutableStateOf<Stage>(Stage.Choice) }

                BackHandler {
                    // Backing out of a game means "not that, then" — return to the choice
                    // screen. Backing out of the choice screen means leaving the app.
                    if (stage is Stage.Playing) stage = Stage.Choice else goHome()
                }

                when (val s = stage) {
                    Stage.Choice -> BlockScreen(
                        appName = appLabel,
                        statusLabel = if (reelMode) "Reels Blocked" else "Blocked",
                    ) {
                        val holdMinutes = if (reelMode) REEL_HOLD_UNLOCK_MINUTES else HOLD_UNLOCK_MINUTES
                        Text(
                            text = if (reelMode) {
                                "Hold the button below for 30 seconds to keep scrolling $appLabel " +
                                    "anyway. The feed stays open for $holdMinutes minutes."
                            } else {
                                "Hold the button below for 30 seconds to open $appLabel anyway. " +
                                    "It'll stay unlocked for $holdMinutes minutes."
                            },
                            style = DiscnctType.bodySmall,
                            color = LocalDiscnctColors.current.textSecondary,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                        HoldToUnlockButton(
                            onUnlocked = {
                                BlockCooldown.allow(targetPackage, minutesToMs(holdMinutes))
                                finish()
                            },
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        if (enabledGames.isNotEmpty()) {
                            Text(
                                text = "— OR —",
                                style = DiscnctType.caption,
                                color = LocalDiscnctColors.current.textDisabled,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            PillButton(
                                label = "Play a Game to Earn More Time",
                                onClick = {
                                    GamePool.randomFrom(enabledGames)?.let { stage = Stage.Playing(it) }
                                },
                                variant = ButtonVariant.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PillButton(
                            label = "Go Home",
                            onClick = { goHome() },
                            variant = ButtonVariant.Ghost,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is Stage.Playing -> GameHost(
                        gameType = s.type,
                        onFinished = { outcome -> stage = Stage.Reward(outcome) },
                    )

                    is Stage.Reward -> GameRewardScreen(
                        appName = appLabel,
                        outcome = s.outcome,
                        onClaim = {
                            // Reel mode always grants at least a small window so a lost game can't
                            // trap the user in a game → block → game loop on their feed.
                            val minutes = if (reelMode) {
                                s.outcome.earnedMinutes.coerceAtLeast(REEL_MIN_MINUTES)
                            } else {
                                s.outcome.earnedMinutes
                            }
                            BlockCooldown.allow(targetPackage, minutesToMs(minutes))
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        finish()
    }

    private fun minutesToMs(minutes: Int): Long = minutes * 60_000L

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_REEL_MODE = "extra_reel_mode"
        private const val HOLD_UNLOCK_MINUTES = 5
        private const val REEL_HOLD_UNLOCK_MINUTES = 3
        private const val REEL_MIN_MINUTES = 2
    }
}
