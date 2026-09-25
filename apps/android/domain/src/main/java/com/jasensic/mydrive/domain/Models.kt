package com.jasensic.mydrive.domain

enum class MediaKind { PHOTO, VIDEO, AUDIO, OTHER }

enum class LibrarySilo { MUSIC, PHOTOS, FILES }

enum class ResourceAccess { OWNER, READ, WRITE }

enum class ShareResourceType { FILE, ALBUM }

enum class SharePermission { READ, WRITE }

enum class LibrarySource { DEVICE, SERVER }

enum class AuthAction { LOGIN, REGISTER, SETUP }

data class Album(
    val id: String,
    val name: String,
    val silo: LibrarySilo = LibrarySilo.PHOTOS,
    val shared: Boolean = false,
    val access: ResourceAccess = ResourceAccess.OWNER,
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
    val removed: List<String> = emptyList(),
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
    /** Absolute origin (`https://api.example.com`) when discovery or the operator supplied a domain. */
    val advertisedUrl: String? = null,
) {
    val baseUrl: String
        get() = advertisedUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: run {
                val raw = host.substringBefore('%')
                val hostPart = if (raw.contains(':') && !raw.startsWith("[")) "[$raw]" else raw
                "http://$hostPart:$port"
            }

    val label: String
        get() = advertisedUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "$host:$port"
}

data class AuthSession(
    val token: String,
    val username: String,
    val deviceId: String?,
    val userId: String? = null,
)

data class ServerStatus(
    val setupRequired: Boolean,
)

data class UserProfile(
    val id: String,
    val username: String,
)

data class ShareGrant(
    val id: String,
    val resourceType: ShareResourceType,
    val resourceId: String,
    val ownerId: String,
    val granteeId: String,
    val granteeUsername: String,
    val permission: SharePermission,
)

data class RemoteFile(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val checksum: String,
    val mediaKind: MediaKind,
    val albumId: String?,
    val contentUrl: String,
    val thumbnailUrl: String?,
    val shared: Boolean,
    val access: ResourceAccess,
    val createdAt: String? = null,
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
    val albumArtist: String? = null,
    val title: String? = null,
    val durationMs: Long? = null,
    val modifiedAtMillis: Long = 0L,
    val artworkPath: String? = null,
    val onDevice: Boolean = true,
    val remoteUrl: String? = null,
    val thumbnailUrl: String? = null,
    val shared: Boolean = false,
    val access: ResourceAccess = ResourceAccess.OWNER,
) {
    val displayArtist: String
        get() = primaryArtist().ifBlank { UNKNOWN_ARTIST }

    val displayAlbum: String
        get() = albumName?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM

    val isVisual: Boolean
        get() = mediaKind == MediaKind.PHOTO || mediaKind == MediaKind.VIDEO
}

/** True when tags identify this audio file as a song (title and/or artist). */
fun LocalFile.isSong(): Boolean =
    mediaKind == MediaKind.AUDIO &&
        (!title.isNullOrBlank() || !artist.isNullOrBlank() || !albumArtist.isNullOrBlank())

fun LocalFile.primaryArtist(): String {
    albumArtist?.trim()?.takeIf { it.isNotBlank() }?.let { return stripFeaturing(it).first }
    val raw = artist?.trim()?.takeIf { it.isNotBlank() } ?: return ""
    return stripFeaturing(raw).first
}

fun LocalFile.songTitle(): String {
    title?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return titleFromFileName()
}

/** Top line: `"Artist" - "Song"` for identified tracks, cleaned file name otherwise. */
fun LocalFile.trackHeadline(): String {
    if (!isSong()) return fileBaseName()
    return "${displayArtist} - ${songTitle()}"
}

/** Bottom line: collaborations (if any) and album for songs. */
fun LocalFile.trackSubtitle(): String {
    if (!isSong()) return "Audio file"
    return listOfNotNull(
        collaborations().takeIf { it.isNotBlank() },
        albumName?.trim()?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

fun LocalFile.collaborations(): String {
    val primary = primaryArtist()
    if (primary.isBlank()) return ""
    val guests = linkedSetOf<String>()
    val rawArtist = artist?.trim().orEmpty()
    if (rawArtist.isNotBlank()) {
        val (lead, featured) = stripFeaturing(rawArtist)
        splitArtists(lead)
            .drop(if (albumArtist.isNullOrBlank()) 1 else 0)
            .forEach { guests += it }
        splitArtists(featured).forEach { guests += it }
    }
    return guests
        .filter { !it.equals(primary, ignoreCase = true) }
        .joinToString(", ")
}

private fun LocalFile.fileBaseName(): String =
    name.substringBeforeLast('.').takeIf { name.contains('.') && it.isNotBlank() } ?: name

private fun LocalFile.titleFromFileName(): String {
    val bare = fileBaseName()
    val primary = primaryArtist()
    if (primary.isNotBlank()) {
        for (sep in listOf(" - ", " – ", " — ", " ~ ")) {
            val prefix = primary + sep
            if (bare.startsWith(prefix, ignoreCase = true)) {
                return bare.substring(prefix.length).trim().ifBlank { bare }
            }
        }
    }
    return bare
}

private fun stripFeaturing(value: String): Pair<String, String> {
    val match = FEATURING_SPLIT.find(value) ?: return value.trim() to ""
    val lead = value.substring(0, match.range.first).trim()
    val featured = value.substring(match.range.last + 1).trim()
    return lead to featured
}

private fun splitArtists(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    return value.split(ARTIST_SPLIT)
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private val FEATURING_SPLIT = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE)
private val ARTIST_SPLIT = Regex("""\s*[,;/&]\s*|\s+and\s+""", RegexOption.IGNORE_CASE)

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

fun LocalFile.hasLocalBytes(): Boolean =
    onDevice && path.isNotBlank() && java.io.File(path).isFile

fun parseMediaKind(value: String?): MediaKind =
    when (value?.lowercase()) {
        "photo" -> MediaKind.PHOTO
        "video" -> MediaKind.VIDEO
        "audio" -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }

fun parseResourceAccess(value: String?): ResourceAccess =
    when (value?.lowercase()) {
        "read" -> ResourceAccess.READ
        "write" -> ResourceAccess.WRITE
        else -> ResourceAccess.OWNER
    }

fun parseShareResourceType(value: String?): ShareResourceType =
    when (value?.lowercase()) {
        "album" -> ShareResourceType.ALBUM
        else -> ShareResourceType.FILE
    }

fun parseSharePermission(value: String?): SharePermission =
    when (value?.lowercase()) {
        "write" -> SharePermission.WRITE
        else -> SharePermission.READ
    }

fun ShareResourceType.wireValue(): String =
    when (this) {
        ShareResourceType.FILE -> "file"
        ShareResourceType.ALBUM -> "album"
    }

fun SharePermission.wireValue(): String =
    when (this) {
        SharePermission.READ -> "read"
        SharePermission.WRITE -> "write"
    }

fun AuthAction.wireValue(): String = name.lowercase()

fun parseAuthAction(value: String?): AuthAction =
    when (value?.lowercase()) {
        "register" -> AuthAction.REGISTER
        "setup" -> AuthAction.SETUP
        else -> AuthAction.LOGIN
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
    val trimmed = input.trim().trimEnd('/')
    require(trimmed.isNotBlank()) { "server address is required" }
    val absolute = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    val uri = runCatching { java.net.URI(absolute) }.getOrElse { error("server address is required") }
    val host = uri.host?.trim().orEmpty()
    require(host.isNotBlank()) { "server address is required" }
    val scheme = uri.scheme ?: "http"
    val port = when {
        uri.port > 0 -> uri.port
        scheme == "https" -> 443
        else -> 80
    }
    val hostPart = if (host.contains(':')) "[$host]" else host
    val defaultPort = if (scheme == "https") 443 else 80
    val advertised = if (port == defaultPort) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
    return DiscoveredServer(host, port, host, advertisedUrl = advertised)
}

/**
 * Prefer an mDNS TXT `url` when it names a real host. Loopback TXT values are ignored so the
 * phone does not call itself; the resolved LAN address is used instead.
 */
fun serverFromDiscovery(name: String, host: String?, port: Int, txtUrl: String?): DiscoveredServer? {
    val advertised = txtUrl?.trim()?.trimEnd('/')?.takeIf {
        (it.startsWith("http://") || it.startsWith("https://")) && !isLoopbackHttpUrl(it)
    }
    if (advertised != null) {
        return runCatching {
            parseManualServer(advertised).copy(name = name.ifBlank { advertised })
        }.getOrNull()
    }
    val rawHost = host
        ?.trim()
        ?.substringBefore('%')
        ?.removePrefix("[")
        ?.removeSuffix("]")
        .orEmpty()
    if (rawHost.isBlank() || port <= 0) return null
    if (rawHost == "0.0.0.0" || rawHost == "::" || isLoopbackHost(rawHost)) return null
    return DiscoveredServer(rawHost, port, name.ifBlank { rawHost })
}

fun isLoopbackHttpUrl(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
    return isLoopbackHost(host)
}

private fun isLoopbackHost(host: String): Boolean {
    val value = host.lowercase().removePrefix("[").removeSuffix("]")
    return value == "localhost" || value == "127.0.0.1" || value == "::1" || value == "0.0.0.0"
}
