package com.discnct.app.feed

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val W = 1080
private const val H = 2400
private val SCREEN = FeedRegion(0, 0, W, H)

/** The bands the cover is never allowed into, for the screen these tests use. */
private const val TOP_SAFE = (H * MIN_TOP_CHROME_FRACTION).toInt()
private const val BOTTOM_SAFE = H - (H * MIN_BOTTOM_CHROME_FRACTION).toInt()

private fun node(id: String, top: Int, bottom: Int, left: Int = 0, right: Int = W) =
    FeedNode("com.instagram.android:id/$id", left, top, right, bottom)

/** The highest pixel any part of the cover reaches, across both rectangles. */
private fun FeedDetection.topmostCovered(): Int =
    minOf(feedRegion.top, storiesRegion?.top ?: feedRegion.top)

/** The lowest pixel any part of the cover reaches. */
private fun FeedDetection.bottommostCovered(): Int =
    maxOf(feedRegion.bottom, storiesRegion?.bottom ?: feedRegion.bottom)

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
        // Starts under the stories strip, stops above the nav bar.
        assertEquals(FeedRegion(0, 400, W, 2200), hit.feedRegion)
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

    @Test fun `the bottom band stays clear even when no nav bar is recognised`() {
        // The failure this guards against is the one that actually happened on a device: an id we
        // don't know means a bar we don't clip, and a covered nav bar makes the app unusable.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("some_unknown_bottom_bar_2026", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertTrue(hit.bottommostCovered() <= BOTTOM_SAFE)
        // Not just inside the guaranteed band — clipped to the bar itself, found by its shape.
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
        // Had the row been taken for a bar, the cover would stop at 2150 and leave a live strip.
        assertTrue(hit.feedRegion.bottom > 2150)
    }

    @Test fun `the shell is measured off the views that are really there`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            FeedNode("", 20, 200, 210, 390),
            FeedNode("", 240, 200, 430, 390),
            FeedNode("", 460, 200, 650, 390),
            FeedNode("", 680, 200, 870, 390),
            node("row_feed_photo_imageview", 700, 1780),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        // Four rings on screen, the user's own left uncovered, so three get drawn over.
        assertEquals(3, hit.storyShapes.count { it.form == ShapeForm.CIRCLE })
        assertEquals((210 + 240) / 2, assertNotNull(hit.storiesRegion).left)
        // And the empty photo lands on the real photo.
        val block = assertNotNull(hit.feedShapes.firstOrNull { it.form == ShapeForm.BLOCK })
        assertEquals(700, block.bounds.top)
        assertEquals(1780, block.bounds.bottom)
    }

    @Test fun `the top band stays clear even when no action bar is recognised`() {
        // The logo, the new-post button and notifications all live up here and all have to work.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertTrue(hit.topmostCovered() >= TOP_SAFE)
    }

    @Test fun `the top bar is left uncovered`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertTrue(hit.topmostCovered() >= 160)
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
        assertEquals(TOP_SAFE, hit.topmostCovered())
    }

    @Test fun `the stories strip is covered but the users own story is not`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 500, 1600),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        val stories = assertNotNull(hit.storiesRegion)
        // A slot's worth of strip on the left is left alone — that's "add to your story".
        assertTrue(stories.left > 0)
        assertEquals(W, stories.right)
        assertEquals(500, stories.bottom)
        // And the feed picks up exactly where the strip ends, with no live gap between them.
        assertEquals(stories.bottom, hit.feedRegion.top)
    }

    @Test fun `a tall banner above the first post is not mistaken for the stories strip`() {
        // If it were, the slot we keep live for "your story" would be a tall column of real feed
        // showing through the cover.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 1100, 1900),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertNull(hit.storiesRegion)
        assertEquals(TOP_SAFE, hit.feedRegion.top)
    }

    @Test fun `a named stories tray is used when the app gives us one`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("reel_tray_recycler_view", 200, 520),
            node("row_feed_photo_imageview", 560, 1600),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(520, assertNotNull(hit.storiesRegion).bottom)
        assertEquals(520, hit.feedRegion.top)
    }

    @Test fun `a scrolled feed has no stories strip left to cover`() {
        // Scroll down and the strip goes with it; the space it held is feed again, not a live gap.
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 170, 1200),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertNull(hit.storiesRegion)
        assertEquals(TOP_SAFE, hit.feedRegion.top)
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
        assertTrue(hit.feedRegion.left >= 0 && hit.topmostCovered() >= 0)
        assertTrue(hit.feedRegion.right <= W && hit.bottommostCovered() <= H)
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
