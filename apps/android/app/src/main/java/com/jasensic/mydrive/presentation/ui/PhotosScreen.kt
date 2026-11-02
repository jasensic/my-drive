package com.jasensic.mydrive.presentation.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.pressScale
import com.jasensic.mydrive.presentation.theme.rememberHaptics
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.filesInAlbum
import com.jasensic.mydrive.domain.filterLibraryFiles
import com.jasensic.mydrive.domain.groupVisualMediaByDay
import com.jasensic.mydrive.domain.isBlankLibraryQuery
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    files: List<LocalFile>,
    albums: List<Album>,
    albumId: String?,
    selectedIds: Set<String>,
    onOpen: (String) -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(files, albumId) { filesInAlbum(files, albumId) }
    val filtered = remember(visible, query) { filterLibraryFiles(visible, query) }
    val selecting = selectedIds.isNotEmpty()
    val searching = !isBlankLibraryQuery(query)
    if (files.isEmpty() && albums.isEmpty()) {
        EmptyLibrary(
            title = "No photos or videos",
            body = "Synced pictures and clips from the LAN server will appear here. Create albums to keep them in sync with the portal.",
            icon = Icons.Outlined.PhotoLibrary,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    val zone = remember { ZoneId.systemDefault().id }
    val groups = remember(filtered, zone) { groupVisualMediaByDay(filtered, zone) }
    val today = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
            Text(
                "Photos",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
            LibrarySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search photos")
        }
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "albums") {
                AlbumFilterRow(albums, albumId, onOpenAlbum)
            }
        }
        if (groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyLibrary(
                    title = if (searching) "No matching photos" else "No photos here",
                    body = if (searching) "Try another file name." else "This album has no photos or videos yet.",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-${group.epochDay}") {
                Text(
                    formatDateHeader(group.epochDay, today),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                )
            }
            items(group.files, key = { it.id }) { file ->
                PhotoTile(
                    file = file,
                    selected = file.id in selectedIds,
                    selecting = selecting,
                    onClick = { if (selecting) onToggleSelect(file.id) else onOpen(file.id) },
                    onLongClick = { onLongPress(file.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * A square tile. Selecting one shrinks it a little inside a rounded frame with a check badge, so the
 * state is readable without a border.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    file: LocalFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, Motion.spring(), label = "tileInset")
    val corner by animateDpAsState(if (selected) 10.dp else 0.dp, Motion.spring(), label = "tileCorner")
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .pressScale(source, 0.97f)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.lift()
                    onLongClick()
                },
            ),
    ) {
        AsyncImage(
            model = thumbnailModel(file),
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
        )
        if (file.mediaKind == MediaKind.VIDEO) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp)
                    .size(18.dp),
            )
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            enter = scaleIn(Motion.spring(), initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(tween(Motion.Fast)) + fadeOut(tween(Motion.Fast)),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = colors.primary,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.White, CircleShape),
            )
        }
        if (selecting && !selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}

@Composable
private fun thumbnailModel(file: LocalFile): Any {
    val context = LocalContext.current
    val model = mediaModel(file.artworkPath) ?: mediaModel(file.thumbnailUrl) ?: mediaModel(file.path) ?: file.path
    return if (file.mediaKind == MediaKind.VIDEO) {
        ImageRequest.Builder(context)
            .data(model)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(1_000)
            .crossfade(true)
            .build()
    } else {
        model
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    files: List<LocalFile>,
    currentId: String,
    authToken: String?,
    onPage: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onClose: () -> Unit = {},
) {
    val start = files.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { files.size }
    LaunchedEffect(pager.currentPage, files) {
        val id = files.getOrNull(pager.currentPage)?.id ?: return@LaunchedEffect
        if (id != currentId) onPage(id)
    }
    LaunchedEffect(currentId, files) {
        val index = files.indexOfFirst { it.id == currentId }
        if (index >= 0 && index != pager.currentPage) {
            pager.scrollToPage(index)
        }
    }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    // Dragging a photo down closes the viewer: the picture follows the finger, the backdrop fades.
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dragProgress = (abs(drag.value) / 900f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - dragProgress * 0.85f)),
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = drag.value
                    val s = 1f - dragProgress * 0.15f
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(zoomed) {
                    if (zoomed) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(drag.value) > 260f) {
                                    drag.animateTo(if (drag.value > 0) 1600f else -1600f, tween(Motion.Medium))
                                    onClose()
                                } else {
                                    drag.animateTo(0f, Motion.spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, Motion.spring()) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount) }
                    }
                },
        ) { page ->
            val file = files[page]
            when (file.mediaKind) {
                MediaKind.PHOTO -> ZoomablePhoto(
                    file = file,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChange = { zoomed = it },
                )
                MediaKind.VIDEO -> {
                    LaunchedEffect(file.id) { onPauseAudio() }
                    VideoPlayer(file, authToken)
                }
                else -> Text(file.name, color = Color.White, modifier = Modifier.padding(24.dp))
            }
        }
        AnimatedVisibility(
            visible = chromeVisible && dragProgress == 0f,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(Motion.Fast)),
            exit = fadeOut(tween(Motion.Fast)),
        ) {
            val current = files.getOrNull(pager.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(current?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        "${pager.currentPage + 1} of ${files.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Pinch to zoom, double tap to jump between fit and 2.5x, tap to hide the controls. */
@Composable
private fun ZoomablePhoto(file: LocalFile, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    var scale by remember(file.id) { mutableFloatStateOf(1f) }
    var offset by remember(file.id) { mutableStateOf(Offset.Zero) }
    val animatedScale by animateFloatAsState(scale, Motion.spring(), label = "photoZoom")
    LaunchedEffect(scale) { onZoomChange(scale > 1.01f) }
    AsyncImage(
        model = mediaModel(file.path) ?: file.remoteUrl ?: file.path,
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .pointerInput(file.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(file.id) {
                // Only claims the gesture for two fingers or when already zoomed, so a one-finger
                // swipe still pages the viewer and a drag down still closes it.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(file: LocalFile, authToken: String?) {
    val context = LocalContext.current
    val exo = remember(file.id, file.path, file.remoteUrl, authToken) {
        val uri = when {
            File(file.path).isFile -> Uri.fromFile(File(file.path))
            !file.remoteUrl.isNullOrBlank() -> Uri.parse(file.remoteUrl)
            else -> Uri.fromFile(File(file.path))
        }
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    setDefaultRequestProperties(mapOf("Authorization" to "Bearer $authToken"))
                }
            }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(context, http),
                ),
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(file.id) {
        onDispose { exo.release() }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                useController = true
            }
        },
        update = { it.player = exo },
    )
}
