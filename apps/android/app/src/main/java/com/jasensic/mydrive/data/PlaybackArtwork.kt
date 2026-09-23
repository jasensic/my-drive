package com.jasensic.mydrive.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.songTitle
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(UnstableApi::class)
internal object PlaybackArtwork {
    const val MAX_EDGE_PX = 320
    const val JPEG_QUALITY = 85

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun contentUri(context: Context, file: File): Uri? =
        runCatching { FileProvider.getUriForFile(context, authority(context), file) }.getOrNull()

    fun grantRead(context: Context, packageName: String, uri: Uri?) {
        if (uri == null || uri.scheme != "content" || packageName.isBlank()) return
        runCatching {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun resizedJpeg(path: String, maxEdge: Int = MAX_EDGE_PX): ByteArray? {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (width / sample > maxEdge * 2 || height / sample > maxEdge * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val longest = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        val scale = maxEdge.toFloat() / longest.toFloat()
        val bitmap = if (scale < 1f) {
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        val bytes = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
        bitmap.recycle()
        return bytes.toByteArray().takeIf { ok && it.isNotEmpty() }
    }
}

@OptIn(UnstableApi::class)
internal fun LocalFile.toPlayableMediaItem(context: Context): MediaItem {
    val mediaFile = File(path)
    val mediaUri = when {
        mediaFile.isFile -> PlaybackArtwork.contentUri(context, mediaFile) ?: Uri.fromFile(mediaFile)
        !remoteUrl.isNullOrBlank() -> Uri.parse(remoteUrl)
        else -> PlaybackArtwork.contentUri(context, mediaFile) ?: Uri.fromFile(mediaFile)
    }
    val artFile = artworkPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    val artUri = when {
        artFile != null -> PlaybackArtwork.contentUri(context, artFile)
        !thumbnailUrl.isNullOrBlank() -> Uri.parse(thumbnailUrl)
        artworkPath?.startsWith("http") == true -> Uri.parse(artworkPath)
        else -> null
    }
    val artBytes = artFile?.absolutePath?.let { PlaybackArtwork.resizedJpeg(it) }
    val metadata = MediaMetadata.Builder()
        .setTitle(songTitle())
        .setArtist(displayArtist)
        .setAlbumTitle(displayAlbum)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .apply {
            if (artUri != null) setArtworkUri(artUri)
            if (artBytes != null) setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(mediaUri)
        .setMimeType(mime.ifBlank { "audio/*" })
        .setMediaMetadata(metadata)
        .build()
}

@OptIn(UnstableApi::class)
internal fun browsableMediaItem(
    mediaId: String,
    title: String,
    mediaType: Int,
    artworkPath: String? = null,
    context: Context? = null,
): MediaItem {
    val artFile = artworkPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    val artUri = if (context != null && artFile != null) PlaybackArtwork.contentUri(context, artFile) else null
    val artBytes = artFile?.absolutePath?.let { PlaybackArtwork.resizedJpeg(it) }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsBrowsable(true)
        .setIsPlayable(false)
        .setMediaType(mediaType)
        .apply {
            if (artUri != null) setArtworkUri(artUri)
            if (artBytes != null) setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
        .build()
}

internal fun MediaItem.grantArtworkAndContent(context: Context, packageName: String) {
    PlaybackArtwork.grantRead(context, packageName, localConfiguration?.uri)
    PlaybackArtwork.grantRead(context, packageName, mediaMetadata.artworkUri)
}
