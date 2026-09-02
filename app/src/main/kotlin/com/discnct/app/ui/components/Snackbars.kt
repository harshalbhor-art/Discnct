package com.discnct.app.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * The one snackbar host for the whole app.
 *
 * There is no `Scaffold` anywhere in Discnct — [com.discnct.app.MainActivity] lays its screens out
 * by hand — so a composable deep in the tree has nowhere to put a message. Threading a
 * `SnackbarHostState` down through every screen signature to reach the one card that needs it would
 * be a lot of noise for one message, so it is handed down implicitly instead.
 *
 * Reading this without a provider is a programming error rather than something to paper over with a
 * silent default: a message that vanishes is worse than one that crashes in development.
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — wrap the content in DiscnctSnackbarHost")
}

/** Discnct-styled snackbar, so a message doesn't arrive in stock Material colours. */
@Composable
fun DiscnctSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            shape = DiscnctShapes.card,
            containerColor = colors.surfaceRaised,
            contentColor = colors.textPrimary,
        ) {
            androidx.compose.material3.Text(
                text = data.visuals.message,
                style = DiscnctType.body,
                color = colors.textPrimary,
            )
        }
    }
}
