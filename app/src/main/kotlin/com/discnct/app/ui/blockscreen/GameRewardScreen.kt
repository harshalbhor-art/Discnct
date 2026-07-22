package com.discnct.app.ui.blockscreen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discnct.app.game.GameOutcome
import com.discnct.app.ui.components.ButtonVariant
import com.discnct.app.ui.components.PillButton
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

@Composable
fun GameRewardScreen(appName: String, outcome: GameOutcome, onClaim: () -> Unit) {
    val colors = LocalDiscnctColors.current
    BlockScreen(appName = appName, statusLabel = "Time Earned") {
        Text(
            text = "+${outcome.earnedMinutes} MIN",
            style = DiscnctType.displayLg,
            color = colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = outcome.resultLabel,
            style = DiscnctType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        PillButton(
            label = "Unlock $appName",
            onClick = onClaim,
            variant = ButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
