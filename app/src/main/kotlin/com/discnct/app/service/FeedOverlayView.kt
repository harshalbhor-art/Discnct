package com.discnct.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/**
 * What the user sees where the feed was: a few empty blocks and one line of text.
 *
 * Deliberately blank. We can't edit the host app's views — nothing can, short of rooting the phone
 * — so the feed isn't being hidden so much as covered, and the covering may as well say something.
 * Skeleton placeholders read as "this is deliberately empty" where a plain black rectangle reads as
 * a crash, and an empty block is a fair picture of what was there anyway.
 *
 * Drawn with a plain [Canvas] rather than Compose. A ComposeView added straight to the
 * WindowManager needs a lifecycle owner, a saved-state registry and a recomposer attached by hand
 * before it will draw at all; for a handful of rounded rectangles that is a lot of machinery to own
 * and a lot of ways to leak one.
 */
@SuppressLint("ViewConstructor")
class FeedOverlayView(context: Context) : View(context) {

    // Not named `background`: View already has getBackground(), and a Kotlin property of that name
    // generates a getter with the same JVM signature and a different return type.
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BLACK }
    private val block = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SURFACE_RAISED }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

        val side = dp(20f)
        val gap = dp(16f)
        val radius = dp(8f)
        val blockHeight = dp(132f)
        val captionHeight = dp(64f)

        // Fill the region with as many blocks as fit, then hand the leftover strip to the caption.
        var top = gap
        val usable = height - captionHeight
        while (top + blockHeight <= usable) {
            rect.set(side, top, width - side, top + blockHeight)
            canvas.drawRoundRect(rect, radius, radius, block)
            canvas.drawRoundRect(rect, radius, radius, stroke)
            top += blockHeight + gap
        }

        val baseline = height - captionHeight / 2f - (caption.descent() + caption.ascent()) / 2f
        canvas.drawText(CAPTION, width / 2f, baseline, caption)
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
        const val COLOR_SURFACE_RAISED = 0xFF1A1A1A.toInt()
        const val COLOR_BORDER = 0xFF222222.toInt()
        const val COLOR_TEXT_DISABLED = 0xFF666666.toInt()
    }
}
