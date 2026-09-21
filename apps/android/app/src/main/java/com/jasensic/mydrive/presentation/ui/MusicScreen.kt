package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MusicGroup
import com.jasensic.mydrive.domain.groupMusicByAlbum
import com.jasensic.mydrive.domain.groupMusicByArtist
import com.jasensic.mydrive.domain.recentMusic

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    tracks: List<LocalFile>,
    albums: List<Album>,
    albumId: String?,
    artistName: String?,
    selectedIds: Set<String>,
    onPlay: (List<LocalFile>, String) -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val groupedAlbums = remember(tracks, albums) { groupMusicByAlbum(tracks, albums) }
    val artists = remember(tracks) { groupMusicByArtist(tracks) }
    val recent = remember(tracks) { recentMusic(tracks) }
    val selecting = selectedIds.isNotEmpty()
    val detail = when {
        albumId != null -> groupedAlbums.find { it.id == albumId } ?: MusicGroup(albumId, albums.find { it.id == albumId }?.name ?: "Album", emptyList(), null)
        artistName != null -> artists.find { it.name == artistName }
        else -> null
    }
    if (tracks.isEmpty() && albums.isEmpty()) {
        EmptyLibrary(
            title = "No music yet",
            body = "Sync from the server to fill this library with albums and tracks stored on your LAN.",
            icon = Icons.Filled.MusicNote,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    if (detail != null) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (albumId != null) onOpenAlbum(null) else onOpenArtist(null)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(detail.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${detail.tracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TrackList(
                tracks = detail.tracks,
                selectedIds = selectedIds,
                onPlay = { track ->
                    if (selecting) onToggleSelect(track.id) else onPlay(detail.tracks, track.id)
                },
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Music", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel("Recently added") }
            item {
                MusicCarousel(recent.map { track ->
                    MusicGroup(track.id, track.name, listOf(track), track.artworkPath)
                }, onClick = { group -> onPlay(tracks, group.id) })
            }
        }
        if (groupedAlbums.isNotEmpty()) {
            item { SectionLabel("Albums") }
            item { MusicCarousel(groupedAlbums, onClick = { onOpenAlbum(it.id) }) }
        }
        if (artists.isNotEmpty()) {
            item { SectionLabel("Artists") }
            item { MusicCarousel(artists, onClick = { onOpenArtist(it.name) }) }
        }
        item { SectionLabel("Tracks") }
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track,
                selected = track.id in selectedIds,
                onClick = {
                    if (selecting) onToggleSelect(track.id) else onPlay(tracks, track.id)
                },
                onLongClick = { onLongPress(track.id) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun MusicCarousel(groups: List<MusicGroup>, onClick: (MusicGroup) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.id }) { group ->
            Column(
                Modifier
                    .width(148.dp)
                    .clickable { onClick(group) },
            ) {
                Artwork(
                    file = group.tracks.firstOrNull(),
                    fallbackName = group.name,
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.size(148.dp),
                    corner = 14.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        group.tracks.isEmpty() -> "Empty album"
                        group.tracks.size == 1 -> group.tracks.first().displayArtist
                        else -> "${group.tracks.size} tracks"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<LocalFile>,
    selectedIds: Set<String>,
    onPlay: (LocalFile) -> Unit,
    onLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track,
                selected = track.id in selectedIds,
                onClick = { onPlay(track) },
                onLongClick = { onLongPress(track.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: LocalFile,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (selected) 1f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(
            file = track,
            fallbackName = track.displayAlbum,
            icon = Icons.Filled.MusicNote,
            modifier = Modifier.size(52.dp),
            corner = 8.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${track.displayArtist} · ${track.displayAlbum}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        } else {
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
