package com.discnct.app.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors

/**
 * The little magnifier that lives in the corner of a section's top bar. Toggles into an X while
 * the search field is open so the same control both opens and closes it.
 */
@Composable
fun SearchIconButton(active: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDiscnctColors.current
    Box(
        modifier = modifier
            .clip(DiscnctShapes.pill)
            .clickable(onClick = onToggle)
            .padding(6.dp),
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.Close else Icons.Filled.Search,
            contentDescription = if (active) "Close search" else "Search apps",
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Single-line search field shown under the top bar while search is open. Auto-focuses so the
 * keyboard comes straight up, matching the monochrome pill styling used elsewhere.
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDiscnctColors.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = DiscnctType.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(DiscnctShapes.pill)
            .background(colors.surface)
            .border(1.dp, colors.borderVisible, DiscnctShapes.pill)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            if (query.isEmpty()) {
                Text(
                    text = "Search apps",
                    style = DiscnctType.body,
                    color = colors.textDisabled,
                )
            }
            innerTextField()
        },
    )
}

/** Case-insensitive label match used to filter the app lists while searching. */
fun List<AppRow>.filteredBy(query: String): List<AppRow> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { it.label.contains(trimmed, ignoreCase = true) }
}
