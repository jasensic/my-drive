package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.RepeatMode
import com.jasensic.mydrive.domain.trackHeadline
import com.jasensic.mydrive.domain.trackSubtitle

@Composable
fun MiniPlayer(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpen: () -> Unit,
) {
    val current = playback.current ?: return
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(
                file = current,
                fallbackName = current.displayAlbum,
                icon = Icons.Filled.MusicNote,
                modifier = Modifier.size(48.dp),
                corner = 8.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(current.trackHeadline(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                val subtitle = current.trackSubtitle().ifBlank { current.displayArtist }
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.playing) "Pause" else "Play",
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playback: PlaybackState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val current = playback.current
    var dragging by remember { mutableFloatStateOf(-1f) }
    val duration = playback.durationMs.coerceAtLeast(1L).toFloat()
    val position = if (dragging >= 0f) dragging else playback.positionMs.toFloat().coerceIn(0f, duration)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.height(12.dp))
        Artwork(
            file = current,
            fallbackName = current?.displayAlbum ?: "Now playing",
            icon = Icons.Filled.MusicNote,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 20.dp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            current?.trackHeadline() ?: "Nothing playing",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            current?.let { it.trackSubtitle().ifBlank { it.displayArtist } }.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Slider(
            value = position,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                onSeek(dragging.toLong())
                dragging = -1f
            },
            valueRange = 0f..duration,
            enabled = current != null,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp), enabled = current != null) {
                Icon(
                    if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.playing) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onRepeat) {
                val tint = if (playback.repeat == RepeatMode.OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Icon(
                    if (playback.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = tint,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
