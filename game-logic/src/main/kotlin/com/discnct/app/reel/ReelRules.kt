package com.discnct.app.reel

/**
 * Pure, Android-free detection rules for short-form-video ("reel") surfaces.
 *
 * The strategy is the same one shipping blockers use: an accessibility service reads the
 * on-screen view tree and reports the resource-ids that are currently visible. A Reels/Shorts
 * feed always mounts a handful of uniquely named views (e.g. Instagram's `clips_tab`, YouTube's
 * `reel_recycler`). Matching those ids tells us the user is *inside* the reel feed specifically —
 * not just that the host app is open — so we can bounce them out of the feed without blocking the
 * whole app. For browsers there is no such view; instead we read the URL/omnibox text and match
 * known short-form paths (youtube.com/shorts, instagram.com/reels, ...).
 *
 * Everything here is a plain string table + pure function so it can be unit-tested on the JVM and
 * extended by anyone as apps rename their views — no device or emulator required.
 */

/** A short-form surface inside one host app. */
data class ReelPlatform(
    val packageName: String,
    val displayName: String,
    /**
     * Substrings of accessibility view resource-ids that are mounted **only while the full-screen
     * short-form viewer is actually open**. Empty means the whole app *is* short-form video
     * (e.g. TikTok), so any foreground window of it counts as a reel.
     */
    val reelNodeIdMarkers: List<String> = emptyList(),
    /**
     * Ids that merely *lead* to the feed: the Reels tab button in the bottom nav, a Shorts shelf on
     * the home timeline, a story ring. These sit on screens the user is allowed to be on, so
     * matching them is what makes a surgical reel block feel like a whole-app block. They are
     * listed here so a viewer marker can never match one of them by substring.
     */
    val entryPointNodeIdMarkers: List<String> = emptyList(),
) {
    val isWholeAppReels: Boolean get() = reelNodeIdMarkers.isEmpty()
}

/**
 * In-app reel surfaces. Markers are matched as substrings against full resource-ids like
 * `com.instagram.android:id/clips_viewer_view_pager`, so they keep working across minor app
 * updates that only add a prefix/suffix.
 *
 * The markers are deliberately *viewer*-scoped rather than *feature*-scoped. Instagram's bottom
 * navigation carries `clips_tab` on every single screen of the app and YouTube's home timeline
 * carries a `shorts_shelf`, so a rule that matched those would fire on the inbox, on search, on
 * a profile — everywhere — and Level 1 would stop being surgical. See [ReelPlatform.entryPointNodeIdMarkers].
 */
val REEL_PLATFORMS: List<ReelPlatform> = listOf(
    ReelPlatform(
        packageName = "com.instagram.android",
        displayName = "Instagram Reels",
        reelNodeIdMarkers = listOf(
            "clips_viewer",
            "root_clips_layout",
            "clips_video_container",
            "reel_item_toolbar_container",
            // Stories play in the same full-screen swipe-through viewer, which Level 1 covers too.
            "reel_viewer_",
        ),
        entryPointNodeIdMarkers = listOf(
            "clips_tab",
            "tab_icon",
            "reel_tray",
            "reel_ring",
        ),
    ),
    ReelPlatform(
        packageName = "com.google.android.youtube",
        displayName = "YouTube Shorts",
        reelNodeIdMarkers = listOf(
            "reel_recycler",
            "reel_watch_player",
            "reel_player_underlay",
            "shorts_video_container",
        ),
        entryPointNodeIdMarkers = listOf(
            "shorts_shelf",
            "shorts_tab",
            "pivot_bar",
        ),
    ),
    ReelPlatform(
        packageName = "com.facebook.katana",
        displayName = "Facebook Reels",
        reelNodeIdMarkers = listOf(
            "reels_viewer",
            "reels_player",
            "video_reels_viewer",
        ),
        entryPointNodeIdMarkers = listOf(
            "reels_tab",
            "reels_tray",
            "reels_unit",
        ),
    ),
    ReelPlatform(
        packageName = "com.snapchat.android",
        displayName = "Snapchat Spotlight",
        reelNodeIdMarkers = listOf(
            "spotlight_view_pager",
            "spotlight_player",
        ),
        entryPointNodeIdMarkers = listOf(
            "spotlight_tab",
            "spotlight_icon",
        ),
    ),
    // Whole-app short-form platforms (both TikTok package variants).
    ReelPlatform(packageName = "com.zhiliaoapp.musically", displayName = "TikTok"),
    ReelPlatform(packageName = "com.ss.android.ugc.trill", displayName = "TikTok"),
)

/** Browsers whose URL/omnibox text we read to catch short-form sites opened on the web. */
val REEL_BROWSER_PACKAGES: Set<String> = setOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.brave.browser",
    "com.opera.browser",
    "com.microsoft.emmx",
    "org.mozilla.firefox",
    "com.duckduckgo.mobile.android",
    "com.sec.android.app.sbrowser",
)

/** Resource-id substrings of the URL/omnibox field across the supported browsers. */
val BROWSER_URL_NODE_ID_MARKERS: List<String> = listOf(
    "url_bar",
    "omnibarTextInput",
    "mozac_browser_toolbar_url_view",
    "location_bar_edit_text",
    "url_field",
)

/** Short-form URL fragments matched (case-insensitively) against browser URL text. */
val BLOCKED_URL_MARKERS: List<String> = listOf(
    "youtube.com/shorts",
    "/shorts/",
    "instagram.com/reels",
    "instagram.com/reel/",
    "/reels/",
    "facebook.com/reel",
    "tiktok.com",
)

/** What we found on screen and why. [via] is the marker that matched — useful for logging/tests. */
data class ReelDetection(val platform: String, val via: String)

/**
 * True if [nodeId] is one of this platform's *entry points* — a tab button, a shelf, a story ring.
 * Being on a screen that merely offers a way into the feed is not being in the feed.
 */
fun ReelPlatform.isEntryPoint(nodeId: String): Boolean =
    entryPointNodeIdMarkers.any { nodeId.contains(it) }

/** The in-app platform for [packageName], or null if it isn't a known reel host. */
fun reelPlatformFor(packageName: String): ReelPlatform? =
    REEL_PLATFORMS.firstOrNull { it.packageName == packageName }

/** True if [packageName] can host reels either as an in-app surface or as a browser. */
fun isReelHostPackage(packageName: String): Boolean =
    reelPlatformFor(packageName) != null || packageName in REEL_BROWSER_PACKAGES

/**
 * Decide whether a reel/short is currently on screen.
 *
 * @param packageName the foreground app.
 * @param presentNodeIds every view resource-id currently visible in the active window.
 * @param browserUrlText the URL/omnibox text, when [packageName] is a browser (else null).
 * @return a [ReelDetection] when a reel surface is showing, or null.
 */
fun detectReelSurface(
    packageName: String,
    presentNodeIds: Set<String>,
    browserUrlText: String?,
): ReelDetection? {
    reelPlatformFor(packageName)?.let { platform ->
        if (platform.isWholeAppReels) return ReelDetection(platform.displayName, "whole-app")
        val marker = platform.reelNodeIdMarkers.firstOrNull { needle ->
            presentNodeIds.any { id -> id.contains(needle) && !platform.isEntryPoint(id) }
        }
        if (marker != null) return ReelDetection(platform.displayName, marker)
    }

    if (packageName in REEL_BROWSER_PACKAGES && !browserUrlText.isNullOrBlank()) {
        val url = browserUrlText.lowercase()
        val marker = BLOCKED_URL_MARKERS.firstOrNull { url.contains(it) }
        if (marker != null) return ReelDetection("Web short-form", marker)
    }

    return null
}
