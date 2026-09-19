package com.jasensic.mydrive.domain

enum class MediaKind { PHOTO, VIDEO, AUDIO, OTHER }

enum class LibrarySilo { MUSIC, PHOTOS, FILES }

data class Album(
    val id: String,
    val name: String,
    val silo: LibrarySilo = LibrarySilo.PHOTOS,
)

data class ManifestFile(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val checksum: String,
    val url: String,
    val mediaKind: MediaKind,
    val albumId: String?,
)

data class SyncManifest(
    val generatedAt: String,
    val files: List<ManifestFile>,
    val albums: List<Album>,
)

data class SyncResult(
    val server: DiscoveredServer,
    val manifest: SyncManifest,
    val downloaded: Int,
)

data class SyncRule(
    val mediaKind: MediaKind,
    val maxAgeDays: Int?,
    val maxSizeBytes: Long?,
    val includeAll: Boolean,
)

data class DiscoveredServer(
    val host: String,
    val port: Int,
    val name: String,
) {
    val baseUrl: String
        get() {
            val raw = host.substringBefore('%')
            val hostPart = if (raw.contains(':') && !raw.startsWith("[")) "[$raw]" else raw
            return "http://$hostPart:$port"
        }
}

data class AuthSession(
    val token: String,
    val username: String,
    val deviceId: String?,
)

data class LocalFile(
    val id: String,
    val name: String,
    val mime: String,
    val mediaKind: MediaKind,
    val albumId: String?,
    val albumName: String?,
    val size: Long,
    val path: String,
    val artist: String? = null,
    val durationMs: Long? = null,
    val modifiedAtMillis: Long = 0L,
    val artworkPath: String? = null,
) {
    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST

    val displayAlbum: String
        get() = albumName?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM

    val isVisual: Boolean
        get() = mediaKind == MediaKind.PHOTO || mediaKind == MediaKind.VIDEO
}

data class LocalLibrary(
    val albums: List<Album>,
    val files: List<LocalFile>,
)

data class ClassifiedLibrary(
    val music: List<LocalFile>,
    val photos: List<LocalFile>,
    val documents: List<LocalFile>,
)

data class MusicGroup(
    val id: String,
    val name: String,
    val tracks: List<LocalFile>,
    val artworkPath: String?,
)

data class DatedMediaGroup(
    val epochDay: Long,
    val files: List<LocalFile>,
)

enum class DateSectionKind { TODAY, YESTERDAY, DATE }

enum class RepeatMode { OFF, ALL, ONE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PlaybackState(
    val queue: List<LocalFile> = emptyList(),
    val currentIndex: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    val current: LocalFile? get() = queue.getOrNull(currentIndex)
    val isActive: Boolean get() = current != null
}

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"

data class AppRelease(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val checksum: String,
    val size: Long,
    val downloadUrl: String,
)

fun parseMediaKind(value: String?): MediaKind =
    when (value?.lowercase()) {
        "photo" -> MediaKind.PHOTO
        "video" -> MediaKind.VIDEO
        "audio" -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }

fun parseLibrarySilo(value: String?): LibrarySilo =
    when (value?.lowercase()) {
        "music" -> LibrarySilo.MUSIC
        "files", "other" -> LibrarySilo.FILES
        else -> LibrarySilo.PHOTOS
    }

fun LibrarySilo.contains(kind: MediaKind): Boolean =
    when (this) {
        LibrarySilo.MUSIC -> kind == MediaKind.AUDIO
        LibrarySilo.PHOTOS -> kind == MediaKind.PHOTO || kind == MediaKind.VIDEO
        LibrarySilo.FILES -> kind == MediaKind.OTHER
    }

fun siloForKind(kind: MediaKind): LibrarySilo =
    when (kind) {
        MediaKind.AUDIO -> LibrarySilo.MUSIC
        MediaKind.PHOTO, MediaKind.VIDEO -> LibrarySilo.PHOTOS
        MediaKind.OTHER -> LibrarySilo.FILES
    }

fun LibrarySilo.wireValue(): String =
    when (this) {
        LibrarySilo.MUSIC -> "music"
        LibrarySilo.PHOTOS -> "photos"
        LibrarySilo.FILES -> "files"
    }

fun parseManualServer(input: String): DiscoveredServer {
    val trimmed = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    require(trimmed.isNotBlank()) { "server address is required" }
    val hostPort = trimmed.substringBefore("/")
    val host = hostPort.substringBefore(":")
    val port = hostPort.substringAfter(":", "80").toIntOrNull() ?: 80
    require(host.isNotBlank()) { "server address is required" }
    return DiscoveredServer(host, port, host)
}
