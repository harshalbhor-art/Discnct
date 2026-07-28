package com.discnct.app.feed

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

private const val SW = 1080
private const val SH = 2400
private val DISPLAY = FeedRegion(0, 0, SW, SH)

private fun box(left: Int, top: Int, right: Int, bottom: Int, id: String = "") =
    FeedNode(id, left, top, right, bottom)

/** Five story avatars in a row, the size and spacing Instagram actually uses on a 1080px screen. */
private fun trayAvatars() = (0 until 5).map { i ->
    val left = 20 + i * 220
    box(left, 200, left + 190, 390)
}

class FeedSkeletonTest {

    private val band = FeedRegion(0, 192, SW, 500)

    @Test fun `story items are found by shape rather than by id`() {
        // None of these carry an id — Instagram doesn't expose one for every ring, which is the
        // whole reason this goes by geometry.
        val items = trayItemsIn(band, trayAvatars(), DISPLAY)
        assertEquals(5, items.size)
        assertEquals(20, items.first().left)
        assertEquals(900, items.last().left)
    }

    @Test fun `one item seen at two depths counts once`() {
        val nested = trayAvatars() + box(40, 215, 220, 375)
        assertEquals(5, trayItemsIn(band, nested, DISPLAY).size)
    }

    @Test fun `the returned items run left to right`() {
        val shuffled = trayAvatars().reversed()
        val items = trayItemsIn(band, shuffled, DISPLAY)
        assertEquals(items.map { it.left }, items.map { it.left }.sorted())
    }

    @Test fun `things too big or too small to be a story item are ignored`() {
        val noise = listOf(
            box(0, 200, 40, 240),        // an icon
            box(0, 190, SW, 400),        // the strip itself
            box(300, 210, 900, 380),     // a banner
        )
        assertTrue(trayItemsIn(band, noise, DISPLAY).isEmpty())
    }

    @Test fun `views outside the strip are not story items`() {
        val below = listOf(box(20, 900, 210, 1090))
        assertTrue(trayItemsIn(band, below, DISPLAY).isEmpty())
    }

    @Test fun `the cover starts between the users own item and the next one`() {
        val items = trayItemsIn(band, trayAvatars(), DISPLAY)
        // Halfway across the gap, so neither ring is clipped whatever the spacing is.
        assertEquals((210 + 240) / 2, storiesCoverLeft(band, items))
    }

    @Test fun `with nothing measurable the cover falls back to a share of the strip`() {
        val fallback = band.left + (band.width * SELF_STORY_WIDTH_FRACTION).toInt()
        assertEquals(fallback, storiesCoverLeft(band, emptyList()))
    }

    @Test fun `a single story item leaves everything to its right covered`() {
        val one = trayItemsIn(band, listOf(trayAvatars().first()), DISPLAY)
        assertEquals(210, storiesCoverLeft(band, one))
    }

    @Test fun `each story item becomes a ring and a name label`() {
        val items = trayItemsIn(band, trayAvatars(), DISPLAY)
        val shapes = storyShapes(items)
        assertEquals(10, shapes.size)
        assertEquals(5, shapes.count { it.form == ShapeForm.CIRCLE })
        assertEquals(5, shapes.count { it.form == ShapeForm.LINE })
        // The ring lands on the real ring, not near it.
        val first = shapes.first { it.form == ShapeForm.CIRCLE }
        assertEquals(20, first.bounds.left)
        assertEquals(200, first.bounds.top)
        assertEquals(190, first.bounds.width)
    }

    @Test fun `a post card is built around the real photo`() {
        val feed = FeedRegion(0, 400, SW, 2208)
        val nodes = listOf(
            box(0, 700, SW, 1780, "com.instagram.android:id/row_feed_photo_imageview"),
        )
        val shapes = postShapes(feed, nodes, DISPLAY)
        val block = assertNotNull(shapes.firstOrNull { it.form == ShapeForm.BLOCK })
        // The empty photo sits exactly where the real one was — that's the point of measuring.
        assertEquals(FeedRegion(0, 700, SW, 1780), block.bounds)
        // Header above it, actions and caption below.
        assertTrue(shapes.any { it.form == ShapeForm.CIRCLE && it.bounds.bottom <= 700 })
        assertTrue(shapes.any { it.form == ShapeForm.LINE && it.bounds.top >= 1780 })
    }

    @Test fun `a card is sized in proportion to the photo it replaces`() {
        val feed = FeedRegion(0, 400, SW, 2208)
        val wide = postShapes(
            feed,
            listOf(box(0, 700, SW, 1780, "com.instagram.android:id/row_feed_photo_imageview")),
            DISPLAY,
        )
        val narrow = postShapes(
            FeedRegion(0, 400, 720, 2208),
            listOf(box(0, 700, 720, 1420, "com.instagram.android:id/row_feed_photo_imageview")),
            FeedRegion(0, 0, 720, SH),
        )
        val wideAvatar = wide.first { it.form == ShapeForm.CIRCLE }.bounds.width
        val narrowAvatar = narrow.first { it.form == ShapeForm.CIRCLE }.bounds.width
        assertTrue(narrowAvatar < wideAvatar, "a narrower screen gets a smaller avatar")
    }

    @Test fun `nothing is drawn outside the covered region`() {
        // A shape past the edge would be painted straight onto the live app.
        val feed = FeedRegion(0, 400, SW, 1900)
        val nodes = listOf(
            box(0, 420, SW, 1500, "com.instagram.android:id/row_feed_photo_imageview"),
        )
        val shapes = postShapes(feed, nodes, DISPLAY)
        assertTrue(shapes.all { it.bounds.top >= feed.top && it.bounds.bottom <= feed.bottom })
    }

    @Test fun `a thumbnail is not a post photo`() {
        val feed = FeedRegion(0, 400, SW, 2208)
        val nodes = listOf(box(0, 700, 300, 1000, "com.instagram.android:id/row_feed_photo_imageview"))
        assertTrue(postShapes(feed, nodes, DISPLAY).isEmpty())
    }

    @Test fun `with no photo on screen there is nothing to measure`() {
        val feed = FeedRegion(0, 400, SW, 2208)
        assertTrue(postShapes(feed, listOf(box(0, 700, SW, 900, "x:id/row_feed_comment")), DISPLAY).isEmpty())
    }
}
