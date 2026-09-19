package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.filesInAlbum

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
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
            title = "No files yet",
            body = "Documents and other non-media files show up here after they are downloaded from the server. Create albums to keep folders in sync with the portal.",
            icon = Icons.Outlined.FolderOpen,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                "Files",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (albums.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(selected = albumId == null, onClick = { onOpenAlbum(null) }, label = { Text("All") })
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
        items(visible, key = { it.id }) { file ->
            FileRow(
                file = file,
                selected = file.id in selectedIds,
                onClick = { if (selecting) onToggleSelect(file.id) else onOpen(file.id) },
                onLongClick = { onLongPress(file.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: LocalFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else fileTypeIcon(file),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val meta = listOfNotNull(
                formatSize(file.size),
                formatModified(file.modifiedAtMillis).takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
