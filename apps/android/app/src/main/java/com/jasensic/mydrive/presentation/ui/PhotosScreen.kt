package com.jasensic.mydrive.presentation.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jasensic.mydrive.domain.groupVisualMediaByDay
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
    val visible = remember(files, albumId) { filesInAlbum(files, albumId) }
    val selecting = selectedIds.isNotEmpty()
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
    val groups = remember(visible, zone) { groupVisualMediaByDay(visible, zone) }
    val today = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(118.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = albumId == null,
                            onClick = { onOpenAlbum(null) },
                            label = { Text("All") },
                        )
                    }
                    items(albums, key = { it.id }) { album ->
                        FilterChip(
                            selected = albumId == album.id,
                            onClick = { onOpenAlbum(album.id) },
                            label = { Text(album.name) },
                        )
                    }
                }
            }
        }
        groups.forEach { group ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-${group.epochDay}") {
                Text(
                    formatDateHeader(group.epochDay, today),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(group.files, key = { it.id }) { file ->
                val selected = file.id in selectedIds
                Box(
                    Modifier
                        .padding(2.dp)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selecting) onToggleSelect(file.id) else onOpen(file.id)
                            },
                            onLongClick = { onLongPress(file.id) },
                        )
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            else Modifier
                        ),
                ) {
                    AsyncImage(
                        model = thumbnailModel(file),
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
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
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun thumbnailModel(file: LocalFile): Any {
    val context = LocalContext.current
    val disk = File(file.path)
    return if (file.mediaKind == MediaKind.VIDEO) {
        ImageRequest.Builder(context)
            .data(disk)
            .decoderFactory(VideoFrameDecoder.Factory())
            .videoFrameMillis(1_000)
            .crossfade(true)
            .build()
    } else {
        disk
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    files: List<LocalFile>,
    currentId: String,
    onBack: () -> Unit,
    onPage: (String) -> Unit,
    onPauseAudio: () -> Unit,
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
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val file = files[page]
            when (file.mediaKind) {
                MediaKind.PHOTO -> ZoomablePhoto(file)
                MediaKind.VIDEO -> {
                    LaunchedEffect(file.id) { onPauseAudio() }
                    VideoPlayer(file)
                }
                else -> Text(file.name, color = Color.White, modifier = Modifier.padding(24.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            val current = files.getOrNull(pager.currentPage)
            Column(Modifier.weight(1f)) {
                Text(current?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${pager.currentPage + 1} / ${files.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(file: LocalFile) {
    var scale by remember(file.id) { mutableFloatStateOf(1f) }
    var offset by remember(file.id) { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = File(file.path),
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .pointerInput(file.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(file: LocalFile) {
    val context = LocalContext.current
    val exo = remember(file.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(file.path))))
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
