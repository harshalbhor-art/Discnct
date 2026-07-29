package com.discnct.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.RequiresApi
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
 * Two things here are not obvious:
 *
 *  * **The blur belongs to the window, not the view.** Nothing we draw can blur pixels we don't
 *    own; [WindowManager.LayoutParams.blurBehindRadius] asks the compositor to do it for us. It
 *    needs Android 12, a translucent window, and cross-window blur to be switched on — none of
 *    which is guaranteed, so [FeedOverlayView.frosted] carries the answer through to the tint.
 *  * **Appearing is animated, and so is leaving.** A cover that snaps on mid-scroll reads as a
 *    glitch in Instagram; the same cover fading up over half a second reads as something arriving
 *    on purpose. Both the view's alpha and the blur radius are driven off one animator, so the
 *    glass thickens as the tint darkens instead of the blur popping in fully formed.
 *
 * Every method must be called from the main thread; [WindowManager] and [ValueAnimator] both need
 * it, and the accessibility service's callbacks already arrive there.
 */
class FeedOverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: FeedOverlayView? = null
    private var params: WindowManager.LayoutParams? = null
    private var region: FeedRegion? = null

    private var fade: ValueAnimator? = null

    /** Where the current fade is heading: 1 covered, 0 gone. */
    private var goal = 0f

    /** Where it has got to. 0 = fully faded out, 1 = fully covered. */
    private var progress = 0f

    private var blurring = false
    private var appliedBlur = -1

    /** True while anything is on screen, including a cover that is still fading out. */
    val isShowing: Boolean get() = view != null

    /**
     * Cover what [detection] describes, adding the window or moving the one already up. Silently
     * does nothing without the overlay permission — the setup flow asks for it, but it can be
     * revoked at any time and a cover that crashed on being switched off would be worse than one
     * that stops working.
     */
    fun show(detection: FeedDetection) {
        if (!Settings.canDrawOverlays(context)) return
        val bounds = detection.feedRegion
        if (bounds.isEmpty) {
            hide()
            return
        }

        val existing = view
        if (existing == null) {
            blurring = blurAvailable()
            val fresh = FeedOverlayView(context).apply {
                frosted = blurring
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
            appliedBlur = -1
        } else if (region != bounds) {
            val current = params ?: return
            current.x = bounds.left
            current.y = bounds.top
            current.width = bounds.width
            current.height = bounds.height
            runCatching { windowManager.updateViewLayout(existing, current) }
            region = bounds
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
        if (blurring) applyBlur((BLUR_RADIUS_PX * value).toInt())
    }

    /**
     * Push the blur radius to the window, in steps.
     *
     * Every change is a [WindowManager.updateViewLayout], which relayouts the window — at sixty a
     * second for half a second that is a lot of churn for a difference nobody can see. Rounding to
     * [BLUR_STEP_PX] cuts it to a handful of updates and looks identical.
     */
    private fun applyBlur(radius: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val current = view ?: return
        val currentParams = params ?: return
        val stepped = (radius / BLUR_STEP_PX) * BLUR_STEP_PX
        if (stepped == appliedBlur) return
        appliedBlur = stepped
        setBlurRadius(currentParams, stepped)
        runCatching { windowManager.updateViewLayout(current, currentParams) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setBlurRadius(target: WindowManager.LayoutParams, radius: Int) {
        target.blurBehindRadius = radius
    }

    private fun detach() {
        val going = view ?: return
        view = null
        params = null
        region = null
        progress = 0f
        goal = 0f
        appliedBlur = -1
        runCatching { windowManager.removeView(going) }
    }

    private fun blurAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && crossWindowBlurEnabled()

    @RequiresApi(Build.VERSION_CODES.S)
    private fun crossWindowBlurEnabled(): Boolean =
        runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)

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
            // entire point, and without it the user would scroll an invisible feed under the glass.
            //
            // LAYOUT_IN_SCREEN is the one that has to be here. The coordinates come from
            // AccessibilityNodeInfo.getBoundsInScreen(), which measures from the top of the
            // *display*; without this flag the window is positioned inside the content area
            // instead, which starts below the status bar. The result is the whole cover sitting
            // exactly one status bar too low.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                if (blurring) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0,
            // Translucent, not opaque: both the fade and the blur behind need the window to
            // actually composite with what is under it.
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private companion object {
        /** Half a second each way, as asked for. */
        const val FADE_MILLIS = 500f

        /** Enough to make text unreadable while leaving the shape of the app recognisable. */
        const val BLUR_RADIUS_PX = 72f
        const val BLUR_STEP_PX = 6
    }
}
