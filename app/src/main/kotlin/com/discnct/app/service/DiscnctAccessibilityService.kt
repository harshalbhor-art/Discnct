package com.discnct.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.discnct.app.feed.FeedNode
import com.discnct.app.feed.FeedRegion
import com.discnct.app.feed.detectFeedSurface
import com.discnct.app.feed.isFeedHostPackage
import com.discnct.app.reel.BROWSER_URL_NODE_ID_MARKERS
import com.discnct.app.reel.BounceAction
import com.discnct.app.reel.BounceState
import com.discnct.app.reel.REEL_BROWSER_PACKAGES
import com.discnct.app.reel.ReelNode
import com.discnct.app.reel.detectReelSurface
import com.discnct.app.reel.nextBounce
import com.discnct.app.ui.applist.BlockListStore
import com.discnct.app.ui.blockscreen.BlockActivity
import com.discnct.app.ui.settings.PauseStore
import com.discnct.app.ui.theme.ThemeMode
import com.discnct.app.ui.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The enforcement brain. Three jobs:
 *
 *  1. **Whole-app block (Levels 2 and 3):** on a foreground-app change, if the app is on the block
 *     list and has no active [BlockCooldown], launch [BlockActivity] over it.
 *
 *  2. **Reel block (Level 1):** for apps on the reel-block list, watch content/scroll events and,
 *     when a Reels/Shorts feed is on screen (identified by [detectReelSurface] from the on-screen
 *     view ids or a browser's URL), bounce the user out with a global Back.
 *
 *  3. **Feed cover (Level 1):** for apps on the feed-block list, find the scrolling timeline with
 *     [detectFeedSurface] and lay a solid pane over it with [FeedOverlayController]. Nothing is
 *     hidden — we can't touch another app's views — the feed is covered where it sits, so the top
 *     bar and the bottom navigation both keep working. The cover also holds audio focus, because a
 *     reel underneath would otherwise keep playing where nobody can reach its controls.
 *
 * Both Level 1 blocks are surgical: the rest of the host app stays usable, and neither has anything
 * to dismiss or play through.
 */
class DiscnctAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val overlay by lazy { FeedOverlayController(this) }

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var reelBlockedPackages: Set<String> = emptySet()

    @Volatile
    private var feedBlockedPackages: Set<String> = emptySet()

    private val coverImages by lazy { CoverImageStore(applicationContext) }

    /** Each app's chosen cover picture, unresolved. Empty entries fall back to the locale default. */
    @Volatile
    private var coverKeys: Map<String, String> = emptyMap()

    /** Master switch for the reel blocker (Level 1). When false, reel scanning is skipped entirely. */
    @Volatile
    private var reelBlockingEnabled: Boolean = true

    /** Epoch millis until which all blocking is suspended (Settings > Pause Everything). 0 = not paused. */
    @Volatile
    private var pausedUntilMillis: Long = 0L

    /** Throttle: content/scroll events fire in bursts, so scan the tree at most this often. */
    private var lastScanAtMs = 0L

    /** The app the last event came from, so a switch can reset the per-app guard state. */
    @Volatile
    private var lastForegroundPackage: String? = null

    /** The user's light/dark choice, which is the only thing the cover's colour follows. */
    @Volatile
    private var themeMode: ThemeMode = ThemeMode.SYSTEM

    /** Apps Discnct stands down for entirely while they are in front. See [FinanceAppStore]. */
    @Volatile
    private var financeExemptPackages: Set<String> = emptySet()

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
            FeedBlockStore(applicationContext).feedBlockedPackages.collect {
                feedBlockedPackages = it
                // Turning a switch off has to take the overlay down straight away. Waiting for the
                // next accessibility event would leave the block sitting there over a feed the user
                // has just un-blocked, with nothing they can do to shift it.
                hideOverlaySoon()
            }
        }
        serviceScope.launch {
            BlockerGamesStore(applicationContext).reelBlockingEnabled.collect { reelBlockingEnabled = it }
        }
        serviceScope.launch {
            ThemeStore(applicationContext).themeMode.collect { themeMode = it }
        }
        serviceScope.launch {
            // No hideOverlaySoon() here: picking a new picture should swap it under a cover that's
            // already up, not blink the cover off and on. The next scan repaints it.
            coverImages.coverKeys.collect { coverKeys = it }
        }
        serviceScope.launch {
            FinanceAppStore(applicationContext).exemptPackages.collect {
                financeExemptPackages = it
                // Un-exempting an app the user is looking at right now has to bite immediately;
                // exempting one has to lift the cover for the same reason.
                hideOverlaySoon()
            }
        }
        serviceScope.launch {
            PauseStore(applicationContext).pausedUntilMillis.collect {
                pausedUntilMillis = it
                hideOverlaySoon()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) return

        // Banking, payments and the authenticators in front of them: Discnct gets out of the way
        // completely, and only for as long as one of them is what the user is looking at. There is
        // no timer and nothing persisted, so the exemption cannot outlive the app that earned it —
        // the next event from anything else puts every block straight back.
        if (foregroundPackage in financeExemptPackages) {
            standDownFor(foregroundPackage)
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                maybeBlockWholeApp(foregroundPackage)
                guardLevelOneSurfaces(foregroundPackage)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                guardLevelOneSurfaces(foregroundPackage)
            }
        }
    }

    /**
     * Suspend everything while a financial app is in front.
     *
     * The per-app guard state is reset the same way a real app switch resets it, so coming back to
     * Instagram from the banking app doesn't find a half-used throttle window or a stale bounce
     * backoff — arriving from an exempt app must be indistinguishable from arriving from any other.
     */
    private fun standDownFor(foregroundPackage: String) {
        lastForegroundPackage = foregroundPackage
        lastScanAtMs = 0L
        bounceState = BounceState()
        hideOverlay()
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

    /**
     * Both Level 1 blocks, off a single walk of the view tree. They need the same information —
     * which views are on screen and where — and scanning twice per event for it would double the
     * cost of every scroll.
     */
    private fun guardLevelOneSurfaces(foregroundPackage: String) {
        // A different app in front is a clean slate. Both pieces of state carried across a switch
        // were silently disarming the blocker: a 30s backoff left over from a run of failed bounces
        // in *some other app* meant coming back to Instagram found the reel block asleep, and a
        // half-used throttle window could swallow the one scan a resumed app gets before it settles.
        if (foregroundPackage != lastForegroundPackage) {
            lastForegroundPackage = foregroundPackage
            lastScanAtMs = 0L
            bounceState = BounceState()
        }

        val paused = System.currentTimeMillis() < pausedUntilMillis

        // Deliberately not checking BlockCooldown for either: that's time earned at Level 2 to open
        // the *app*, and neither reels nor the feed is something you can earn your way into.
        // Honouring it here would mean winning a game to open Instagram quietly unlocked both.
        val guardReels = !paused &&
            reelBlockingEnabled &&
            foregroundPackage in reelBlockedPackages
        val guardFeed = !paused &&
            foregroundPackage in feedBlockedPackages &&
            isFeedHostPackage(foregroundPackage)

        if (!guardReels && !guardFeed) {
            hideOverlay()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAtMs < SCAN_THROTTLE_MS) return

        // Only spend the throttle window on a scan that actually happened. The window right after a
        // resume is exactly when the root is most likely to be missing, and burning the slot there
        // would drop the next 400ms of events too — long enough for a reel to start playing.
        val root = rootInActiveWindow ?: return
        lastScanAtMs = now
        val scanned = ArrayList<ScannedNode>()
        val window = Rect()
        var browserUrl: String? = null
        val isBrowser = foregroundPackage in REEL_BROWSER_PACKAGES

        try {
            root.getBoundsInScreen(window)
            collectScreen(root, scanned, isBrowser) { url -> browserUrl = url }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }

        if (guardFeed) {
            updateFeedOverlay(foregroundPackage, scanned, window)
        } else {
            hideOverlay()
        }

        if (guardReels) {
            val windowArea = window.width().toLong() * window.height().toLong()
            val reelNodes = scanned.map { ReelNode(it.id, it.coverageOf(window, windowArea)) }
            if (detectReelSurface(foregroundPackage, reelNodes, browserUrl) != null) {
                bounceOutOfReels()
            }
        }
    }

    private fun updateFeedOverlay(
        foregroundPackage: String,
        scanned: List<ScannedNode>,
        window: Rect,
    ) {
        val screen = FeedRegion(window.left, window.top, window.right, window.bottom)
        val feedNodes = scanned.map { FeedNode(it.id, it.left, it.top, it.right, it.bottom) }
        val detection = detectFeedSurface(foregroundPackage, feedNodes, screen)
        if (detection == null) {
            hideOverlay()
            return
        }
        overlay.show(
            detection,
            coverToneFor(themeMode, this),
            coverImages.resolve(coverKeys[foregroundPackage]),
        )
    }

    /** Take the cover down. Nothing else to unwind — the cover holds no captured state. */
    private fun hideOverlay() {
        overlay.hide()
    }

    /**
     * Level 1's reel enforcement: pop the feed off the stack and let the user land on whatever
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

    /** [WindowManager] work has to happen on the main thread; store collectors don't run there. */
    private fun hideOverlaySoon() {
        mainHandler.post { hideOverlay() }
    }

    /** One visible view: its id and where it sits on screen. */
    private class ScannedNode(
        val id: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        /** Share of the window this view covers, clipped to it. */
        fun coverageOf(window: Rect, windowArea: Long): Float {
            if (windowArea <= 0L) return 0f
            val w = (minOf(right, window.right) - maxOf(left, window.left)).coerceAtLeast(0)
            val h = (minOf(bottom, window.bottom) - maxOf(top, window.top)).coerceAtLeast(0)
            return w.toLong() * h.toLong() / windowArea.toFloat()
        }
    }

    /**
     * Bounded breadth-first walk of the active window collecting every view that is **actually
     * being shown**, with its on-screen bounds (and, for a browser, the URL/omnibox text). Bounded
     * so a deep tree can't make each scroll event expensive.
     *
     * The visibility filter is not an optimisation, it is what keeps Level 1 from behaving like a
     * whole-app block: host apps keep the reel fragment attached after the user navigates away from
     * it, so its ids stay reachable from the root on every other screen in the app. We walk into
     * hidden subtrees anyway rather than pruning at them — a scrolled-out container can report
     * itself invisible while the rows inside it are on screen — and simply don't record what we
     * can't see.
     */
    private fun collectScreen(
        root: AccessibilityNodeInfo,
        outNodes: MutableList<ScannedNode>,
        isBrowser: Boolean,
        onBrowserUrl: (String) -> Unit,
    ) {
        val bounds = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (node.isVisibleToUser) {
                node.getBoundsInScreen(bounds)
                // Views with no id are kept too. They match no marker and so can't trigger
                // anything, but the feed cover finds the bars it must stay clear of by shape, and
                // an unnamed view is as good a rectangle as any other.
                outNodes.add(
                    ScannedNode(id ?: "", bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
                if (id != null && isBrowser && BROWSER_URL_NODE_ID_MARKERS.any { id.contains(it) }) {
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let(onBrowserUrl)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // dispose, not hide: a fade needs something alive to finish it and remove the window.
        overlay.dispose()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        overlay.dispose()
        serviceJob.cancel()
    }

    private companion object {
        const val SCAN_THROTTLE_MS = 400L

        /**
         * Raised from the reel-only days: feed rows sit deeper than a reel player's container, and
         * a budget that stopped short of them would read as "no feed here" and take the block down.
         */
        const val MAX_NODES = 900
    }
}
