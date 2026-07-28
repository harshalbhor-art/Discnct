package com.discnct.app.feed

/**
 * Pure, Android-free rules for finding the *scrolling feed* inside a social app.
 *
 * This is the second half of Level 1. The reel blocker bounces you out of a full-screen short-form
 * viewer; the feed blocker leaves you exactly where you are and covers the endless-scroll surface
 * with blank blocks, so DMs, search, posting and profiles keep working while the trap doesn't.
 *
 * Detection is a harder problem here than it is for reels, and it's worth being explicit about why.
 * A reel viewer mounts distinctively named views — `clips_viewer`, `reel_recycler` — so matching an
 * id is nearly enough on its own. A feed does not: the home timeline is a `recycler_view`, and so
 * is the DM list, the search grid and a profile's post list. Matching the container alone would
 * cover somebody's inbox, which is a far worse failure than missing a feed.
 *
 * So a match needs two independent things to agree:
 *
 *  1. a **container** big enough to be the main scrolling surface, and
 *  2. at least one visible **feed item** — ids that only the timeline's own rows carry.
 *
 * The second condition is what distinguishes the home feed from every other list in the app, and
 * it's why [FeedPlatform.itemMarkers] is not optional. Nothing is covered without one.
 */

/** A view visible on screen, with its on-screen bounds in pixels. */
data class FeedNode(
    val viewId: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
}

/** A rectangle in screen pixels. Used for both the screen itself and the region we cover. */
data class FeedRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/** The feed surface of one host app. */
data class FeedPlatform(
    val packageName: String,
    val displayName: String,
    /** Ids of the scrolling container that holds the timeline. Generic on purpose — see below. */
    val containerMarkers: List<String>,
    /**
     * Ids carried only by the timeline's own rows. Deliberately narrow: these are the whole reason
     * we can tell the home feed apart from the inbox, and a false match here covers the wrong list.
     */
    val itemMarkers: List<String>,
    /**
     * Top bar and bottom navigation. Never covered — they're how the user leaves the feed, and an
     * overlay that swallowed them would turn a feed block into an app block.
     */
    val chromeMarkers: List<String>,
)

/**
 * Instagram only, for now. Every other app needs its own ids read off a real device before it can
 * be added honestly, and a wrong guess here is much more visible than a wrong guess about reels.
 *
 * The stories tray is not listed separately: on Instagram it's the first row *inside* the feed
 * list, so covering the container covers the tray with it. That matches the intent — the tray is
 * the same pull as the feed and belongs behind the same switch.
 */
val FEED_PLATFORMS: List<FeedPlatform> = listOf(
    FeedPlatform(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        containerMarkers = listOf(
            "recycler_view",
            "feed_recycler",
            "main_feed",
            "list_view",
        ),
        itemMarkers = listOf(
            "row_feed_",
            "feed_item",
            "media_group",
        ),
        chromeMarkers = listOf(
            "action_bar",
            "tab_bar",
            "navigation_bar",
        ),
    ),
)

/**
 * How much of the screen the scrolling container has to occupy to be believable as *the* feed.
 * Low enough to survive a tall top bar and a bottom nav, high enough to reject the small lists that
 * sit inside a bottom sheet or a dialog.
 */
const val MIN_FEED_CONTAINER_COVERAGE = 0.35f

/**
 * A covered region smaller than this fraction of the screen isn't worth drawing. Below it we're
 * almost certainly looking at a collapsed or half-laid-out view mid-transition, and flashing a
 * block over a sliver of the screen reads as a rendering bug rather than a deliberate act.
 */
const val MIN_FEED_REGION_COVERAGE = 0.25f

/** What we decided to cover, and the id that convinced us. */
data class FeedDetection(val platform: String, val via: String, val region: FeedRegion)

/** The feed platform for [packageName], or null if we have no rules for it. */
fun feedPlatformFor(packageName: String): FeedPlatform? =
    FEED_PLATFORMS.firstOrNull { it.packageName == packageName }

/** True if [packageName] has a feed we know how to find. */
fun isFeedHostPackage(packageName: String): Boolean = feedPlatformFor(packageName) != null

/**
 * Work out which part of the screen to cover, or null to cover nothing.
 *
 * Returning null is the safe answer and the common one: every screen in the app that isn't the
 * timeline should land here. The caller is expected to take the overlay down whenever this returns
 * null, so a momentary miss costs a flicker rather than a stuck block.
 *
 * @param packageName the foreground app.
 * @param nodes every view currently visible on screen, with bounds.
 * @param screen the bounds of the app window.
 */
fun detectFeedSurface(
    packageName: String,
    nodes: List<FeedNode>,
    screen: FeedRegion,
): FeedDetection? {
    val platform = feedPlatformFor(packageName) ?: return null
    if (screen.area <= 0L) return null

    // Proof we're on the timeline and not some other list. Without this the rest is guesswork.
    val itemNode = nodes.firstOrNull { node ->
        platform.itemMarkers.any { node.viewId.contains(it) } && node.area > 0L
    } ?: return null

    val container = nodes
        .filter { node -> platform.containerMarkers.any { node.viewId.contains(it) } }
        .filter { it.area.toFloat() / screen.area >= MIN_FEED_CONTAINER_COVERAGE }
        .maxByOrNull { it.area }

    // Fall back to the items themselves when the container is named something we don't know. The
    // rows are visible and identified, so their extent is a fair description of the feed.
    val base = container?.let { FeedRegion(it.left, it.top, it.right, it.bottom) }
        ?: unionOfItems(nodes, platform)
        ?: return null

    val clipped = clipToChrome(base.intersect(screen), nodes, platform)
    if (clipped.isEmpty) return null
    if (clipped.area.toFloat() / screen.area < MIN_FEED_REGION_COVERAGE) return null

    return FeedDetection(platform.displayName, container?.viewId ?: itemNode.viewId, clipped)
}

private fun unionOfItems(nodes: List<FeedNode>, platform: FeedPlatform): FeedRegion? {
    val items = nodes.filter { node ->
        platform.itemMarkers.any { node.viewId.contains(it) } && node.area > 0L
    }
    if (items.isEmpty()) return null
    return FeedRegion(
        left = items.minOf { it.left },
        top = items.minOf { it.top },
        right = items.maxOf { it.right },
        bottom = items.maxOf { it.bottom },
    )
}

private fun FeedRegion.intersect(other: FeedRegion): FeedRegion = FeedRegion(
    left = maxOf(left, other.left),
    top = maxOf(top, other.top),
    right = minOf(right, other.right),
    bottom = minOf(bottom, other.bottom),
)

/**
 * Pull the region clear of the top bar and the bottom navigation.
 *
 * Chrome above the middle of the region pushes its top down; chrome below the middle pulls its
 * bottom up. Deciding by which half the bar sits in — rather than by name — means a bar we haven't
 * seen before still gets out of the way, and it keeps the rule to one line of reasoning.
 */
private fun clipToChrome(
    region: FeedRegion,
    nodes: List<FeedNode>,
    platform: FeedPlatform,
): FeedRegion {
    if (region.isEmpty) return region
    var top = region.top
    var bottom = region.bottom
    val middle = (region.top + region.bottom) / 2

    for (node in nodes) {
        if (platform.chromeMarkers.none { node.viewId.contains(it) }) continue
        if (node.height <= 0 || node.right <= region.left || node.left >= region.right) continue
        if (node.bottom <= middle) {
            top = maxOf(top, node.bottom)
        } else if (node.top >= middle) {
            bottom = minOf(bottom, node.top)
        }
    }

    return FeedRegion(region.left, top, region.right, bottom)
}
