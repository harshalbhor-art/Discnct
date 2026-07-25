package com.discnct.app.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.discnct.app.service.GamePlayCountStore
import com.discnct.app.service.playableGames
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctTheme
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.launch

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
 * Two entry modes. Whole-app blocks (Level 2) open the [Stage.Choice] screen: hold-to-unlock or
 * play a game. Reel blocks (Level 1, [EXTRA_REEL_MODE]) skip straight into a random game — winning
 * grants a few minutes of access to the app's reel feed, which is the whole deal for that level.
 *
 * Games carry daily play caps (see [GamePlayCountStore]), so the pool can run dry. When it does,
 * reel mode falls back to the same choice screen instead of dead-ending: hold-to-unlock is always
 * available, because a level whose only exit is a game you've used up isn't a blocker, it's a lock.
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
                val scope = rememberCoroutineScope()
                val gamesStore = remember { BlockerGamesStore(applicationContext) }
                val playCountStore = remember { GamePlayCountStore(applicationContext) }

                // Null until DataStore has actually answered. Deciding the opening stage off a
                // placeholder would risk launching a game the user has already used up today.
                val enabledGames by gamesStore.enabledGames.collectAsStateWithLifecycle(initialValue = null)
                val playsToday by playCountStore.playsToday.collectAsStateWithLifecycle(initialValue = null)
                val pool: Set<GameType>? = run {
                    val enabled = enabledGames ?: return@run null
                    val plays = playsToday ?: return@run null
                    playableGames(enabled, plays)
                }

                var stage by remember { mutableStateOf<Stage?>(null) }

                fun beginGame(type: GameType): Stage {
                    scope.launch { playCountStore.recordPlay(type) }
                    return Stage.Playing(type)
                }

                LaunchedEffect(pool) {
                    val available = pool ?: return@LaunchedEffect
                    if (stage != null) return@LaunchedEffect
                    val opening = if (reelMode) GamePool.randomFrom(available) else null
                    stage = if (opening != null) beginGame(opening) else Stage.Choice
                }

                BackHandler {
                    when (stage) {
                        // In reel mode there's no Choice screen to fall back to — backing out
                        // of the game means "not now", so leave the app entirely.
                        is Stage.Playing -> if (reelMode) goHome() else stage = Stage.Choice
                        else -> goHome()
                    }
                }

                when (val s = stage) {
                    null -> LoadingScrim()

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

                        val available = pool.orEmpty()
                        if (available.isEmpty()) {
                            Text(
                                text = "You've used up today's games. They come back tomorrow.",
                                style = DiscnctType.caption,
                                color = LocalDiscnctColors.current.textDisabled,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
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
                                    GamePool.randomFrom(available)?.let { stage = beginGame(it) }
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

/**
 * Covers the blocked app for the frame or two DataStore takes to answer. It has to be opaque:
 * a transparent placeholder would flash the very feed we're here to interrupt.
 */
@Composable
private fun LoadingScrim() {
    Box(modifier = Modifier.fillMaxSize().background(LocalDiscnctColors.current.black))
}
