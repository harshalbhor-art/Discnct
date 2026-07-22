@file:OptIn(ExperimentalTextApi::class)

package com.discnct.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.discnct.app.R

/** Display face: variable dot-matrix font, hero numbers only (36px+, never body text). */
val Doto = FontFamily(
    Font(
        R.font.doto_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Body / UI face: variable, used at Regular/Medium/Bold. */
val SpaceGrotesk = FontFamily(
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Data / label face: static, ALL CAPS labels and numeric readouts. */
val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, weight = FontWeight.Normal),
    Font(R.font.space_mono_bold, weight = FontWeight.Bold),
)

/**
 * The design system's type scale, kept as a flat set of [TextStyle]s rather than
 * [androidx.compose.material3.Typography] roles — the token names (display-xl,
 * label, ...) don't map cleanly onto Material's semantic slots.
 */
object DiscnctType {
    val displayXl = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.03f).em)
    val displayLg = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.02f).em)
    val displayMd = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02f).em)
    val heading = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.01f).em)
    val subheading = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp)
    val body = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.01f.em)
    val caption = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.04f.em)
    val label = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.08f.em)
}
