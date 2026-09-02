package com.discnct.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Three square-ended columns, drawn here rather than pulled from `material-icons-extended` — that
 * artifact is thousands of icons for the sake of one, and Material's rounded chart glyph would sit
 * oddly next to the app's square-block data language anyway.
 *
 * Built once and cached, matching how Material's own icon accessors behave.
 */
val StatsBarsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StatsBars",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Ascending heights on a shared baseline: 4dp, 10dp, 16dp tall, 4dp wide, 3dp apart.
        bar(x = 4f, top = 16f)
        bar(x = 10f, top = 10f)
        bar(x = 16f, top = 4f)
    }.build()
}

private fun ImageVector.Builder.bar(x: Float, top: Float) {
    path(fill = SolidColor(Color.White)) {
        moveTo(x, top)
        lineTo(x + 4f, top)
        lineTo(x + 4f, 20f)
        lineTo(x, 20f)
        close()
    }
}
