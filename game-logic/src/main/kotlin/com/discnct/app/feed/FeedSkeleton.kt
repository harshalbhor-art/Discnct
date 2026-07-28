package com.discnct.app.feed

/**
 * The empty shell drawn over a covered feed, built from where the real thing actually is.
 *
 * The first version of this drew from fixed dp constants — a 32dp avatar, a 62dp story ring, 16dp
 * gaps — and stacked them down the covered rectangle. That adapts to screen density and nothing
 * else. It doesn't know how tall Instagram's stories strip is on *this* phone, where the photo in a
 * post starts, or that the user has display size set to large. So the shell drifted out of
 * alignment with the app underneath it, which is exactly the thing that makes a cover look broken
 * rather than deliberate.
 *
 * Everything here is derived from the bounds of views we actually measured on screen this frame, in
 * screen pixels. A ring lands where a ring was; an empty photo lands where the photo was. Density,
 * resolution, font scale and font size all come out in the wash, because none of them are inputs —
 * the measured pixels already account for them.
 *
 * Where a real view can't be found, each builder returns nothing and the view falls back to drawing
 * proportionally. Nothing here is load-bearing for *covering*; it only decides what the cover looks
 * like.
 */

/** How a piece of the shell is drawn. */
enum class ShapeForm { CIRCLE, LINE, BLOCK }

/** One piece of the shell, positioned in screen pixels. */
data class FeedShape(val bounds: FeedRegion, val form: ShapeForm)

/** Views smaller than this share of the screen's width are too small to be a story item. */
private const val TRAY_ITEM_MIN_WIDTH_FRACTION = 0.06f

/** And wider than this they're a banner, not a story item. */
private const val TRAY_ITEM_MAX_WIDTH_FRACTION = 0.32f

/** Two candidates whose centres are closer than this are the same item seen at two depths. */
private const val TRAY_ITEM_CLUSTER_FRACTION = 0.04f

/** Ids that carry a post's photo, which is the anchor everything else in a card hangs off. */
private val MEDIA_MARKERS = listOf("row_feed_photo", "media_image", "carousel_image", "preview_image")

/** A post's photo runs nearly the full width; anything narrower is a thumbnail somewhere else. */
private const val MEDIA_MIN_WIDTH_FRACTION = 0.6f

/**
 * The story items on screen, left to right, one entry per item.
 *
 * Candidates are picked by shape rather than by id — roughly square, about the size of an avatar,
 * sitting inside the strip — because the ids on these change between Instagram versions and their
 * geometry doesn't. Nested views describing the same item (a container and the image inside it)
 * collapse to the largest of the cluster.
 */
fun trayItemsIn(band: FeedRegion, nodes: List<FeedNode>, screen: FeedRegion): List<FeedNode> {
    if (band.isEmpty || screen.width <= 0) return emptyList()
    val minWidth = screen.width * TRAY_ITEM_MIN_WIDTH_FRACTION
    val maxWidth = screen.width * TRAY_ITEM_MAX_WIDTH_FRACTION
    val slack = band.height / 4

    val candidates = nodes.filter { node ->
        node.width >= minWidth &&
            node.width <= maxWidth &&
            node.height >= node.width * 0.7f &&
            node.height <= node.width * 1.8f &&
            node.top >= band.top - slack &&
            node.bottom <= band.bottom + slack
    }

    val clusterWidth = screen.width * TRAY_ITEM_CLUSTER_FRACTION
    val clustered = mutableListOf<FeedNode>()
    for (node in candidates.sortedByDescending { it.area }) {
        val centre = (node.left + node.right) / 2
        val clash = clustered.any { kotlin.math.abs((it.left + it.right) / 2 - centre) < clusterWidth }
        if (!clash) clustered.add(node)
    }
    return clustered.sortedBy { it.left }
}

/**
 * Where the cover over the strip starts, given the item the user owns.
 *
 * Splitting the difference between the user's item and the next one means neither gets clipped,
 * whatever the spacing happens to be. With nothing to measure we fall back to a fixed share of the
 * strip, which is the old behaviour and is wrong often enough to be worth avoiding.
 */
fun storiesCoverLeft(band: FeedRegion, items: List<FeedNode>): Int {
    val own = items.firstOrNull() ?: return band.left + (band.width * SELF_STORY_WIDTH_FRACTION).toInt()
    val next = items.getOrNull(1) ?: return own.right
    return (own.right + next.left) / 2
}

/** Empty rings and blank name labels, one per item, exactly where the real ones are. */
fun storyShapes(items: List<FeedNode>): List<FeedShape> = items.flatMap { item ->
    val side = minOf(item.width, item.height)
    val centreX = (item.left + item.right) / 2
    val ring = FeedRegion(
        left = centreX - side / 2,
        top = item.top,
        right = centreX + side / 2,
        bottom = item.top + side,
    )
    val labelWidth = (side * 0.62f).toInt()
    val label = FeedRegion(
        left = centreX - labelWidth / 2,
        top = ring.bottom + (side * 0.12f).toInt(),
        right = centreX + labelWidth / 2,
        bottom = ring.bottom + (side * 0.24f).toInt(),
    )
    listOf(FeedShape(ring, ShapeForm.CIRCLE), FeedShape(label, ShapeForm.LINE))
}

/**
 * An empty post card per photo on screen.
 *
 * The photo is the one part of a post big enough and distinctive enough to find reliably, so it's
 * the anchor: the header goes above it and the actions below, sized from its width. That keeps the
 * card in proportion with the real one on any screen, and keeps the empty photo exactly over the
 * real photo — the part the user is actually here to not look at.
 */
fun postShapes(feed: FeedRegion, nodes: List<FeedNode>, screen: FeedRegion): List<FeedShape> {
    if (feed.isEmpty || screen.width <= 0) return emptyList()
    val minWidth = screen.width * MEDIA_MIN_WIDTH_FRACTION

    val media = nodes
        .filter { node -> MEDIA_MARKERS.any { node.viewId.contains(it) } }
        .filter { it.width >= minWidth && it.height > 0 }
        .filter { it.bottom > feed.top && it.top < feed.bottom }
        .sortedBy { it.top }
    if (media.isEmpty()) return emptyList()

    val shapes = mutableListOf<FeedShape>()
    for (photo in media) {
        val w = photo.width
        val unit = w / 100f
        val margin = photo.left

        shapes += FeedShape(
            FeedRegion(photo.left, photo.top, photo.right, photo.bottom),
            ShapeForm.BLOCK,
        )

        // Header: avatar and two lines of handle, sitting in the space above the photo.
        val avatar = (unit * 9f).toInt()
        val headerBottom = photo.top - (unit * 3f).toInt()
        val headerTop = headerBottom - avatar
        shapes += FeedShape(
            FeedRegion(margin, headerTop, margin + avatar, headerTop + avatar),
            ShapeForm.CIRCLE,
        )
        val textLeft = margin + avatar + (unit * 3f).toInt()
        shapes += FeedShape(
            FeedRegion(textLeft, headerTop + (unit * 1f).toInt(), textLeft + (unit * 30f).toInt(), headerTop + (unit * 4f).toInt()),
            ShapeForm.LINE,
        )
        shapes += FeedShape(
            FeedRegion(textLeft, headerTop + (unit * 6f).toInt(), textLeft + (unit * 20f).toInt(), headerTop + (unit * 8.5f).toInt()),
            ShapeForm.LINE,
        )

        // Actions: like, comment, share on the left; save on the right.
        val icon = (unit * 6.5f).toInt()
        val actionsTop = photo.bottom + (unit * 3f).toInt()
        var x = margin
        repeat(3) {
            shapes += FeedShape(FeedRegion(x, actionsTop, x + icon, actionsTop + icon), ShapeForm.LINE)
            x += icon + (unit * 4f).toInt()
        }
        shapes += FeedShape(
            FeedRegion(photo.right - icon, actionsTop, photo.right, actionsTop + icon),
            ShapeForm.LINE,
        )

        // Caption.
        val captionTop = actionsTop + icon + (unit * 3f).toInt()
        shapes += FeedShape(
            FeedRegion(margin, captionTop, margin + (unit * 62f).toInt(), captionTop + (unit * 2.6f).toInt()),
            ShapeForm.LINE,
        )
        val secondTop = captionTop + (unit * 5f).toInt()
        shapes += FeedShape(
            FeedRegion(margin, secondTop, margin + (unit * 38f).toInt(), secondTop + (unit * 2.6f).toInt()),
            ShapeForm.LINE,
        )
    }

    // Anything that would land outside the cover would be drawn on top of the live app.
    return shapes.filter { it.bounds.top >= feed.top && it.bounds.bottom <= feed.bottom }
}
