package com.discnct.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.discnct.app.feed.FeedRegion

/**
 * Owns the single window drawn over a blocked feed.
 *
 * Touch handling is the reason this is one window sized to the feed rather than a full-screen one:
 * a window swallows every touch inside its own bounds and passes on nothing, all or nothing, with
 * no way to be selectively transparent. Sized to the feed that is exactly the behaviour we want —
 * the feed can't be scrolled, and the nav bar, DM button and everything else around it still work.
 * Sized to the screen it would be an app block.
 *
 * Every method must be called from the main thread; [WindowManager] is not thread-safe and the
 * accessibility service's callbacks already arrive there.
 */
class FeedOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: FeedOverlayView? = null
    private var shownRegion: FeedRegion? = null

    /** True while a feed is actually covered. */
    val isShowing: Boolean get() = view != null

    /**
     * Cover [region], adding the window or moving the existing one. Silently does nothing without
     * the overlay permission — the setup flow asks for it, but it can be revoked at any time and a
     * blocker that crashed on being switched off would be worse than one that stops working.
     */
    fun show(region: FeedRegion) {
        if (region.isEmpty) return hide()
        if (!Settings.canDrawOverlays(context)) return

        if (region == shownRegion && view != null) return

        val existing = view
        if (existing == null) {
            val created = FeedOverlayView(context)
            runCatching { windowManager.addView(created, layoutParamsFor(region)) }
                .onSuccess {
                    view = created
                    shownRegion = region
                }
        } else {
            runCatching { windowManager.updateViewLayout(existing, layoutParamsFor(region)) }
                .onSuccess { shownRegion = region }
        }
    }

    /** Take the overlay down. Safe to call when nothing is showing. */
    fun hide() {
        val current = view ?: return
        view = null
        shownRegion = null
        runCatching { windowManager.removeView(current) }
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
}
