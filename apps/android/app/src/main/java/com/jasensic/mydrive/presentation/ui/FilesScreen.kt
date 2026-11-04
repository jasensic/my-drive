package com.jasensic.mydrive.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.filesInAlbum
import com.jasensic.mydrive.domain.filterLibraryFiles
import com.jasensic.mydrive.domain.isBlankLibraryQuery
import com.jasensic.mydrive.presentation.theme.Motion

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
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(files, albumId) { filesInAlbum(files, albumId) }
    val filtered = remember(visible, query) { filterLibraryFiles(visible, query) }
    val selecting = selectedIds.isNotEmpty()
    val searching = !isBlankLibraryQuery(query)
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
    ) {
        item {
            Text(
                "Files",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
            )
        }
        item {
            LibrarySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search files")
        }
        if (albums.isNotEmpty()) {
            item { AlbumFilterRow(albums, albumId, onOpenAlbum) }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyLibrary(
                    title = if (searching) "No matching files" else "No files here",
                    body = if (searching) "Try another file name." else "This album has no files yet.",
                    icon = Icons.Outlined.FolderOpen,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item {
                // Rows sit in one rounded group, like the iOS Files list.
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    filtered.forEachIndexed { index, file ->
                        FileRow(
                            file = file,
                            selected = file.id in selectedIds,
                            divider = index != filtered.lastIndex,
                            onClick = { if (selecting) onToggleSelect(file.id) else onOpen(file.id) },
                            onLongClick = { onLongPress(file.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun fileTypeColor(file: LocalFile): Color {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val mime = file.mime.lowercase()
    return when {
        mime.contains("pdf") || ext == "pdf" -> Color(0xFFE5484D)
        mime.contains("zip") || ext in setOf("zip", "7z", "rar", "gz", "tgz") -> Color(0xFFF59E0B)
        ext in setOf("xls", "xlsx", "ods", "csv") -> Color(0xFF34A853)
        ext in setOf("doc", "docx", "odt") -> Color(0xFF3B82F6)
        mime.startsWith("text") || ext in setOf("txt", "md", "log", "json", "xml") -> Color(0xFF8E8E93)
        else -> Color(0xFF6B7280)
    }
}

@Composable
private fun FileRow(
    file: LocalFile,
    selected: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        tween(Motion.Fast),
        label = "fileSelected",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .rowClickable(onClick, onLongClick)
            .drawBehind {
                if (divider) {
                    drawLine(
                        colors.outlineVariant,
                        Offset(68.dp.toPx(), size.height - 0.5f),
                        Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f,
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(fileTypeIcon(file), fileTypeColor(file), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val meta = listOfNotNull(
                formatSize(file.size),
                formatModified(file.modifiedAtMillis).takeIf { it.isNotBlank() },
                "Shared".takeIf { file.shared },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (scaleIn(Motion.spring()) + fadeIn(tween(Motion.Fast))) togetherWith fadeOut(tween(Motion.Fast)) },
            label = "fileTrailing",
        ) { isSelected ->
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = colors.primary)
            }
        }
    }
}
