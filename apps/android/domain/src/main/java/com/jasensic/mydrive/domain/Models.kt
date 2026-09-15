package com.jasensic.mydrive.domain

enum class MediaKind { PHOTO, VIDEO, AUDIO, OTHER }

data class ManifestFile(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val checksum: String,
    val url: String,
)

data class SyncManifest(
    val generatedAt: String,
    val files: List<ManifestFile>,
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
)

data class AuthSession(
    val token: String,
    val username: String,
    val deviceId: String?,
)
