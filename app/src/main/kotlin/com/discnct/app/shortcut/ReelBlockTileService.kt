package com.discnct.app.shortcut

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.discnct.app.R
import com.discnct.app.service.BlockerGamesStore
import com.discnct.app.ui.settings.StrictModeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for Level 1. Same switch as [ReelToggleActivity], but it shows the current
 * state on its face — which is what you want from a control you reach for while you're mid-scroll
 * in another app.
 *
 * Tiles aren't lifecycle owners, so the scope is created and cancelled by hand alongside the
 * service. The stores are cheap DataStore wrappers, so they're built per callback rather than held
 * across a service the system starts and stops at will.
 */
class ReelBlockTileService : TileService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(BlockerGamesStore(applicationContext).reelBlockingEnabled.first()) }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val gamesStore = BlockerGamesStore(applicationContext)
            val result = toggleReelBlocking(gamesStore, StrictModeStore(applicationContext))
            // A successful flip is obvious from the tile itself; a refusal isn't, so it gets words.
            if (!result.changed) {
                Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            }
            render(gamesStore.reelBlockingEnabled.first())
        }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_reel_blocker)
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
