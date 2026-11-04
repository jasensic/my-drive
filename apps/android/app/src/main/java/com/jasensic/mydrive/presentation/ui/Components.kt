package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.presentation.theme.ArtworkPalette
import java.io.File
import kotlin.math.absoluteValue

fun mediaModel(path: String?): Any? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val file = File(path)
    return if (file.isFile) file else path
}

@Composable
fun Artwork(
    file: LocalFile?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    corner: Dp = 12.dp,
) {
    val color = remember(fallbackName) {
        ArtworkPalette[fallbackName.hashCode().absoluteValue % ArtworkPalette.size]
    }
    val artPath = file?.artworkPath ?: file?.thumbnailUrl
    val model = mediaModel(artPath)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = fallbackName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.85f), color))),
            )
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxSize(0.46f),
                )
            }
        }
    }
}

@Composable
fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) = IosSearchField(query, onQueryChange, placeholder, modifier)

@Composable
fun EmptyLibrary(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "All" plus one capsule per album. Used by the Photos and Files lists. */
@Composable
fun AlbumFilterRow(
    albums: List<com.jasensic.mydrive.domain.Album>,
    albumId: String?,
    onOpenAlbum: (String?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { IosChip("All", albumId == null, { onOpenAlbum(null) }) }
        items(albums, key = { it.id }) { album ->
            IosChip(
                label = if (album.shared) "${album.name} · Shared" else album.name,
                selected = albumId == album.id,
                onClick = { onOpenAlbum(album.id) },
            )
        }
    }
}
