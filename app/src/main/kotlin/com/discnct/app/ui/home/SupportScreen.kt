package com.discnct.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** Its own top-level tab rather than a card at the bottom of Home — same top-level pattern as
 * Stats and Settings, so it's reachable without scrolling past the three levels every time. */
@Composable
fun SupportScreen(modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.black)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 32.dp),
    ) {
        Text(text = "SUPPORT", style = DiscnctType.displayMd, color = colors.textDisplay)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Discnct doesn't run ads or sell data. If it's helped, this is the only ask.",
            style = DiscnctType.body,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        SupportCard()
    }
}
