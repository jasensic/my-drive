package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.RepeatMode
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.pressScale
import com.jasensic.mydrive.presentation.theme.rememberHaptics
import kotlinx.coroutines.launch
import com.jasensic.mydrive.domain.trackHeadline
import com.jasensic.mydrive.domain.trackSubtitle
import kotlin.math.roundToInt
/** A floating card above the tab bar. Tap it to open Now Playing. */
@Composable
fun MiniPlayer(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val current = playback.current ?: return
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val fraction = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(fraction, tween(250), label = "miniProgress")
    Surface(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .pressScale(source, 0.98f),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        shadowElevation = 10.dp,
        border = BorderStroke(0.5.dp, colors.outlineVariant),
    ) {
        Column {
            Row(
                Modifier
                    .clickable(interactionSource = source, indication = null, onClick = onOpen)
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(
                    file = current,
                    fallbackName = current.displayAlbum,
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.size(44.dp),
                    corner = 8.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        current.trackHeadline(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        current.trackSubtitle().ifBlank { current.displayArtist },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    haptics.tick()
                    onPlayPause()
                }) {
                    Crossfade(playback.playing, animationSpec = tween(Motion.Fast), label = "miniPlayPause") { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                IconButton(onClick = {
                    haptics.tick()
                    onSkipNext()
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.outlineVariant),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(colors.primary))
            }
        }
    }
}

/**
 * Slides up over the library. Drag the cover down to close it; the cover shrinks while paused,
 * so play state is visible without reading the button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRemoveQueued: (String) -> Unit,
    onMoveQueued: (Int, Int) -> Unit,
    onPlayQueued: (String) -> Unit,
    onClose: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val current = playback.current
    var dragging by remember { mutableFloatStateOf(-1f) }
    var queueDragging by remember { mutableStateOf(false) }
    val duration = playback.durationMs.coerceAtLeast(1L).toFloat()
    val position = if (dragging >= 0f) dragging else playback.positionMs.toFloat().coerceIn(0f, duration)
    val coverScale by animateFloatAsState(if (playback.playing) 1f else 0.86f, Motion.spring(), label = "coverScale")
    val sheetOffset = remember { Animatable(0f) }
    val dismissDistance = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = sheetOffset.value.coerceAtLeast(0f) }
            .background(colors.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = !queueDragging)
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            // Grabber and cover form the handle that closes the screen when dragged down.
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (sheetOffset.value > dismissDistance) {
                                        sheetOffset.animateTo(size.height.toFloat() * 3f, tween(Motion.Medium))
                                        onClose()
                                    } else {
                                        sheetOffset.animateTo(0f, Motion.spring())
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { sheetOffset.animateTo(0f, Motion.spring()) } },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { sheetOffset.snapTo((sheetOffset.value + amount).coerceAtLeast(0f)) }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SheetGrabber(Modifier.padding(top = 10.dp, bottom = 18.dp))
                Artwork(
                    file = current,
                    fallbackName = current?.displayAlbum ?: "Now playing",
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .shadow(if (playback.playing) 24.dp else 6.dp, RoundedCornerShape(16.dp), clip = false),
                    corner = 16.dp,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                current?.trackHeadline() ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                current?.let { it.trackSubtitle().ifBlank { it.displayArtist } }.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Slider(
                value = position,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    onSeek(dragging.toLong())
                    dragging = -1f
                },
                valueRange = 0f..duration,
                enabled = current != null,
                colors = SliderDefaults.colors(
                    thumbColor = colors.onSurface,
                    activeTrackColor = colors.onSurface,
                    inactiveTrackColor = colors.outlineVariant,
                ),
                // Thin track with a small round thumb instead of the tall Material bar.
                thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(colors.onSurface)) },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(4.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = colors.onSurface,
                            inactiveTrackColor = colors.outlineVariant,
                        ),
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        drawStopIndicator = null,
                    )
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position.toLong()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleGlyph(Icons.Filled.Shuffle, "Shuffle", playback.shuffle) {
                    haptics.tick()
                    onShuffle()
                }
                TransportButton(Icons.Filled.SkipPrevious, "Previous", 44.dp) {
                    haptics.tick()
                    onPrev()
                }
                TransportButton(
                    icon = if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (playback.playing) "Pause" else "Play",
                    size = 64.dp,
                    enabled = current != null,
                    busy = playback.buffering && !playback.playing,
                ) {
                    haptics.tick()
                    onPlayPause()
                }
                TransportButton(Icons.Filled.SkipNext, "Next", 44.dp) {
                    haptics.tick()
                    onNext()
                }
                ToggleGlyph(
                    if (playback.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    playback.repeat != RepeatMode.OFF,
                ) {
                    haptics.tick()
                    onRepeat()
                }
            }
            Spacer(Modifier.height(28.dp))
            QueueSection(
                playback = playback,
                onRemove = onRemoveQueued,
                onMove = onMoveQueued,
                onPlay = onPlayQueued,
                onDragging = { queueDragging = it },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size + 16.dp)
            .pressScale(source, 0.88f)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(size * 0.6f), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Crossfade(icon, animationSpec = tween(Motion.Fast), label = "transport") { glyph ->
                Icon(
                    glyph,
                    contentDescription = description,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

@Composable
private fun ToggleGlyph(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (active) colors.primaryContainer else Color.Transparent, tween(Motion.Fast), label = "toggleBg")
    val tint by animateColorAsState(if (active) colors.primary else colors.onSurfaceVariant, tween(Motion.Fast), label = "toggleTint")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QueueSection(
    playback: PlaybackState,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlay: (String) -> Unit,
    onDragging: (Boolean) -> Unit,
) {
    val items = remember { mutableStateListOf<LocalFile>() }
    val queueKey = playback.queue.joinToString { it.id }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var originIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(queueKey) {
        if (draggingIndex >= 0) return@LaunchedEffect
        items.clear()
        items.addAll(playback.queue)
    }

    Text("Queue", style = MaterialTheme.typography.titleLarge)
    Text(
        if (playback.shuffle) {
            "Shuffle is on. Drag a row to change the play order (turns shuffle off)."
        } else {
            "${playback.queue.size} tracks · drag the handle to reorder"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (items.isEmpty()) {
        Text(
            "Nothing queued",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    items.forEachIndexed { index, track ->
        key(track.id) {
            val current = track.id == playback.current?.id
            val dragging = index == draggingIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) rowHeight = coords.size.height.toFloat()
                    }
                    .clickable(enabled = !dragging) { onPlay(track.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(track.id) {
                            detectDragGestures(
                                onDragStart = {
                                    val start = items.indexOfFirst { it.id == track.id }
                                    originIndex = start
                                    draggingIndex = start
                                    dragOffset = 0f
                                    onDragging(true)
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    originIndex = -1
                                    dragOffset = 0f
                                    onDragging(false)
                                },
                                onDragEnd = {
                                    val from = originIndex
                                    val to = draggingIndex
                                    draggingIndex = -1
                                    originIndex = -1
                                    dragOffset = 0f
                                    onDragging(false)
                                    if (from >= 0 && to >= 0 && from != to) onMove(from, to)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (rowHeight <= 0f) {
                                        dragOffset += amount.y
                                        return@detectDragGestures
                                    }
                                    dragOffset += amount.y
                                    val from = draggingIndex
                                    if (from < 0) return@detectDragGestures
                                    val target = when {
                                        dragOffset > rowHeight * 0.5f -> (from + 1).coerceAtMost(items.lastIndex)
                                        dragOffset < -rowHeight * 0.5f -> (from - 1).coerceAtLeast(0)
                                        else -> from
                                    }
                                    if (target != from) {
                                        val moved = items.removeAt(from)
                                        items.add(target, moved)
                                        draggingIndex = target
                                        dragOffset -= (target - from) * rowHeight
                                    }
                                },
                            )
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        track.trackHeadline(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        track.trackSubtitle().ifBlank { track.displayArtist },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRemove(track.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove from queue")
                }
            }
        }
    }
}
