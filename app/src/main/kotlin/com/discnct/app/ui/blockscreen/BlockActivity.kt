package com.discnct.app.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.discnct.app.service.BlockCooldown
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctTheme
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

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

        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            DiscnctTheme {
                BlockScreen(appName = appLabel, statusLabel = "Blocked") {
                    Text(
                        text = "Hold the button below for 30 seconds to open $appLabel anyway. " +
                            "It'll stay unlocked for 5 minutes.",
                        style = DiscnctType.bodySmall,
                        color = LocalDiscnctColors.current.textSecondary,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                    HoldToUnlockButton(
                        onUnlocked = {
                            BlockCooldown.allow(targetPackage, COOLDOWN_MS)
                            finish()
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PillButton(
                        label = "Go Home",
                        onClick = { goHome() },
                        variant = ButtonVariant.Ghost,
                        modifier = Modifier.fillMaxWidth(),
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

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val COOLDOWN_MS = 5 * 60_000L
    }
}
