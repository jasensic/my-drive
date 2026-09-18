package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MusicGroup
import com.jasensic.mydrive.domain.groupMusicByAlbum
import com.jasensic.mydrive.domain.groupMusicByArtist
import com.jasensic.mydrive.domain.recentMusic

@Composable
fun MusicScreen(
    tracks: List<LocalFile>,
    onPlay: (List<LocalFile>, String) -> Unit,
    contentPadding: PaddingValues,
) {
    var selected by remember { mutableStateOf<MusicGroup?>(null) }
    val albums = remember(tracks) { groupMusicByAlbum(tracks) }
    val artists = remember(tracks) { groupMusicByArtist(tracks) }
    val recent = remember(tracks) { recentMusic(tracks) }
    if (tracks.isEmpty()) {
        EmptyLibrary(
            title = "No music yet",
            body = "Sync from the server to fill this library with albums and tracks stored on your LAN.",
            icon = Icons.Filled.MusicNote,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    val detail = selected
    if (detail != null) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(detail.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${detail.tracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TrackList(
                tracks = detail.tracks,
                onPlay = { onPlay(detail.tracks, it.id) },
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
        if (albums.isNotEmpty()) {
            item { SectionLabel("Albums") }
            item { MusicCarousel(albums, onClick = { selected = it }) }
        }
        if (artists.isNotEmpty()) {
            item { SectionLabel("Artists") }
            item { MusicCarousel(artists, onClick = { selected = it }) }
        }
        item { SectionLabel("Tracks") }
        items(tracks, key = { it.id }) { track ->
            TrackRow(track, onClick = { onPlay(tracks, track.id) })
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
                    if (group.tracks.size == 1) group.tracks.first().displayArtist else "${group.tracks.size} tracks",
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
private fun TrackList(tracks: List<LocalFile>, onPlay: (LocalFile) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(track, onClick = { onPlay(track) })
        }
    }
}

@Composable
fun TrackRow(track: LocalFile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
