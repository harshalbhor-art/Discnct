package com.discnct.app.feed

/**
 * Pure, Android-free rules for finding the *scrolling feed* inside a social app.
 *
 * This is the second half of Level 1. The reel blocker bounces you out of a full-screen short-form
 * viewer; the feed cover leaves you exactly where you are and lays frosted glass over the
 * endless-scroll surface, so DMs, search, posting and profiles keep working while the trap doesn't.
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
 *
 * What comes back is one rectangle: everything between the top bar and the bottom navigation. The
 * point of this level is minimum access, not no access — the logo, the new-post and notification
 * buttons and the whole bottom navigation stay live, because they're how you post and how you
 * leave. Only the part built to be scrolled forever gets covered, and it gets covered whole:
 * stories are the same infinite surface as the timeline under them.
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
     * overlay that swallowed them would turn a feed cover into an app block.
     */
    val chromeMarkers: List<String>,
    /**
     * The horizontal stories strip. It gets covered along with everything else; the reason it's
     * named at all is [clipToBars], which must not mistake it for a bar and start the cover below it.
     */
    val storyTrayMarkers: List<String>,
)

/**
 * Instagram only, for now. Every other app needs its own ids read off a real device before it can
 * be added honestly, and a wrong guess here is much more visible than a wrong guess about reels.
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
            "title_logo",
            "toolbar",
        ),
        storyTrayMarkers = listOf(
            "reel_tray",
            "tray_recycler",
            "stories_tray",
            "story_tray",
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
const val MIN_FEED_REGION_COVERAGE = 0.20f

/**
 * Bands at the top and bottom of the screen that are never covered **when we couldn't find the bar
 * itself**.
 *
 * These are a fallback, not an extra constraint, and the distinction is what fixes a live strip of
 * feed showing under the cover: a nav bar measures about 150px on a 1080×2400 screen, this band is
 * 192px, and applying both left 42px of real timeline scrolling away below the glass. When a bar is
 * recognised — by id or by shape — its own edge is exact and the band has nothing to add. The band
 * only speaks up when nothing was recognised at all, which is the case it was written for: an id we
 * don't know is a bar we don't clip, and a covered bottom navigation makes the app unusable.
 */
const val MIN_TOP_CHROME_FRACTION = 0.08f
const val MIN_BOTTOM_CHROME_FRACTION = 0.08f

/**
 * A bar is a view that runs nearly the full width, is short, and hugs the top or bottom edge.
 *
 * Recognising bars by that shape instead of by name is what makes the bottom navigation safe from
 * an Instagram rename. The height cap is the load-bearing one: an action bar or a nav bar is about
 * 50dp, while the stories strip — the next-widest thing up there — is more than twice that, so the
 * cap tells them apart without needing to know what either is called.
 */
const val BAR_MIN_WIDTH_FRACTION = 0.8f
const val BAR_MAX_HEIGHT_FRACTION = 0.10f
const val BAR_EDGE_ZONE_FRACTION = 0.15f

/**
 * What we decided to cover: one rectangle, from under the top bar to above the bottom navigation.
 *
 * @param feedRegion the covered area, already clear of both bars and clipped to the screen.
 */
data class FeedDetection(
    val platform: String,
    val via: String,
    val feedRegion: FeedRegion,
)

/** The feed platform for [packageName], or null if we have no rules for it. */
fun feedPlatformFor(packageName: String): FeedPlatform? =
    FEED_PLATFORMS.firstOrNull { it.packageName == packageName }

/** True if [packageName] has a feed we know how to find. */
fun isFeedHostPackage(packageName: String): Boolean = feedPlatformFor(packageName) != null

/**
 * Work out what to cover, or null to cover nothing.
 *
 * Returning null is the safe answer and the common one: every screen in the app that isn't the
 * timeline should land here. The caller is expected to take the overlay down whenever this returns
 * null, so a momentary miss costs a fade rather than a stuck cover.
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
    val items = nodes.filter { node ->
        platform.itemMarkers.any { node.viewId.contains(it) } && node.area > 0L
    }
    if (items.isEmpty()) return null

    val container = nodes
        .filter { node -> platform.containerMarkers.any { node.viewId.contains(it) } }
        .filter { it.area.toFloat() / screen.area >= MIN_FEED_CONTAINER_COVERAGE }
        .maxByOrNull { it.area }

    // Fall back to the items themselves when the container is named something we don't know. The
    // rows are visible and identified, so their extent is a fair description of the feed.
    val base = container?.let { FeedRegion(it.left, it.top, it.right, it.bottom) }
        ?: unionOf(items)

    val byName = clipToChrome(base.intersect(screen), nodes, platform, screen)
    val byShape = clipToBars(byName.region, nodes, platform, screen)
    val region = reserveChrome(
        region = byShape.region,
        screen = screen,
        topFound = byName.topFound || byShape.topFound,
        bottomFound = byName.bottomFound || byShape.bottomFound,
    )
    if (region.isEmpty) return null
    if (region.area.toFloat() / screen.area < MIN_FEED_REGION_COVERAGE) return null

    return FeedDetection(
        platform = platform.displayName,
        via = container?.viewId ?: items.first().viewId,
        feedRegion = region,
    )
}

/**
 * A clipped region, plus whether we actually recognised a bar at each edge.
 *
 * The flags are what let [reserveChrome] stay out of the way. An edge pinned to a bar we found is
 * exact; an edge nobody claimed is a guess, and a guess is what the safety band is for.
 */
private data class Clip(
    val region: FeedRegion,
    val topFound: Boolean,
    val bottomFound: Boolean,
)

/**
 * Pull the region clear of any bar at the top or bottom of the screen, found by shape.
 *
 * This is the backstop for [clipToChrome], and the more important of the two. Matching bar ids is
 * the part of these rules most likely to be out of date, and the failure it produces is the worst
 * one available: a covered bottom navigation leaves the user no way out of the feed, which is
 * indistinguishable from blocking the whole app. Shape survives a rename.
 *
 * Feed rows are excluded as candidates — a one-line comment row is wide and short too, and near the
 * bottom of the screen it would happily pass for a nav bar and shrink the cover away from it. The
 * stories strip is excluded for the mirror-image reason: taken for a top bar it would push the
 * cover below itself and leave the stories scrolling in the clear.
 */
private fun clipToBars(
    region: FeedRegion,
    nodes: List<FeedNode>,
    platform: FeedPlatform,
    screen: FeedRegion,
): Clip {
    if (region.isEmpty || screen.height <= 0) return Clip(region, false, false)
    val minWidth = screen.width * BAR_MIN_WIDTH_FRACTION
    val maxHeight = screen.height * BAR_MAX_HEIGHT_FRACTION
    val topZone = screen.top + screen.height * BAR_EDGE_ZONE_FRACTION
    val bottomZone = screen.bottom - screen.height * BAR_EDGE_ZONE_FRACTION

    var top = region.top
    var bottom = region.bottom
    var topFound = false
    var bottomFound = false
    for (node in nodes) {
        if (node.height <= 0 || node.width < minWidth || node.height > maxHeight) continue
        if (platform.itemMarkers.any { node.viewId.contains(it) }) continue
        if (platform.storyTrayMarkers.any { node.viewId.contains(it) }) continue
        val centre = (node.top + node.bottom) / 2f
        if (centre <= topZone) {
            top = maxOf(top, node.bottom)
            topFound = true
        }
        if (centre >= bottomZone) {
            bottom = minOf(bottom, node.top)
            bottomFound = true
        }
    }
    return Clip(FeedRegion(region.left, top, region.right, bottom), topFound, bottomFound)
}

private fun unionOf(nodes: List<FeedNode>): FeedRegion = FeedRegion(
    left = nodes.minOf { it.left },
    top = nodes.minOf { it.top },
    right = nodes.maxOf { it.right },
    bottom = nodes.maxOf { it.bottom },
)

private fun FeedRegion.intersect(other: FeedRegion): FeedRegion = FeedRegion(
    left = maxOf(left, other.left),
    top = maxOf(top, other.top),
    right = minOf(right, other.right),
    bottom = minOf(bottom, other.bottom),
)

/**
 * Pull the region clear of the top bar and the bottom navigation, found by id.
 *
 * Chrome above the middle of the region pushes its top down; chrome below the middle pulls its
 * bottom up. Deciding by which half the bar sits in — rather than by name — means a bar we haven't
 * seen before still gets out of the way, and it keeps the rule to one line of reasoning.
 *
 * A match only counts as *found* if it also sits in the screen's edge zone. A `toolbar` halfway
 * down the screen is worth clipping to but is no evidence about where the real chrome ends, and
 * letting it stand down the safety band would be trusting the wrong thing.
 */
private fun clipToChrome(
    region: FeedRegion,
    nodes: List<FeedNode>,
    platform: FeedPlatform,
    screen: FeedRegion,
): Clip {
    if (region.isEmpty) return Clip(region, false, false)
    var top = region.top
    var bottom = region.bottom
    var topFound = false
    var bottomFound = false
    val middle = (region.top + region.bottom) / 2
    val topZone = screen.top + screen.height * BAR_EDGE_ZONE_FRACTION
    val bottomZone = screen.bottom - screen.height * BAR_EDGE_ZONE_FRACTION

    for (node in nodes) {
        if (platform.chromeMarkers.none { node.viewId.contains(it) }) continue
        if (node.height <= 0 || node.right <= region.left || node.left >= region.right) continue
        if (node.bottom <= middle) {
            top = maxOf(top, node.bottom)
            if (node.bottom <= topZone) topFound = true
        } else if (node.top >= middle) {
            bottom = minOf(bottom, node.top)
            if (node.top >= bottomZone) bottomFound = true
        }
    }

    return Clip(FeedRegion(region.left, top, region.right, bottom), topFound, bottomFound)
}

/** Apply the safety bands, but only at an edge where no bar was recognised. See the constants. */
private fun reserveChrome(
    region: FeedRegion,
    screen: FeedRegion,
    topFound: Boolean,
    bottomFound: Boolean,
): FeedRegion = FeedRegion(
    left = region.left,
    top = if (topFound) {
        region.top
    } else {
        maxOf(region.top, screen.top + (screen.height * MIN_TOP_CHROME_FRACTION).toInt())
    },
    right = region.right,
    bottom = if (bottomFound) {
        region.bottom
    } else {
        minOf(region.bottom, screen.bottom - (screen.height * MIN_BOTTOM_CHROME_FRACTION).toInt())
    },
)
