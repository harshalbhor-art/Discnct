package com.discnct.app.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import com.discnct.app.ui.theme.ThemeMode

/** Which way round the cover is painted. Follows the user's light/dark choice, nothing else. */
enum class CoverTone { DARK, LIGHT }

/**
 * The cover's colour, from the app's theme setting.
 *
 * SYSTEM asks the device, and an *undefined* night mode counts as dark: Discnct is a dark-first app
 * and a cover that came out white on a phone that couldn't say would be the more startling mistake.
 */
fun coverToneFor(mode: ThemeMode, context: Context): CoverTone = when (mode) {
    ThemeMode.LIGHT -> CoverTone.LIGHT
    ThemeMode.DARK -> CoverTone.DARK
    ThemeMode.SYSTEM -> {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == Configuration.UI_MODE_NIGHT_NO) CoverTone.LIGHT else CoverTone.DARK
    }
}

/**
 * What the user sees where the feed was: a solid pane, and a card telling them to get out.
 *
 * This has been through two failed ideas, and both failed the same way. First a wireframe of
 * Instagram — empty post cards, blank story rings — measured off the real views underneath;
 * almost-aligned reads as a broken render, and the closer it got the worse the remaining error
 * looked. Then frosted glass made from a screenshot of the feed, shrunk and stretched back out:
 * that came out visibly pixelated, and because the still was frozen while the feed underneath was
 * not, the two drifted apart into a ghost of a scroll position that no longer existed.
 *
 * So the cover is solid now, and the lesson is that a block should not try to *resemble* what it is
 * covering. There is no alignment to get wrong, no staleness to drift, no capture to pay for, and —
 * because nothing is sampled from the screen any more — the accessibility service no longer needs
 * permission to take screenshots at all.
 *
 * Drawn with a plain [Canvas] rather than Compose. A ComposeView added straight to the
 * WindowManager needs a lifecycle owner, a saved-state registry and a recomposer attached by hand
 * before it will draw at all; for a pane and a card that is a lot of machinery to own and a lot of
 * ways to leak one.
 */
class FeedOverlayView(context: Context) : View(context) {

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    private val card = RectF()
    private val clip = Path()
    private val artwork = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var tone = CoverTone.DARK
    private var cachedPoster: Bitmap? = null
    private var posterMissing = false
    private var glowShaderSide = 0f

    /** Paint the cover dark or light. No effect if it's already that way round. */
    fun setTone(value: CoverTone) {
        if (tone == value) return
        tone = value
        glow.shader = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (tone == CoverTone.DARK) COVER_DARK else COVER_LIGHT)
        if (!layOutCard()) return

        val art = poster(card.width().toInt())
        if (art != null) {
            val radius = card.width() * CARD_CORNER_FRACTION
            canvas.save()
            clip.reset()
            clip.addRoundRect(card, radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            // Square source into a square card, so the scale is uniform and nothing distorts.
            // Nothing is stroked around it: the artwork is the card, and a border drawn over its
            // own edge was the outline that showed up on device.
            canvas.drawBitmap(art, null, card, artwork)
            canvas.restore()
        } else {
            // No artwork to find, so say it in words instead.
            drawGlow(canvas)
            drawHeadline(canvas)
        }
    }

    /**
     * The poster, decoded no larger than the card that will hold it.
     *
     * Two things make this worth more than a plain `getDrawable`. The art is 1254px square; decoded
     * whole on a phone whose card comes out smaller than that, it is megabytes of bitmap held for
     * the lifetime of an accessibility service to draw something smaller. And it's looked up by
     * name rather than by generated id, so the code compiles and runs whether or not the asset is
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
     * strip is smaller than that — a card that overhung the cover would be painted onto the live
     * app on either side of it. On a 1080px-wide phone the asked-for 1200 is wider than the feed
     * itself, so [CARD_MAX_FRACTION] is what it actually comes out at.
     */
    private fun layOutCard(): Boolean {
        val available = minOf(width, height) * CARD_MAX_FRACTION
        val side = minOf(CARD_SIZE_PX, available)
        if (side < MIN_CARD_PX) return false
        val cx = width / 2f
        val cy = height / 2f
        card.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        return true
    }

    /** The light at the end of it, sitting behind the words. */
    private fun drawGlow(canvas: Canvas) {
        val side = card.width()
        if (glowShaderSide != side || glow.shader == null) {
            glowShaderSide = side
            val dark = tone == CoverTone.DARK
            glow.shader = RadialGradient(
                card.centerX(),
                card.centerY(),
                side * 0.55f,
                intArrayOf(
                    if (dark) GLOW_CORE_DARK else GLOW_CORE_LIGHT,
                    if (dark) GLOW_MID_DARK else GLOW_MID_LIGHT,
                    if (dark) GLOW_EDGE_DARK else GLOW_EDGE_LIGHT,
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val radius = side * CARD_CORNER_FRACTION
        canvas.drawRoundRect(card, radius, radius, glow)
    }

    /** GET / OUT, as big as the card will take. */
    private fun drawHeadline(canvas: Canvas) {
        headline.color = if (tone == CoverTone.DARK) TEXT_ON_DARK else TEXT_ON_LIGHT
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

    private companion object {
        /** `res/drawable-nodpi/get_out.webp`. Absent, the card falls back to drawing the words. */
        const val POSTER_NAME = "get_out"

        const val LINE_ONE = "GET"
        const val LINE_TWO = "OUT"

        /** The card, as asked for: 1200×1200 with curved corners and no border of any kind. */
        const val CARD_SIZE_PX = 1200f

        /** Never more than this share of the cover, so the card can't reach past its edges. */
        const val CARD_MAX_FRACTION = 0.92f
        const val CARD_CORNER_FRACTION = 0.08f
        const val HEADLINE_WIDTH_FRACTION = 0.66f

        /** Below this the card is too small to read and the cover is better off plain. */
        const val MIN_CARD_PX = 200f

        /** Any size works — the text is scaled off what it measures at this one. */
        const val MEASURE_SIZE = 100f

        // Opaque, both of them. The point of dropping the screenshot was that the feed underneath
        // stops being visible at all, and anything short of full alpha leaves it faintly readable.
        const val COVER_DARK = 0xFF000000.toInt()
        const val COVER_LIGHT = 0xFFFFFFFF.toInt()

        const val TEXT_ON_DARK = 0xFFF2F2F2.toInt()
        const val TEXT_ON_LIGHT = 0xFF101010.toInt()

        const val GLOW_CORE_DARK = 0x33FFFFFF
        const val GLOW_MID_DARK = 0x14FFFFFF
        const val GLOW_EDGE_DARK = 0x00FFFFFF

        const val GLOW_CORE_LIGHT = 0x22000000
        const val GLOW_MID_LIGHT = 0x0D000000
        const val GLOW_EDGE_LIGHT = 0x00000000
    }
}
