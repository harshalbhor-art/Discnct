package com.discnct.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * What the user sees where the feed was: frosted glass, and a card telling them to get out.
 *
 * The version before this one drew a wireframe of Instagram — empty post cards, blank story rings —
 * measured off the real views underneath. It was a nice idea and it did not survive contact with a
 * real phone: a shell that is *almost* aligned with the app behind it reads as a broken render, and
 * the closer it gets to right the more the remaining error stands out. So the shell is gone. One
 * pane of dark glass over the whole scrolling surface has no alignment to get wrong, and it says
 * the thing more plainly anyway.
 *
 * The blur itself is not drawn here — it's [WindowManager.LayoutParams.blurBehindRadius], applied
 * by the compositor to whatever is behind this window, which is the only way to blur pixels we
 * don't own. All this view contributes is the tint on top: enough to make the feed unreadable, not
 * so much that the app disappears and the cover reads as a crash. How dark that tint is depends on
 * whether the blur is actually live — see [frosted].
 *
 * Drawn with a plain [Canvas] rather than Compose. A ComposeView added straight to the
 * WindowManager needs a lifecycle owner, a saved-state registry and a recomposer attached by hand
 * before it will draw at all; for a scrim and a card that is a lot of machinery to own and a lot of
 * ways to leak one.
 */
class FeedOverlayView(context: Context) : View(context) {

    /**
     * True when the system is blurring behind this window.
     *
     * Cross-window blur needs Android 12 and can be switched off at any time — by battery saver, by
     * the developer options toggle, or by the device simply not supporting it. Without it the tint
     * is the only thing standing between the user and the feed, so it goes almost opaque; with it,
     * it can stay light enough that the shape of the app still shows through the glass.
     */
    var frosted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    // Not named `background`: View already has getBackground(), and a Kotlin property of that name
    // generates a getter with the same JVM signature and a different return type.
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CARD_EDGE
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    private val card = RectF()
    private val clip = Path()
    private val artwork = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var cachedPoster: Bitmap? = null
    private var posterMissing = false
    private var glowShaderSide = 0f

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (frosted) SCRIM_FROSTED else SCRIM_FLAT)
        if (!layOutCard()) return

        val radius = card.width() * CARD_CORNER_FRACTION
        val art = poster(card.width().toInt())
        if (art != null) {
            canvas.save()
            clip.reset()
            clip.addRoundRect(card, radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            // Square source into a square card, so the scale is uniform and nothing distorts.
            canvas.drawBitmap(art, null, card, artwork)
            canvas.restore()
        } else {
            canvas.drawRoundRect(card, radius, radius, glass)
            drawGlow(canvas)
            drawHeadline(canvas)
        }
        canvas.drawRoundRect(card, radius, radius, edge)
    }

    /**
     * The poster, decoded no larger than the card that will hold it.
     *
     * Two things make this worth more than a plain `getDrawable`. The art is 1254px square and the
     * card is 500; decoded whole that is six megabytes of bitmap held for the lifetime of an
     * accessibility service, to draw something a fifth of the size. And it's looked up by name
     * rather than by generated id, so the code compiles and runs whether or not the asset is
     * there — with nothing to find, [drawHeadline] draws the words instead.
     */
    private fun poster(side: Int): Bitmap? {
        cachedPoster?.let { return it }
        if (posterMissing || side <= 0) return null
        val id = resources.getIdentifier(POSTER_NAME, "drawable", context.packageName)
        if (id == 0) {
            posterMissing = true
            return null
        }
        val measured = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, measured)
        val decoded = runCatching {
            BitmapFactory.decodeResource(
                resources,
                id,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(measured.outWidth, side)
                    inScaled = false
                },
            )
        }.getOrNull()
        if (decoded == null) posterMissing = true
        cachedPoster = decoded
        return decoded
    }

    /** The largest power-of-two downscale that still leaves the bitmap wider than [target]. */
    private fun sampleSizeFor(source: Int, target: Int): Int {
        if (source <= 0 || target <= 0) return 1
        var sample = 1
        while (source / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * Size and centre the card. Square, [CARD_SIZE_PX] on a side, shrunk to fit when the covered
     * strip is smaller than that — a card that overhangs the cover would be painted onto the live
     * app on either side of it.
     */
    private fun layOutCard(): Boolean {
        val available = minOf(width, height) * CARD_MAX_FRACTION
        val side = minOf(CARD_SIZE_PX, available)
        if (side < dp(80f)) return false
        val cx = width / 2f
        val cy = height / 2f
        card.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        return true
    }

    /** The light at the end of it, sitting behind the words. */
    private fun drawGlow(canvas: Canvas) {
        val side = card.width()
        if (glowShaderSide != side) {
            glowShaderSide = side
            glow.shader = RadialGradient(
                card.centerX(),
                card.centerY(),
                side * 0.55f,
                intArrayOf(GLOW_CORE, GLOW_MID, GLOW_EDGE),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val radius = side * CARD_CORNER_FRACTION
        canvas.drawRoundRect(card, radius, radius, glow)
    }

    /** GET / OUT, as big as the card will take. */
    private fun drawHeadline(canvas: Canvas) {
        val target = card.width() * HEADLINE_WIDTH_FRACTION
        headline.textSize = MEASURE_SIZE
        val widest = maxOf(headline.measureText(LINE_ONE), headline.measureText(LINE_TWO))
        if (widest <= 0f) return
        headline.textSize = MEASURE_SIZE * target / widest

        val line = headline.descent() - headline.ascent()
        val block = line * 2f
        val first = card.centerY() - block / 2f - headline.ascent()
        canvas.drawText(LINE_ONE, card.centerX(), first, headline)
        canvas.drawText(LINE_TWO, card.centerX(), first + line, headline)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        /** `res/drawable-nodpi/get_out.webp`. Absent, the card falls back to drawing the words. */
        const val POSTER_NAME = "get_out"

        const val LINE_ONE = "GET"
        const val LINE_TWO = "OUT"

        /** The card, as asked for: 500×500 with curved corners. */
        const val CARD_SIZE_PX = 500f

        /** Never more than this share of the cover, so the card can't reach past its edges. */
        const val CARD_MAX_FRACTION = 0.8f
        const val CARD_CORNER_FRACTION = 0.08f
        const val HEADLINE_WIDTH_FRACTION = 0.66f

        /** Any size works — the text is scaled off what it measures at this one. */
        const val MEASURE_SIZE = 100f

        // The tint over the blurred feed. Dark enough that nothing is readable through it; light
        // enough that the app is visibly still there, behind glass, rather than gone.
        const val SCRIM_FROSTED = 0xC4000000.toInt()

        /** No blur to hide behind, so the tint has to do all of it on its own. */
        const val SCRIM_FLAT = 0xEB000000.toInt()

        const val CARD_FILL = 0x1FFFFFFF
        const val CARD_EDGE = 0x40FFFFFF
        const val TEXT = 0xFFF2F2F2.toInt()

        const val GLOW_CORE = 0x33FFFFFF
        const val GLOW_MID = 0x14FFFFFF
        const val GLOW_EDGE = 0x00FFFFFF
    }
}
