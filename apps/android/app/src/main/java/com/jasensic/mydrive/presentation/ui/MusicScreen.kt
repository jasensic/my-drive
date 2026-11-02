package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.pressScale
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MusicGroup
import com.jasensic.mydrive.domain.filterLibraryFiles
import com.jasensic.mydrive.domain.filterMusicGroups
import com.jasensic.mydrive.domain.groupMusicByAlbum
import com.jasensic.mydrive.domain.groupMusicByArtist
import com.jasensic.mydrive.domain.isBlankLibraryQuery
import com.jasensic.mydrive.domain.isSong
import com.jasensic.mydrive.domain.otherAudio
import com.jasensic.mydrive.domain.playableAudio
import com.jasensic.mydrive.domain.recentMusic
import com.jasensic.mydrive.domain.songTitle
import com.jasensic.mydrive.domain.trackHeadline
import com.jasensic.mydrive.domain.trackSubtitle

@Composable
fun MusicScreen(
    tracks: List<LocalFile>,
    albums: List<Album>,
    albumId: String?,
    artistName: String?,
    selectedIds: Set<String>,
    playingId: String? = null,
    isPlaying: Boolean = false,
    onPlay: (List<LocalFile>, String) -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val songs = remember(tracks) {
        tracks.filter { it.isSong() }.sortedBy { it.trackHeadline().lowercase() }
    }
    val other = remember(tracks) { otherAudio(tracks) }
    val groupedAlbums = remember(songs, albums) { groupMusicByAlbum(songs, albums) }
    val artists = remember(songs) { groupMusicByArtist(songs) }
    val filteredSongs = remember(songs, query) { filterLibraryFiles(songs, query) }
    val filteredOther = remember(other, query) { filterLibraryFiles(other, query) }
    val filteredAlbums = remember(groupedAlbums, query) { filterMusicGroups(groupedAlbums, query) }
    val filteredArtists = remember(artists, query) { filterMusicGroups(artists, query) }
    val recent = remember(filteredSongs) { recentMusic(filteredSongs) }
    val playable = remember(filteredSongs, filteredOther) { playableAudio(filteredSongs + filteredOther) }
    val selecting = selectedIds.isNotEmpty()
    val searching = !isBlankLibraryQuery(query)
    val detail = when {
        albumId != null -> groupedAlbums.find { it.id == albumId } ?: MusicGroup(albumId, albums.find { it.id == albumId }?.name ?: "Album", emptyList(), null)
        artistName != null -> artists.find { it.name == artistName }
        else -> null
    }
    val detailTracks = remember(detail, query) { filterLibraryFiles(detail?.tracks.orEmpty(), query) }
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
            Row(
                Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(detail.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${detailTracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detailTracks.isNotEmpty()) {
                    PlayPill("Play") { onPlay(detailTracks, detailTracks.first().id) }
                }
            }
            LibrarySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search songs")
            if (detailTracks.isEmpty()) {
                EmptyLibrary(
                    title = if (searching) "No matching songs" else "Empty album",
                    body = if (searching) "Try another title, artist, or album name." else "This album has no tracks yet.",
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.weight(1f),
                )
            } else {
                TrackList(
                    tracks = detailTracks,
                    selectedIds = selectedIds,
                    playingId = playingId,
                    isPlaying = isPlaying,
                    onPlay = { track ->
                        if (selecting) onToggleSelect(track.id) else onPlay(detailTracks, track.id)
                    },
                    onLongPress = onLongPress,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
    val hasMatches = recent.isNotEmpty() || filteredAlbums.isNotEmpty() || filteredArtists.isNotEmpty() ||
        filteredSongs.isNotEmpty() || filteredOther.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Music", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (searching) "${filteredSongs.size + filteredOther.size} matches" else "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (playable.isNotEmpty()) {
                    PlayPill("Play all") { onPlay(playable, playable.first().id) }
                }
            }
        }
        item {
            LibrarySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search songs")
        }
        if (!hasMatches) {
            item {
                EmptyLibrary(
                    title = "No matching songs",
                    body = "Try another title, artist, or album name.",
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.height(240.dp),
                )
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel("Recently added") }
            item {
                MusicCarousel(recent.map { track ->
                    MusicGroup(track.id, track.songTitle(), listOf(track), track.artworkPath)
                }, onClick = { group -> onPlay(filteredSongs, group.id) })
            }
        }
        if (filteredAlbums.isNotEmpty()) {
            item { SectionLabel("Albums") }
            item { MusicCarousel(filteredAlbums, onClick = { onOpenAlbum(it.id) }) }
        }
        if (filteredArtists.isNotEmpty()) {
            item { SectionLabel("Artists") }
            item { MusicCarousel(filteredArtists, onClick = { onOpenArtist(it.name) }) }
        }
        if (filteredSongs.isNotEmpty()) {
            item { SectionLabel("Tracks") }
            items(filteredSongs, key = { it.id }) { track ->
                TrackRow(
                    track,
                    selected = track.id in selectedIds,
                    playing = track.id == playingId,
                    isPlaying = isPlaying,
                    onClick = {
                        if (selecting) onToggleSelect(track.id) else onPlay(filteredSongs, track.id)
                    },
                    onLongClick = { onLongPress(track.id) },
                )
            }
        }
        if (filteredOther.isNotEmpty()) {
            item { SectionLabel("Other audio") }
            items(filteredOther, key = { it.id }) { track ->
                TrackRow(
                    track,
                    selected = track.id in selectedIds,
                    playing = track.id == playingId,
                    isPlaying = isPlaying,
                    onClick = {
                        if (selecting) onToggleSelect(track.id) else onPlay(filteredOther, track.id)
                    },
                    onLongClick = { onLongPress(track.id) },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlayPill(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(source)
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MusicCarousel(groups: List<MusicGroup>, onClick: (MusicGroup) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(groups, key = { it.id }) { group ->
            val source = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .width(148.dp)
                    .pressScale(source, 0.95f)
                    .clickable(interactionSource = source, indication = null) { onClick(group) },
            ) {
                Artwork(
                    file = group.tracks.firstOrNull(),
                    fallbackName = group.name,
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.size(148.dp),
                    corner = 12.dp,
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
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (LocalFile) -> Unit,
    onLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track,
                selected = track.id in selectedIds,
                playing = track.id == playingId,
                isPlaying = isPlaying,
                onClick = { onPlay(track) },
                onLongClick = { onLongPress(track.id) },
            )
        }
    }
}

@Composable
fun TrackRow(
    track: LocalFile,
    selected: Boolean = false,
    playing: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        tween(Motion.Fast),
        label = "trackSelected",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .rowClickable(onClick, onLongClick)
            .drawBehind {
                drawLine(
                    colors.outlineVariant,
                    Offset(80.dp.toPx(), size.height - 0.5f),
                    Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            Artwork(
                file = track,
                fallbackName = track.displayAlbum,
                icon = Icons.Filled.MusicNote,
                modifier = Modifier.fillMaxSize(),
                corner = 8.dp,
            )
            if (playing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(playing = isPlaying, color = Color.White)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.trackHeadline(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) colors.primary else colors.onSurface,
            )
            val subtitle = listOfNotNull(
                track.trackSubtitle().takeIf { it.isNotBlank() },
                "Shared".takeIf { track.shared },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (scaleIn(Motion.spring()) + fadeIn(tween(Motion.Fast))) togetherWith fadeOut(tween(Motion.Fast)) },
            label = "trackTrailing",
        ) { isSelected ->
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.primary)
            } else {
                Text(
                    formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
