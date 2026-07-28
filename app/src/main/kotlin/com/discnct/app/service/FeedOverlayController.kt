package com.discnct.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.discnct.app.feed.FeedDetection
import com.discnct.app.feed.FeedRegion

/**
 * Owns the windows drawn over a covered feed.
 *
 * Touch handling is the reason these are windows sized to the strips they cover rather than one
 * full-screen window: a window swallows every touch inside its own bounds and passes on nothing,
 * all or nothing, with no way to be selectively transparent. Sized to the feed that is exactly the
 * behaviour we want — the feed can't be scrolled, while the top bar, the bottom navigation, the
 * user's own story slot and everything else around it still work. Sized to the screen it would be
 * an app block, which is the level above this one.
 *
 * Two windows for the same reason: the slot of the stories tray we leave live sits *inside* the
 * area we want covered, and a single rectangle can't have a hole in it.
 *
 * Every method must be called from the main thread; [WindowManager] is not thread-safe and the
 * accessibility service's callbacks already arrive there.
 */
class FeedOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var feed: Pane? = null
    private var stories: Pane? = null

    /** True while anything is actually covered. */
    val isShowing: Boolean get() = feed != null || stories != null

    /**
     * Cover what [detection] describes, adding windows or moving the ones already up. Silently does
     * nothing without the overlay permission — the setup flow asks for it, but it can be revoked at
     * any time and a cover that crashed on being switched off would be worse than one that stops
     * working.
     */
    fun show(detection: FeedDetection) {
        if (!Settings.canDrawOverlays(context)) return
        feed = reposition(feed, detection.feedRegion, FeedOverlayView.Kind.FEED)
        stories = reposition(stories, detection.storiesRegion, FeedOverlayView.Kind.STORIES)
    }

    /** Take everything down. Safe to call when nothing is showing. */
    fun hide() {
        feed = remove(feed)
        stories = remove(stories)
    }

    private fun reposition(
        pane: Pane?,
        region: FeedRegion?,
        kind: FeedOverlayView.Kind,
    ): Pane? {
        if (region == null || region.isEmpty) return remove(pane)
        if (pane == null) {
            val view = FeedOverlayView(context, kind)
            return runCatching { windowManager.addView(view, layoutParamsFor(region)) }
                .map { Pane(view, region) }
                .getOrNull()
        }
        if (pane.region == region) return pane
        return runCatching { windowManager.updateViewLayout(pane.view, layoutParamsFor(region)) }
            .map { Pane(pane.view, region) }
            .getOrElse { pane }
    }

    private fun remove(pane: Pane?): Pane? {
        pane ?: return null
        runCatching { windowManager.removeView(pane.view) }
        return null
    }

    private fun layoutParamsFor(region: FeedRegion): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            region.width,
            region.height,
            region.left,
            region.top,
            // minSdk is 26, so the overlay type that survived the O clampdown is always available.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the host app keeps input focus, so the keyboard and the back gesture
            // behave normally. Not NOT_TOUCHABLE, though — swallowing touches over the feed is the
            // entire point, and without it the user would scroll an invisible feed under a picture
            // of an empty one.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    /** One window and the rectangle it is currently sitting on. */
    private class Pane(val view: FeedOverlayView, val region: FeedRegion)
}
