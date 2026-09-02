package com.discnct.app.ui.applist

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.discnct.app.cover.BUILTIN_COVER_ART
import com.discnct.app.cover.CoverSource
import com.discnct.app.service.CoverImageStore
import com.discnct.app.ui.home.SectionTopBar
import com.discnct.app.ui.theme.DiscnctShapes
import com.discnct.app.ui.theme.DiscnctType
import com.discnct.app.ui.theme.LocalDiscnctColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks the picture that sits in the middle of one app's feed cover.
 *
 * Per app, not global, so the screen leads with which app it is editing — without that it's a
 * grid of images with no way to tell what you're changing.
 *
 * Choosing applies immediately. There is no save button anywhere else in Discnct and adding one
 * here would be the odd screen out; the preview at the top is what confirms the choice landed.
 */
@Composable
fun CoverImageScreen(
    packageName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AppListViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalDiscnctColors.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CoverImageStore(context.applicationContext) }

    val selected by store.coverSource(packageName).collectAsStateWithLifecycle(initialValue = null)
    val row = state.rows.firstOrNull { it.packageName == packageName }

    // Imported pictures are files rather than a Flow, so the list is re-read whenever it changes
    // under us. Bumping the counter is what re-reads it.
    var libraryVersion by remember { mutableIntStateOf(0) }
    val userImages by produceState(initialValue = emptyList<CoverSource.UserImage>(), libraryVersion) {
        value = withContext(Dispatchers.IO) { store.userImages() }
    }
    var importFailed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = store.importImage(uri)
            if (added == null) {
                importFailed = true
            } else {
                importFailed = false
                store.setCover(packageName, added)
                libraryVersion++
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.black)) {
        SectionTopBar(title = "Change image", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp),
                ) {
                    if (row != null) {
                        Image(
                            bitmap = row.icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp).clip(DiscnctShapes.cardCompact),
                        )
                    }
                    Text(
                        text = "FOR ${(row?.label ?: packageName).uppercase()}",
                        style = DiscnctType.caption,
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = "The picture shown on the cover that hides this app's feed. Each app " +
                        "keeps its own, and the cover itself doesn't change — it stays a solid " +
                        "pane with this square in the middle.",
                    style = DiscnctType.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
                )
            }

            item {
                SubHeader("Preview")
                CoverPreview(source = selected, store = store)
            }

            item { SubHeader("Defaults") }
            item {
                CoverGrid(
                    tiles = BUILTIN_COVER_ART.map { art ->
                        CoverTile(
                            source = CoverSource.Builtin(art.drawableName),
                            caption = art.label,
                            deletable = false,
                        )
                    },
                    selected = selected,
                    store = store,
                    onPick = { scope.launch { store.setCover(packageName, it) } },
                    onDelete = {},
                )
            }

            item { SubHeader("Yours") }
            item {
                AddImageRow(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            if (importFailed) {
                item {
                    Text(
                        text = "That picture couldn't be read. Try a different one.",
                        style = DiscnctType.bodySmall,
                        color = colors.warning,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            if (userImages.isNotEmpty()) {
                item {
                    CoverGrid(
                        tiles = userImages.map { CoverTile(source = it, caption = "Added", deletable = true) },
                        selected = selected,
                        store = store,
                        onPick = { scope.launch { store.setCover(packageName, it) } },
                        onDelete = { image ->
                            scope.launch {
                                store.deleteUserImage(image)
                                libraryVersion++
                            }
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Tapping an image applies it straight away. Deleting one you're using " +
                        "puts this app back on its default.",
                    style = DiscnctType.label,
                    color = colors.textDisabled,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
                )
            }
        }
    }
}

private data class CoverTile(
    val source: CoverSource,
    val caption: String,
    val deletable: Boolean,
)

/**
 * The cover as it will actually look: the opaque pane, with the picture in a square card centred
 * on it. Short enough not to push the grid off screen, tall enough to judge the crop.
 */
@Composable
private fun CoverPreview(source: CoverSource?, store: CoverImageStore) {
    val colors = LocalDiscnctColors.current
    val bitmap = rememberCoverBitmap(source, store)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(200.dp)
            .clip(DiscnctShapes.card)
            .background(colors.black)
            .border(1.dp, colors.border, DiscnctShapes.card),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(150.dp).clip(DiscnctShapes.card),
            )
        } else {
            // Exactly what the overlay falls back to when there's no artwork to find.
            Text(
                text = "GET\nOUT",
                style = DiscnctType.heading,
                color = colors.textDisplay,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

/** Four to a row, square, captioned. Small enough that both groups fit without scrolling far. */
@Composable
private fun CoverGrid(
    tiles: List<CoverTile>,
    selected: CoverSource?,
    store: CoverImageStore,
    onPick: (CoverSource) -> Unit,
    onDelete: (CoverSource.UserImage) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        for (chunk in tiles.chunked(COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                for (tile in chunk) {
                    CoverTileItem(
                        tile = tile,
                        isSelected = tile.source == selected,
                        store = store,
                        onPick = { onPick(tile.source) },
                        onDelete = { (tile.source as? CoverSource.UserImage)?.let(onDelete) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep a short last row the same tile size as a full one.
                repeat(COLUMNS - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CoverTileItem(
    tile: CoverTile,
    isSelected: Boolean,
    store: CoverImageStore,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDiscnctColors.current
    val bitmap = rememberCoverBitmap(tile.source, store)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(DiscnctShapes.cardCompact)
                .background(colors.surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) colors.accent else colors.border,
                    shape = DiscnctShapes.cardCompact,
                )
                .clickable(onClick = onPick),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = tile.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isSelected) {
                Text(
                    text = "✓",
                    style = DiscnctType.label,
                    color = colors.textDisplay,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(DiscnctShapes.pill)
                        .background(colors.accent)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                )
            } else if (tile.deletable) {
                Text(
                    text = "×",
                    style = DiscnctType.label,
                    color = colors.textDisplay,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(DiscnctShapes.pill)
                        .background(colors.surfaceRaised)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = tile.caption,
            style = DiscnctType.label,
            color = colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun AddImageRow(onClick: () -> Unit) {
    val colors = LocalDiscnctColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(DiscnctShapes.pill)
            .border(1.dp, colors.borderVisible, DiscnctShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("+", style = DiscnctType.subheading, color = colors.textPrimary)
        Text("ADD NEW IMAGE", style = DiscnctType.caption, color = colors.textPrimary)
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/**
 * Decode one cover image for display, off the main thread and small.
 *
 * Quarter size rather than full because the largest thing drawn here is a 150dp preview; the
 * built-ins are 1254px square and decoding them whole for a thumbnail grid is how a picker
 * starts dropping frames.
 */
@Composable
internal fun rememberCoverBitmap(source: CoverSource?, store: CoverImageStore): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                when (source) {
                    null -> null
                    is CoverSource.Builtin -> {
                        val id = context.resources.getIdentifier(
                            source.drawableName,
                            "drawable",
                            context.packageName,
                        )
                        if (id == 0) {
                            null
                        } else {
                            BitmapFactory.decodeResource(context.resources, id, options)
                        }
                    }

                    is CoverSource.UserImage -> {
                        val file = store.fileFor(source)
                        if (file.isFile) BitmapFactory.decodeFile(file.path, options) else null
                    }
                }?.asImageBitmap()
            }.getOrNull()
        }
    }.value
}

private const val COLUMNS = 4
