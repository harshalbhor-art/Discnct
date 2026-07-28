package com.discnct.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * What the user sees where the feed was: the shape of the app with nothing in it.
 *
 * We can't edit the host app's views — nothing can, short of rooting the phone — so the feed isn't
 * being hidden so much as covered, and what we cover it with matters. A flat black rectangle over
 * half of Instagram reads as a crash. The wireframe of a post with no post in it reads as what it
 * is: the thing you came for is deliberately not here.
 *
 * Two shapes, because the two strips we cover look nothing alike: [Kind.STORIES] draws the empty
 * rings of a stories tray, [Kind.FEED] draws empty post cards.
 *
 * Drawn with a plain [Canvas] rather than Compose. A ComposeView added straight to the
 * WindowManager needs a lifecycle owner, a saved-state registry and a recomposer attached by hand
 * before it will draw at all; for a handful of rounded rectangles that is a lot of machinery to own
 * and a lot of ways to leak one.
 */
@SuppressLint("ViewConstructor")
class FeedOverlayView(context: Context, private val kind: Kind) : View(context) {

    /** Which of the two covered strips this window is drawing. */
    enum class Kind { FEED, STORIES }

    // Not named `background`: View already has getBackground(), and a Kotlin property of that name
    // generates a getter with the same JVM signature and a different return type.
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BLACK }
    private val skeleton = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SURFACE_RAISED }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BORDER
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_DISABLED
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backdrop.color)
        when (kind) {
            Kind.FEED -> drawFeed(canvas)
            Kind.STORIES -> drawStories(canvas)
        }
    }

    /** Empty post cards, as many as fit, with the line about them at the bottom. */
    private fun drawFeed(canvas: Canvas) {
        val margin = dp(14f)
        val captionBand = dp(56f)
        val usable = height - captionBand

        var top = dp(10f)
        while (usable - top >= dp(120f)) {
            top = drawPostCard(canvas, top, margin, usable) + dp(22f)
        }

        val baseline = height - captionBand / 2f - (caption.descent() + caption.ascent()) / 2f
        canvas.drawText(CAPTION, width / 2f, baseline, caption)
    }

    /**
     * One post: avatar and handle, the media square, the row of actions, two caption lines. Returns
     * the y it finished at, and bails out early rather than drawing past [limit] — a card near the
     * bottom is better truncated than spilling over the message.
     */
    private fun drawPostCard(canvas: Canvas, start: Float, margin: Float, limit: Float): Float {
        var y = start
        val avatar = dp(32f)

        roundRect(canvas, margin, y, margin + avatar, y + avatar, avatar / 2f)
        val textLeft = margin + avatar + dp(10f)
        roundRect(canvas, textLeft, y + dp(5f), textLeft + dp(110f), y + dp(15f), dp(5f))
        roundRect(canvas, textLeft, y + dp(20f), textLeft + dp(70f), y + dp(28f), dp(4f))
        y += avatar + dp(12f)

        // Media. Square like the real thing, but never so tall it pushes the rest off the card.
        val mediaHeight = minOf(width - margin * 2, (limit - y) * 0.62f)
        if (mediaHeight < dp(40f)) return y
        rect.set(margin, y, width - margin, y + mediaHeight)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), skeleton)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), outline)
        y += mediaHeight + dp(12f)

        // Like / comment / share on the left, save on the right.
        val icon = dp(22f)
        if (y + icon > limit) return y
        var x = margin
        repeat(3) {
            roundRect(canvas, x, y, x + icon, y + icon, dp(5f))
            x += icon + dp(14f)
        }
        roundRect(canvas, width - margin - icon, y, width - margin, y + icon, dp(5f))
        y += icon + dp(12f)

        if (y + dp(10f) > limit) return y
        roundRect(canvas, margin, y, margin + (width - margin * 2) * 0.62f, y + dp(10f), dp(5f))
        y += dp(16f)
        if (y + dp(10f) <= limit) {
            roundRect(canvas, margin, y, margin + (width - margin * 2) * 0.38f, y + dp(10f), dp(5f))
            y += dp(10f)
        }
        return y
    }

    /** The stories tray with nobody in it: empty rings and a blank name under each. */
    private fun drawStories(canvas: Canvas) {
        val margin = dp(8f)
        val diameter = minOf(dp(62f), height - dp(30f))
        if (diameter <= 0f) return
        val gap = dp(16f)
        val top = ((height - dp(14f)) - diameter) / 2f

        var x = margin
        while (x + diameter <= width - margin) {
            val cx = x + diameter / 2f
            val cy = top + diameter / 2f
            canvas.drawCircle(cx, cy, diameter / 2f, skeleton)
            canvas.drawCircle(cx, cy, diameter / 2f, outline)
            val labelWidth = diameter * 0.62f
            roundRect(
                canvas,
                cx - labelWidth / 2f,
                top + diameter + dp(6f),
                cx + labelWidth / 2f,
                top + diameter + dp(13f),
                dp(4f),
            )
            x += diameter + gap
        }
    }

    private fun roundRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, skeleton)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val CAPTION = "In the grand scheme of things, these are insignificant."

        // Design-system tokens, inlined: this View is created by the accessibility service and has
        // no Compose theme to read them from.
        const val COLOR_BLACK = 0xFF000000.toInt()
        const val COLOR_SURFACE_RAISED = 0xFF141414.toInt()
        const val COLOR_BORDER = 0xFF242424.toInt()
        const val COLOR_TEXT_DISABLED = 0xFF666666.toInt()
    }
}
