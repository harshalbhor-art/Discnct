package com.discnct.app.shortcut

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.settings.StrictModeStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What a shortcut tap did, and the line to show for it. */
data class ReelToggleResult(val changed: Boolean, val message: String)

/**
 * Flips the reel blocker and describes what happened.
 *
 * Strict Mode is honoured: switching the blocker *off* is the weakening move, and in the app it
 * costs a PIN. A shortcut that skipped that check would be a way around Strict Mode, so it refuses
 * instead. Switching it back *on* never needs the PIN, here or anywhere else.
 */
suspend fun toggleReelBlocking(
    gamesStore: BlockerGamesStore,
    strictStore: StrictModeStore,
): ReelToggleResult {
    val enabled = gamesStore.reelBlockingEnabled.first()
    if (enabled && strictStore.enabled.first()) {
        return ReelToggleResult(
            changed = false,
            message = "Strict Mode is on — turn the reel blocker off inside Discnct.",
        )
    }
    gamesStore.setReelBlockingEnabled(!enabled)
    return ReelToggleResult(
        changed = true,
        message = if (enabled) "Reel blocker paused" else "Reel blocker on",
    )
}

/**
 * The launcher shortcut's target (long-press the Discnct icon → "Reel Blocker On/Off"), and the
 * one-tap way to flip Level 1 without opening the app.
 *
 * It draws nothing: the activity exists only to run the toggle and report the new state as a
 * toast, so tapping the shortcut from another app doesn't yank you out of what you were doing.
 * The Quick Settings tile ([ReelBlockTileService]) is the same switch with a live label.
 */
class ReelToggleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val result = toggleReelBlocking(
                gamesStore = BlockerGamesStore(applicationContext),
                strictStore = StrictModeStore(applicationContext),
            )
            Toast.makeText(applicationContext, result.message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
