package com.discnct.app.feed

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val W = 1080
private const val H = 2400
private val SCREEN = FeedRegion(0, 0, W, H)

/** The bands the cover is held out of when no bar was recognised at that edge. */
private const val TOP_SAFE = (H * MIN_TOP_CHROME_FRACTION).toInt()
private const val BOTTOM_SAFE = H - (H * MIN_BOTTOM_CHROME_FRACTION).toInt()

private fun node(id: String, top: Int, bottom: Int, left: Int = 0, right: Int = W) =
    FeedNode("com.instagram.android:id/$id", left, top, right, bottom)

class FeedRulesTest {

    @Test fun `the instagram home feed is found and covered`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals("Instagram", hit.platform)
        // One pane, from under the top bar down to the top of the nav bar.
        assertEquals(FeedRegion(0, 160, W, 2200), hit.feedRegion)
    }

    @Test fun `the DM inbox is never covered`() {
        // Same container id as the feed, and this is the whole reason a container match alone is
        // not allowed to be enough: covering someone's inbox is worse than missing a feed.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("row_inbox_container", 200, 400),
            node("tab_bar", 2200, 2400),
        )
        assertNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
    }

    @Test fun `the search grid is never covered`() {
        val nodes = listOf(
            node("recycler_view", 160, 2200),
            node("image_button", 200, 500),
        )
        assertNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
    }

    @Test fun `a profile post grid is never covered`() {
        val nodes = listOf(
            node("recycler_view", 160, 2200),
            node("profile_header_container", 160, 700),
            node("media_thumbnail", 700, 1000),
        )
        assertNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
    }

    @Test fun `the bottom navigation is left uncovered`() {
        // The nav bar is how the user gets to DMs and posting. Swallowing it would turn a feed
        // cover into an app block, which is the level above this one.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2200, hit.feedRegion.bottom)
    }

    @Test fun `the cover reaches the nav bar it found, not the safety band above it`() {
        // The bug this pins down: the flat band is deeper than a real nav bar, so applying both
        // stopped the cover short and left a live strip of feed scrolling below it.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2250, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2250, hit.feedRegion.bottom)
        assertTrue(hit.feedRegion.bottom > BOTTOM_SAFE, "the band must not hold the cover back")
    }

    @Test fun `the cover stretches down to a nav bar the feed container stops short of`() {
        // Straight off a device screenshot: Instagram's timeline ends above the bottom navigation
        // rather than running under it, and an edge that could only move inwards had nothing to
        // close the gap with. What showed through was a live like-and-comment row below the cover.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2100),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2200, hit.feedRegion.bottom, "the cover must reach the nav bar, not stop above it")
    }

    @Test fun `the cover stretches up to a top bar the feed container starts below`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 260, 2200),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.feedRegion.top)
    }

    @Test fun `a short list is not stretched out to fill the space between the bars`() {
        // The limit on the reach above. A gap of tens of pixels is a feed ending early; a gap of
        // most of the screen is a different surface, and covering it would be an app block.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 700, 1700),
            node("row_feed_photo_imageview", 750, 1650),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(FeedRegion(0, 700, W, 1700), hit.feedRegion)
    }

    @Test fun `the bottom band stays clear when no nav bar is recognised at all`() {
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(BOTTOM_SAFE, hit.feedRegion.bottom)
    }

    @Test fun `a nav bar with an unfamiliar name is still found, by its shape`() {
        // The failure this guards against is the one that actually happened on a device: an id we
        // don't know means a bar we don't clip, and a covered nav bar makes the app unusable.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("some_unknown_bottom_bar_2026", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2200, hit.feedRegion.bottom)
    }

    @Test fun `a wide short feed row near the bottom is not mistaken for a nav bar`() {
        // If it were, the cover would retreat from the bottom of the feed and leave a live strip.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("row_feed_comment_textview", 2150, 2210),
            node("tab_bar", 2250, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2250, hit.feedRegion.bottom)
    }

    @Test fun `the top band stays clear even when no action bar is recognised`() {
        // The logo, the new-post button and notifications all live up here and all have to work.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(TOP_SAFE, hit.feedRegion.top)
    }

    @Test fun `the cover starts right under the top bar`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.feedRegion.top)
    }

    @Test fun `chrome that does not overlap the feed horizontally is ignored`() {
        // A floating side control shouldn't be able to shave the feed region down.
        val nodes = listOf(
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 400, 1400),
            FeedNode("com.instagram.android:id/action_bar_overflow", -300, 100, -100, 300),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2200, hit.feedRegion.bottom)
        assertEquals(TOP_SAFE, hit.feedRegion.top)
    }

    @Test fun `the stories strip is covered along with the feed`() {
        // One pane now, not two: the strip is the same endless surface as the timeline under it,
        // and a cover that started below it left the most scrollable part of the screen live.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("reel_tray_recycler_view", 170, 480),
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 500, 1600),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.feedRegion.top)
    }

    @Test fun `a stories tray is never taken for a top bar`() {
        // A short tray is wide and near the top, which is the shape of a bar. Clipping to it would
        // start the cover below the stories and leave them scrolling in the clear.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("story_tray_container", 170, 350),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.feedRegion.top)
    }

    @Test fun `a feed list inside a small sheet is not the main feed`() {
        val nodes = listOf(
            node("recycler_view", 2000, 2300),
            node("row_feed_photo_imageview", 2050, 2200),
        )
        assertNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
    }

    @Test fun `feed rows alone still give a region when the container is unrecognised`() {
        val nodes = listOf(
            node("some_new_container_name", 160, 2200),
            node("row_feed_photo_imageview", 200, 1200),
            node("row_feed_comment_textview", 1200, 2100),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(FeedRegion(0, 200, W, 2100), hit.feedRegion)
    }

    @Test fun `the covered region never escapes the screen`() {
        val nodes = listOf(
            FeedNode("com.instagram.android:id/recycler_view", -200, -400, W + 200, H + 400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertTrue(hit.feedRegion.left >= 0 && hit.feedRegion.top >= 0)
        assertTrue(hit.feedRegion.right <= W && hit.feedRegion.bottom <= H)
    }

    @Test fun `an app with no feed rules is never covered`() {
        val nodes = listOf(FeedNode("com.example.notes:id/recycler_view", 0, 0, W, H))
        assertNull(detectFeedSurface("com.example.notes", nodes, SCREEN))
        assertTrue(!isFeedHostPackage("com.example.notes"))
    }

    @Test fun `a zero-sized screen is refused rather than divided by`() {
        val nodes = listOf(node("recycler_view", 0, 100), node("row_feed_photo_imageview", 0, 100))
        assertNull(detectFeedSurface("com.instagram.android", nodes, FeedRegion(0, 0, 0, 0)))
    }

    @Test fun `zero-area feed rows do not count as proof of a feed`() {
        // A collapsed row mid-transition is not the user looking at a timeline.
        val nodes = listOf(
            node("recycler_view", 160, 2200),
            FeedNode("com.instagram.android:id/row_feed_photo_imageview", 0, 500, 0, 500),
        )
        assertNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
    }

    @Test fun `instagram is a known feed host`() {
        assertTrue(isFeedHostPackage("com.instagram.android"))
        assertEquals("Instagram", feedPlatformFor("com.instagram.android")?.displayName)
    }
}
