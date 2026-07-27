package com.discnct.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/** The three top-level destinations. Sub-section screens (Reel Blocker, App Blocker, ...) are
 * reached by drilling in from Home and keep their own back button instead of living in this bar. */
enum class BottomTab { Home, Stats, Settings }

@Composable
fun BottomNav(selected: BottomTab, onSelect: (BottomTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.border)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(
            icon = Icons.Filled.Home,
            label = "Home",
            active = selected == BottomTab.Home,
            onClick = { onSelect(BottomTab.Home) },
        )
        NavItem(
            icon = StatsBarsIcon,
            label = "Stats",
            active = selected == BottomTab.Stats,
            onClick = { onSelect(BottomTab.Stats) },
        )
        NavItem(
            icon = Icons.Filled.Settings,
            label = "Settings",
            active = selected == BottomTab.Settings,
            onClick = { onSelect(BottomTab.Settings) },
        )
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    val tint = if (active) colors.textDisplay else colors.textDisabled
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            // Three tabs instead of two, so the touch targets give back some of their side room.
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label.uppercase(), style = DiscnctType.label, color = tint)
    }
}
