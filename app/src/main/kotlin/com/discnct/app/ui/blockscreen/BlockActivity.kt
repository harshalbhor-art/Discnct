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
import com.discnct.app.game.GameOutcome
import com.discnct.app.game.GamePool
import com.discnct.app.game.GameType
import com.discnct.app.service.BlockCooldown
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
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (targetPackage == null) {
            finish()
            return
        }
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        }.getOrDefault(targetPackage)

        setContent {
            DiscnctTheme {
                var stage by remember { mutableStateOf<Stage>(Stage.Choice) }

                BackHandler {
                    when (stage) {
                        Stage.Choice -> goHome()
                        is Stage.Playing -> stage = Stage.Choice
                        is Stage.Reward -> goHome()
                    }
                }

                when (val s = stage) {
                    Stage.Choice -> BlockScreen(appName = appLabel, statusLabel = "Blocked") {
                        Text(
                            text = "Hold the button below for 30 seconds to open $appLabel anyway. " +
                                "It'll stay unlocked for 5 minutes.",
                            style = DiscnctType.bodySmall,
                            color = LocalDiscnctColors.current.textSecondary,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                        HoldToUnlockButton(
                            onUnlocked = {
                                BlockCooldown.allow(targetPackage, minutesToMs(HOLD_UNLOCK_MINUTES))
                                finish()
                            },
                        )
                        Spacer(modifier = Modifier.height(20.dp))
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
                            onClick = { stage = Stage.Playing(GamePool.random()) },
                            variant = ButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                            BlockCooldown.allow(targetPackage, minutesToMs(s.outcome.earnedMinutes))
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
        private const val HOLD_UNLOCK_MINUTES = 5
    }
}
