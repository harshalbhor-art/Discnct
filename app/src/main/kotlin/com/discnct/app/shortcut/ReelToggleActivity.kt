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
 * Switching it *off* is the weakening move, and in the app that costs a PIN whenever Strict Mode
 * is on. This entry point is stricter than the in-app one: it's reached via an exported shortcut
 * activity and Quick Settings tile, both of which any other installed app can trigger too — not
 * just a tap inside Discnct's own UI — so turning off here refuses whenever a PIN has ever been
 * set, even if Strict Mode itself is currently switched off. Someone who's never set up a PIN gets
 * the same frictionless toggle as before; someone who has gets it protected everywhere it's
 * reachable from outside the app, not only where Strict Mode happens to be on right now. Switching
 * it back *on* never needs the PIN, here or anywhere else.
 */
suspend fun toggleReelBlocking(
    gamesStore: BlockerGamesStore,
    strictStore: StrictModeStore,
): ReelToggleResult {
    val enabled = gamesStore.reelBlockingEnabled.first()
    if (enabled && strictStore.hasPin.first()) {
        return ReelToggleResult(
            changed = false,
            message = "A PIN is set — turn the reel blocker off inside Discnct.",
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
