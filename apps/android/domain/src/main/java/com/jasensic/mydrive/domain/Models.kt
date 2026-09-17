package com.jasensic.mydrive.domain

enum class MediaKind { PHOTO, VIDEO, AUDIO, OTHER }

data class Album(
    val id: String,
    val name: String,
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
    val baseUrl: String get() = "http://$host:$port"
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
)

data class LocalLibrary(
    val albums: List<Album>,
    val files: List<LocalFile>,
)

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

fun parseManualServer(input: String): DiscoveredServer {
    val trimmed = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    require(trimmed.isNotBlank()) { "server address is required" }
    val hostPort = trimmed.substringBefore("/")
    val host = hostPort.substringBefore(":")
    val port = hostPort.substringAfter(":", "80").toIntOrNull() ?: 80
    require(host.isNotBlank()) { "server address is required" }
    return DiscoveredServer(host, port, host)
}
