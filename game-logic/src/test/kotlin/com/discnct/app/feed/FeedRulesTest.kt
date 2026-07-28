package com.discnct.app.feed

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val W = 1080
private const val H = 2400
private val SCREEN = FeedRegion(0, 0, W, H)

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
        val hit = detectFeedSurface("com.instagram.android", nodes, SCREEN)
        assertNotNull(hit)
        assertEquals("Instagram", hit.platform)
        assertEquals(FeedRegion(0, 160, W, 2200), hit.region)
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
        // block into an app block, which is the level above this one.
        val nodes = listOf(
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
            node("tab_bar", 2200, 2400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(2200, hit.region.bottom)
    }

    @Test fun `the top bar is left uncovered`() {
        val nodes = listOf(
            node("action_bar", 0, 160),
            node("recycler_view", 0, 2400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.region.top)
    }

    @Test fun `chrome that does not overlap the feed horizontally is ignored`() {
        // A floating side control shouldn't be able to shave the feed region down.
        val nodes = listOf(
            node("recycler_view", 160, 2200),
            node("row_feed_photo_imageview", 400, 1400),
            FeedNode("com.instagram.android:id/action_bar_overflow", -300, 100, -100, 300),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertEquals(160, hit.region.top)
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
        assertEquals(FeedRegion(0, 200, W, 2100), hit.region)
    }

    @Test fun `the covered region never escapes the screen`() {
        val nodes = listOf(
            FeedNode("com.instagram.android:id/recycler_view", -200, -400, W + 200, H + 400),
            node("row_feed_photo_imageview", 400, 1400),
        )
        val hit = assertNotNull(detectFeedSurface("com.instagram.android", nodes, SCREEN))
        assertTrue(hit.region.left >= 0 && hit.region.top >= 0)
        assertTrue(hit.region.right <= W && hit.region.bottom <= H)
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
