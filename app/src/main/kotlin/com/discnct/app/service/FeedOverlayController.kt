package com.discnct.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import com.discnct.app.cover.CoverSource
import com.discnct.app.feed.FeedDetection
import com.discnct.app.feed.FeedRegion
import kotlin.math.abs

/**
 * Owns the single window drawn over a covered feed.
 *
 * Touch handling is the reason this is a window sized to the feed rather than a full-screen one: a
 * window swallows every touch inside its own bounds and passes on nothing, all or nothing, with no
 * way to be selectively transparent. Sized to the feed that is exactly the behaviour we want — the
 * feed can't be scrolled, while the top bar, the bottom navigation and everything around them still
 * work. Sized to the screen it would be an app block, which is the level above this one.
 *
 * Three things here are not obvious:
 *
 *  * **The cover is painted, never requested from the system.** The tempting way to frost a feed is
 *    [WindowManager.LayoutParams.blurBehindRadius], and it is wrong here: blur-behind goes the same
 *    way as `FLAG_DIM_BEHIND` and applies to everything behind the window rather than the window's
 *    own rectangle, so a cover correctly sized to the feed still frosted the bottom navigation, and
 *    nothing bounds it. What [FeedOverlayView] draws inside its own bounds cannot escape them.
 *  * **Appearing is animated, and so is leaving.** A cover that snaps on mid-scroll reads as a
 *    glitch in Instagram; the same cover fading up over half a second reads as something arriving
 *    on purpose. Reversing mid-fade travels only the distance that's left.
 *  * **Covering a feed has to silence it too.** A reel under the cover keeps playing, and its
 *    controls are behind a window that eats every touch. [FeedSilencer] holds audio focus for
 *    exactly as long as the cover is up.
 *
 * Every method must be called from the main thread; [WindowManager] and [ValueAnimator] both need
 * it, and the accessibility service's callbacks already arrive there.
 */
class FeedOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val silencer = FeedSilencer(context)

    private var view: FeedOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var region: FeedRegion? = null

    private var fade: ValueAnimator? = null

    /** Where the current fade is heading: 1 covered, 0 gone. */
    private var goal = 0f

    /** Where it has got to. 0 = fully faded out, 1 = fully covered. */
    private var progress = 0f

    /** True while anything is on screen, including a cover that is still fading out. */
    val isShowing: Boolean get() = view != null

    /**
     * Cover what [detection] describes, adding the window or moving the one already up. Silently
     * does nothing without the overlay permission — the setup flow asks for it, but it can be
     * revoked at any time and a cover that crashed on being switched off would be worse than one
     * that stops working.
     *
     * @param tone which way round to paint it, from the user's light/dark setting. Passed on every
     *   call so a theme change while the cover is up is picked up on the next scan.
     * @param art the picture for the app being covered. Passed on every call for the same reason as
     *   the tone: changing it in the picker has to show up without taking the cover down first.
     */
    fun show(detection: FeedDetection, tone: CoverTone, art: CoverSource?) {
        if (!Settings.canDrawOverlays(context)) return
        val bounds = detection.feedRegion
        if (bounds.isEmpty) {
            hide()
            return
        }

        val existing = view
        if (existing == null) {
            val fresh = FeedOverlayView(context).apply {
                setTone(tone)
                setArt(art)
                alpha = 0f
            }
            val freshParams = layoutParamsFor(bounds)
            val added = runCatching { windowManager.addView(fresh, freshParams) }.isSuccess
            if (!added) return
            view = fresh
            params = freshParams
            region = bounds
            progress = 0f
            goal = 0f
            silencer.silence()
        } else {
            existing.setTone(tone)
            existing.setArt(art)
            if (region != bounds) {
                val current = params ?: return
                current.x = bounds.left
                current.y = bounds.top
                current.width = bounds.width
                current.height = bounds.height
                runCatching { windowManager.updateViewLayout(existing, current) }
                region = bounds
            }
        }
        fadeTo(1f)
    }

    /** Fade the cover out and take it down when it's gone. Safe to call when nothing is showing. */
    fun hide() {
        if (view == null) return
        fadeTo(0f)
    }

    /**
     * Take the window down now, without the fade.
     *
     * For teardown only. Animating a window whose service is being destroyed would leave it on
     * screen with nothing left alive to remove it.
     */
    fun dispose() {
        fade?.cancel()
        fade = null
        detach()
    }

    private fun fadeTo(to: Float) {
        if (goal == to && fade != null) return
        goal = to
        fade?.cancel()
        fade = null

        val from = progress
        if (from == to) {
            if (to == 0f) detach()
            return
        }

        // Part-way through a fade the other way, only the remaining distance is left to travel;
        // replaying the full half-second from there would drag.
        val span = (FADE_MILLIS * abs(to - from)).toLong().coerceAtLeast(1L)
        var cancelled = false
        fade = ValueAnimator.ofFloat(from, to).apply {
            duration = span
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    fade = null
                    if (to == 0f) detach()
                }
            })
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        view?.alpha = value
    }

    private fun detach() {
        val going = view ?: return
        view = null
        params = null
        region = null
        progress = 0f
        goal = 0f
        runCatching { windowManager.removeView(going) }
        silencer.release()
    }

    private fun layoutParamsFor(bounds: FeedRegion): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            bounds.width,
            bounds.height,
            bounds.left,
            bounds.top,
            // minSdk is 26, so the overlay type that survived the O clampdown is always available.
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: the host app keeps input focus, so the keyboard and the back gesture
            // behave normally. Not NOT_TOUCHABLE, though — swallowing touches over the feed is the
            // entire point, and without it the user would scroll an invisible feed under the cover.
            //
            // LAYOUT_IN_SCREEN is the one that has to be here. The coordinates come from
            // AccessibilityNodeInfo.getBoundsInScreen(), which measures from the top of the
            // *display*; without this flag the window is positioned inside the content area
            // instead, which starts below the status bar. The result is the whole cover sitting
            // exactly one status bar too low.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            // Translucent, not opaque: the fade needs the window to composite with what's under it.
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private companion object {
        /** Half a second each way, as asked for. */
        const val FADE_MILLIS = 500f
    }
}
