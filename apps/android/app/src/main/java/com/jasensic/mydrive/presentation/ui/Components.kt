package com.jasensic.mydrive.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.presentation.UiState
import java.io.File
import kotlin.math.absoluteValue

fun mediaModel(path: String?): Any? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val file = File(path)
    return if (file.isFile) file else path
}

private val AlbumPalette = listOf(
    Color(0xFF0F766E),
    Color(0xFF7C3AED),
    Color(0xFFB45309),
    Color(0xFF1D4ED8),
    Color(0xFFBE123C),
    Color(0xFF047857),
    Color(0xFF4338CA),
    Color(0xFF0E7490),
)

@Composable
fun Artwork(
    file: LocalFile?,
    fallbackName: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    corner: Dp = 12.dp,
) {
    val color = remember(fallbackName) {
        AlbumPalette[fallbackName.hashCode().absoluteValue % AlbumPalette.size]
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
                    .background(
                        Brush.linearGradient(
                            listOf(color, color.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.25f)),
                        ),
                    ),
            )
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyLibrary(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SyncStatusBar(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    val progress = state.syncProgress
    val showSync = progress.isActive || progress.phase == SyncPhase.COMPLETED
    val message = state.statusMessage ?: state.progress
    if (!showSync && message == null) return

    Column(modifier.fillMaxWidth()) {
        when {
            progress.phase == SyncPhase.DOWNLOADING && progress.totalFiles > 0 -> {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${progress.percent}% · ${message.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            showSync || message != null -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
