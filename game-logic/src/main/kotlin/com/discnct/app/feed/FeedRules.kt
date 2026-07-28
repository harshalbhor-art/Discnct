package com.discnct.app.feed

/**
 * Pure, Android-free rules for finding the *scrolling feed* inside a social app.
 *
 * This is the second half of Level 1. The reel blocker bounces you out of a full-screen short-form
 * viewer; the feed cover leaves you exactly where you are and covers the endless-scroll surface
 * with an empty shell, so DMs, search, posting and profiles keep working while the trap doesn't.
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
 * What comes back is deliberately *two* rectangles rather than one. The point of this level is
 * minimum access, not no access: the top bar (logo, new post, notifications), the bottom navigation
 * and the user's own story slot all have to stay live, because they're how you post and how you
 * leave. Only the parts built to be scrolled forever get covered.
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

/** A rectangle in screen pixels. Used for both the screen itself and the regions we cover. */
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
    /** The horizontal stories strip, when the app names it. See [storiesStrip] for the fallback. */
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
 * Bands at the top and bottom of the screen that are **never** covered, whatever the view tree says.
 *
 * These exist because id-matched chrome is the part of this file most likely to be wrong: bar ids
 * change between app versions, and a bar we fail to recognise is a bar we cover. Covering the
 * bottom navigation makes the app unusable and looks, from the user's side, exactly like a whole-app
 * block. So the geometry gets the final say and the ids can only ever clip *further* in.
 *
 * Sized from what these bars actually measure — roughly 72dp each once the status bar and the
 * gesture handle are counted — so on a normal phone the guarantee costs a sliver of feed at most.
 */
const val MIN_TOP_CHROME_FRACTION = 0.08f
const val MIN_BOTTOM_CHROME_FRACTION = 0.08f

/**
 * A gap between the top chrome and the first feed row taller than this is the stories strip.
 *
 * Inferring it from the gap rather than from an id is what makes the strip survive an app update:
 * the ids we'd match on are the ones most likely to be renamed, while "there is something between
 * the top bar and the first post" stays true. When the user scrolls the strip away the gap closes
 * on its own and the whole area goes back to being feed.
 */
const val MIN_STORY_TRAY_HEIGHT_FRACTION = 0.03f

/**
 * And a band taller than this isn't a stories strip, whatever it claims to be.
 *
 * The strip is about 110dp on a phone. Anything much taller is a banner, a promo card or a
 * misidentified container, and treating it as the strip would leave a tall live column of real
 * feed showing through the gap we keep for the user's own story.
 */
const val MAX_STORY_TRAY_HEIGHT_FRACTION = 0.16f

/**
 * The left slice of the stories strip left uncovered: the user's own story.
 *
 * Posting is not the habit this level is aimed at, and "add to your story" is the one control in
 * that strip that isn't someone else's content to scroll through.
 */
const val SELF_STORY_WIDTH_FRACTION = 0.22f

/**
 * What we decided to cover.
 *
 * @param feedRegion the timeline itself, below the stories strip and clear of both bars.
 * @param storiesRegion everyone else's stories, or null when the strip isn't on screen. The user's
 *   own slot is already excluded from this rectangle.
 */
data class FeedDetection(
    val platform: String,
    val via: String,
    val feedRegion: FeedRegion,
    val storiesRegion: FeedRegion? = null,
)

/** The feed platform for [packageName], or null if we have no rules for it. */
fun feedPlatformFor(packageName: String): FeedPlatform? =
    FEED_PLATFORMS.firstOrNull { it.packageName == packageName }

/** True if [packageName] has a feed we know how to find. */
fun isFeedHostPackage(packageName: String): Boolean = feedPlatformFor(packageName) != null

/**
 * Work out which parts of the screen to cover, or null to cover nothing.
 *
 * Returning null is the safe answer and the common one: every screen in the app that isn't the
 * timeline should land here. The caller is expected to take the overlay down whenever this returns
 * null, so a momentary miss costs a flicker rather than a stuck cover.
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

    val body = reserveChrome(clipToChrome(base.intersect(screen), nodes, platform), screen)
    if (body.isEmpty) return null

    val tray = storiesStrip(body, nodes, items, platform, screen)
    val feed = if (tray == null) body else FeedRegion(body.left, tray.bottom, body.right, body.bottom)
    if (feed.isEmpty) return null
    if (feed.area.toFloat() / screen.area < MIN_FEED_REGION_COVERAGE) return null

    return FeedDetection(
        platform = platform.displayName,
        via = container?.viewId ?: items.first().viewId,
        feedRegion = feed,
        storiesRegion = tray?.let { exceptSelfStory(it) },
    )
}

/**
 * The stories strip, if it's on screen.
 *
 * Two ways in, in order of confidence: a container the app names, or — because those names change —
 * the gap between the top of the feed body and the first post. A strip found either way is returned
 * whole; [exceptSelfStory] is what carves the user's own slot back out of it.
 */
private fun storiesStrip(
    body: FeedRegion,
    nodes: List<FeedNode>,
    items: List<FeedNode>,
    platform: FeedPlatform,
    screen: FeedRegion,
): FeedRegion? {
    val named = nodes
        .filter { node -> platform.storyTrayMarkers.any { node.viewId.contains(it) } }
        .filter { it.area > 0L }
        .maxByOrNull { it.area }
    if (named != null) {
        val strip = FeedRegion(body.left, maxOf(named.top, body.top), body.right, named.bottom)
            .intersect(body)
        strip.takeIf { plausibleStrip(it, screen) }?.let { return it }
    }

    val firstRowTop = items.minOf { it.top }
    val inferred = FeedRegion(body.left, body.top, body.right, minOf(firstRowTop, body.bottom))
    return inferred.takeIf { plausibleStrip(it, screen) }
}

private fun plausibleStrip(strip: FeedRegion, screen: FeedRegion): Boolean {
    if (strip.isEmpty) return false
    return strip.height >= screen.height * MIN_STORY_TRAY_HEIGHT_FRACTION &&
        strip.height <= screen.height * MAX_STORY_TRAY_HEIGHT_FRACTION
}

/** Everything in the strip except the leftmost slot, which is the user's own story. */
private fun exceptSelfStory(strip: FeedRegion): FeedRegion? {
    val selfRight = strip.left + (strip.width * SELF_STORY_WIDTH_FRACTION).toInt()
    val rest = FeedRegion(selfRight, strip.top, strip.right, strip.bottom)
    return rest.takeUnless { it.isEmpty }
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

/** Apply the bands that are never covered regardless of what the view tree claims. */
private fun reserveChrome(region: FeedRegion, screen: FeedRegion): FeedRegion = FeedRegion(
    left = region.left,
    top = maxOf(region.top, screen.top + (screen.height * MIN_TOP_CHROME_FRACTION).toInt()),
    right = region.right,
    bottom = minOf(region.bottom, screen.bottom - (screen.height * MIN_BOTTOM_CHROME_FRACTION).toInt()),
)
