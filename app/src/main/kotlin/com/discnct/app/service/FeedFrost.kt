package com.discnct.app.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.discnct.app.feed.FeedRegion

/**
 * Turns a screenshot into the frosted pane drawn over a feed.
 *
 * The first version of the cover asked the system to do this, with
 * [android.view.WindowManager.LayoutParams.blurBehindRadius]. That was wrong in a way no amount of
 * tuning could fix: blur-behind travels the same path as `FLAG_DIM_BEHIND`, so it applies to
 * *everything* behind the window rather than the window's own rectangle. The cover was correctly
 * sized to the feed and the blur still came out across the whole screen, top bar and bottom
 * navigation included. There is no parameter that bounds it.
 *
 * So the blur is ours now. One still of the area about to be covered, shrunk to a couple of
 * hundred pixels and drawn back stretched, is a frosted pane — bilinear filtering does the blurring
 * on the way up, for free, and because we draw it ourselves it cannot land anywhere but inside our
 * own bounds. It costs one capture each time the cover appears rather than GPU on every frame.
 *
 * The still being *still* is the other half of the point. A live blur is a live feed you can't
 * quite read, and motion is the thing that catches an eye. A frozen one is over.
 */

/** How wide the shrunk still is. The blur is what you lose going down to this and back up. */
const val FROST_WIDTH_PX = 160

/**
 * Crop [source] to [region] and shrink it to [targetWidth], keeping its shape.
 *
 * Returns null when there's nothing usable to crop — off-screen bounds, a zero-sized region, a
 * bitmap that wouldn't copy. The caller falls back to a plain tint, which is a worse cover but
 * still a cover.
 */
fun frostedCrop(source: Bitmap, region: FeedRegion, targetWidth: Int = FROST_WIDTH_PX): Bitmap? {
    if (region.isEmpty || targetWidth <= 0) return null

    // A screenshot arrives as a HARDWARE bitmap, which a software Canvas refuses to read from.
    val readable = if (source.config == Bitmap.Config.HARDWARE) {
        runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
    } else {
        source
    }

    try {
        val left = region.left.coerceIn(0, readable.width)
        val top = region.top.coerceIn(0, readable.height)
        val right = region.right.coerceIn(left, readable.width)
        val bottom = region.bottom.coerceIn(top, readable.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        // Never scale *up*: a feed strip narrower than the target is already small enough.
        val scale = minOf(1f, targetWidth.toFloat() / width)
        val outWidth = (width * scale).toInt().coerceAtLeast(1)
        val outHeight = (height * scale).toInt().coerceAtLeast(1)

        val frost = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(frost).drawBitmap(
            readable,
            Rect(left, top, right, bottom),
            RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return frost
    } catch (_: IllegalArgumentException) {
        return null
    } finally {
        // Only the copy is ours to free; the caller still owns what it handed us.
        if (readable !== source) readable.recycle()
    }
}
