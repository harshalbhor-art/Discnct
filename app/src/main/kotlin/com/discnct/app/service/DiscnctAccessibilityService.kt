package com.discnct.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.discnct.app.reel.BROWSER_URL_NODE_ID_MARKERS
import com.discnct.app.reel.REEL_BROWSER_PACKAGES
import com.discnct.app.reel.detectReelSurface
import com.discnct.app.ui.applist.BlockListStore
import com.discnct.app.ui.blockscreen.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The enforcement brain. Two jobs:
 *
 *  1. **Whole-app block (Level 1):** on a foreground-app change, if the app is on the block list
 *     and has no active [BlockCooldown], launch [BlockActivity] over it.
 *
 *  2. **Reel block (Level 2):** for apps on the reel-block list, watch content/scroll events and,
 *     when a Reels/Shorts feed is on screen (identified by [detectReelSurface] from the on-screen
 *     view ids or a browser's URL), bounce the user out with a global Back. This is surgical — the
 *     rest of the host app stays usable.
 */
class DiscnctAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var reelBlockedPackages: Set<String> = emptySet()

    /** Throttle: content/scroll events fire in bursts, so scan the tree at most this often. */
    private var lastReelScanAtMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            BlockListStore(applicationContext).blockedPackages.collect { blockedPackages = it }
        }
        serviceScope.launch {
            ReelBlockStore(applicationContext).reelBlockedPackages.collect { reelBlockedPackages = it }
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
        if (foregroundPackage !in reelBlockedPackages) return
        if (BlockCooldown.isAllowed(foregroundPackage)) return

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
            performGlobalAction(GLOBAL_ACTION_BACK)
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
