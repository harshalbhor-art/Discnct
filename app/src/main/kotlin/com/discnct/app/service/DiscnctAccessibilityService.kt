package com.discnct.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.discnct.app.reel.BROWSER_URL_NODE_ID_MARKERS
import com.discnct.app.reel.BounceAction
import com.discnct.app.reel.BounceState
import com.discnct.app.reel.REEL_BROWSER_PACKAGES
import com.discnct.app.reel.detectReelSurface
import com.discnct.app.reel.nextBounce
import com.discnct.app.ui.applist.BlockListStore
import com.discnct.app.ui.blockscreen.BlockActivity
import com.discnct.app.ui.settings.PauseStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The enforcement brain. Two jobs:
 *
 *  1. **Whole-app block (Levels 2 and 3):** on a foreground-app change, if the app is on the block
 *     list and has no active [BlockCooldown], launch [BlockActivity] over it.
 *
 *  2. **Reel block (Level 1):** for apps on the reel-block list, watch content/scroll events and,
 *     when a Reels/Shorts feed is on screen (identified by [detectReelSurface] from the on-screen
 *     view ids or a browser's URL), bounce the user out with a global Back. This is surgical — the
 *     rest of the host app stays usable, and there is nothing to dismiss or play through: the feed
 *     simply won't stay open while the blocker is on.
 */
class DiscnctAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var reelBlockedPackages: Set<String> = emptySet()

    /** Master switch for the reel blocker (Level 1). When false, reel scanning is skipped entirely. */
    @Volatile
    private var reelBlockingEnabled: Boolean = true

    /** Epoch millis until which all blocking is suspended (Settings > Pause Everything). 0 = not paused. */
    @Volatile
    private var pausedUntilMillis: Long = 0L

    /** Throttle: content/scroll events fire in bursts, so scan the tree at most this often. */
    private var lastReelScanAtMs = 0L

    /** Rate-limiting state for the Level 1 Back bounce. See [nextBounce]. */
    @Volatile
    private var bounceState: BounceState = BounceState()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            BlockListStore(applicationContext).blockedPackages.collect { blockedPackages = it }
        }
        serviceScope.launch {
            ReelBlockStore(applicationContext).reelBlockedPackages.collect { reelBlockedPackages = it }
        }
        serviceScope.launch {
            BlockerGamesStore(applicationContext).reelBlockingEnabled.collect { reelBlockingEnabled = it }
        }
        serviceScope.launch {
            PauseStore(applicationContext).pausedUntilMillis.collect { pausedUntilMillis = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                maybeBlockWholeApp(foregroundPackage)
                maybeBlockReels(foregroundPackage)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                maybeBlockReels(foregroundPackage)
            }
        }
    }

    private fun maybeBlockWholeApp(foregroundPackage: String) {
        if (System.currentTimeMillis() < pausedUntilMillis) return
        if (foregroundPackage !in blockedPackages) return
        if (BlockCooldown.isAllowed(foregroundPackage)) return

        startActivity(
            Intent(this, BlockActivity::class.java).apply {
                putExtra(BlockActivity.EXTRA_PACKAGE_NAME, foregroundPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun maybeBlockReels(foregroundPackage: String) {
        if (System.currentTimeMillis() < pausedUntilMillis) return
        if (!reelBlockingEnabled) return
        if (foregroundPackage !in reelBlockedPackages) return
        // Deliberately not checking BlockCooldown: that's time earned at Level 2 to open the *app*,
        // and reels are no longer something you can earn your way into. Honouring it here would
        // mean winning a game to open Instagram quietly unlocked its Reels tab too.

        val now = SystemClock.elapsedRealtime()
        if (now - lastReelScanAtMs < REEL_SCAN_THROTTLE_MS) return
        lastReelScanAtMs = now

        val root = rootInActiveWindow ?: return
        val nodeIds = HashSet<String>()
        var browserUrl: String? = null
        val isBrowser = foregroundPackage in REEL_BROWSER_PACKAGES

        try {
            collectScreen(root, nodeIds, isBrowser) { url -> browserUrl = url }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }

        if (detectReelSurface(foregroundPackage, nodeIds, browserUrl) != null) {
            bounceOutOfReels()
        }
    }

    /**
     * Level 1's entire enforcement: pop the feed off the stack and let the user land on whatever
     * screen was underneath it. Nothing is drawn over the app, because a full-screen block activity
     * on top of Instagram is visually indistinguishable from blocking Instagram — which is exactly
     * what this level is not. [nextBounce] handles the repetition and the dead-end cases.
     */
    private fun bounceOutOfReels() {
        val decision = nextBounce(bounceState, SystemClock.elapsedRealtime())
        bounceState = decision.state
        when (decision.action) {
            BounceAction.None -> Unit
            BounceAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            BounceAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /**
     * Bounded breadth-first walk of the active window collecting every view resource-id (and, for a
     * browser, the URL/omnibox text). Bounded so a deep tree can't make each scroll event expensive.
     */
    private fun collectScreen(
        root: AccessibilityNodeInfo,
        outIds: MutableSet<String>,
        isBrowser: Boolean,
        onBrowserUrl: (String) -> Unit,
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null) {
                outIds.add(id)
                if (isBrowser && BROWSER_URL_NODE_ID_MARKERS.any { id.contains(it) }) {
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let(onBrowserUrl)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private companion object {
        const val REEL_SCAN_THROTTLE_MS = 400L
        const val MAX_NODES = 600
    }
}
