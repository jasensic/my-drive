package com.jasensic.mydrive.domain

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

const val LAN_TIMEOUT_MS = 8_000L
const val WIFI_UNAVAILABLE = "Wi-Fi not available"

class DiscoverServerUseCase(
    private val connectivity: ConnectivityMonitor,
    private val discovery: ServerDiscovery,
    private val state: SyncStateRepository,
) {
    suspend fun execute(manualHost: String? = null): DiscoveredServer {
        awaitLan()
        val server = if (!manualHost.isNullOrBlank()) {
            parseManualServer(manualHost)
        } else {
            discovery.find(LAN_TIMEOUT_MS) ?: state.lastServer()
                ?: error("my-drive server not found on LAN")
        }
        state.saveServer(server)
        return server
    }

    private suspend fun awaitLan() {
        if (connectivity.isOnWifi()) return
        try {
            withTimeout(LAN_TIMEOUT_MS) {
                connectivity.awaitWifi()
            }
        } catch (_: TimeoutCancellationException) {
            error(WIFI_UNAVAILABLE)
        }
        if (!connectivity.isOnWifi()) {
            error(WIFI_UNAVAILABLE)
        }
    }
}

class ListLocalLibraryUseCase(
    private val localStore: LocalMediaStore,
) {
    suspend fun execute(): LocalLibrary = localStore.library()

    suspend fun enrich(): LocalLibrary = localStore.enrichMetadata()
}

data class AppSnapshot(
    val server: DiscoveredServer?,
    val lastSync: String?,
    val loggedIn: Boolean,
    val library: LocalLibrary,
    val username: String = "",
    val token: String? = null,
)

class LoadAppStateUseCase(
    private val state: SyncStateRepository,
    private val localStore: LocalMediaStore,
) {
    /** Session + Room catalog only. Does not wait for Wi-Fi, mDNS, or updates. */
    suspend fun execute(): AppSnapshot {
        val session = state.session()
        localStore.bindUser(if (session == null) null else session.userId ?: "legacy")
        return AppSnapshot(
            server = state.lastServer(),
            lastSync = state.lastSyncAt(),
            loggedIn = session != null,
            library = localStore.library(),
            username = session?.username.orEmpty(),
            token = session?.token,
        )
    }
}

class SyncFilesUseCase(
    private val connectivity: ConnectivityMonitor,
    private val discovery: DiscoverServerUseCase,
    private val remote: RemoteFileSource,
    private val localStore: LocalMediaStore,
    private val state: SyncStateRepository,
    private val deviceName: String,
    private val progress: SyncProgressStore = InMemorySyncProgressStore(),
    private val exclusions: DeviceExclusionStore = InMemoryDeviceExclusionStore(),
) {
    suspend fun execute(
        username: String? = null,
        password: String? = null,
        manualHost: String? = null,
        authAction: AuthAction = AuthAction.LOGIN,
    ): SyncResult {
        progress.publish(
            SyncProgress(
                phase = SyncPhase.CONNECTING,
                message = if (username.isNullOrBlank()) "Searching the LAN…" else "Signing in…",
            ),
        )
        return try {
            val server = discovery.execute(manualHost)
            val base = server.baseUrl
            var session = state.session()
            if (session == null) {
                require(!username.isNullOrBlank() && !password.isNullOrBlank()) { "login required" }
                val loggedIn = when (authAction) {
                    AuthAction.SETUP -> remote.setup(base, username, password)
                    AuthAction.REGISTER -> remote.register(base, username, password)
                    AuthAction.LOGIN -> remote.login(base, username, password)
                }
                localStore.bindUser(loggedIn.userId ?: "legacy")
                val deviceId = loggedIn.userId?.let { state.rememberedDeviceId(it) }
                    ?: remote.registerDevice(base, loggedIn.token, deviceName)
                session = loggedIn.copy(deviceId = deviceId)
                state.saveSession(session)
            } else {
                localStore.bindUser(session.userId ?: "legacy")
            }
            val deviceId = session.deviceId ?: error("device is not registered")
            flushDeviceExclusions(base, session.token, deviceId)
            progress.publish(
                SyncProgress(phase = SyncPhase.PREPARING, message = "Preparing downloads…"),
            )
            val excluded = exclusions.excludedIds()
            val have = localStore.knownIds()
            val lastSync = state.lastSyncAt().takeIf { have.isNotEmpty() }
            val manifest = remote.fetchManifest(base, session.token, deviceId, lastSync, have)
            localStore.replaceAlbums(manifest.albums)
            val toFetch = manifest.files.filter { it.id !in excluded }
            val total = toFetch.size
            var downloaded = 0
            var completed = 0
            for (file in toFetch) {
                progress.publish(
                    SyncProgress(
                        phase = SyncPhase.DOWNLOADING,
                        completedFiles = completed,
                        totalFiles = total,
                        currentFileName = file.name,
                        currentBytes = 0L,
                        currentTotalBytes = file.size.coerceAtLeast(0L),
                        message = "Downloading ${file.name}…",
                    ),
                )
                val path = localStore.pathFor(file.id)
                val reused = reuseLocalFile(file, path)
                if (!reused) {
                    remote.downloadTo(lanUrl(base, file.url), session.token, path) { bytesRead, contentLength ->
                        val totalBytes = when {
                            contentLength > 0L -> contentLength
                            file.size > 0L -> file.size
                            else -> 0L
                        }
                        progress.publish(
                            SyncProgress(
                                phase = SyncPhase.DOWNLOADING,
                                completedFiles = completed,
                                totalFiles = total,
                                currentFileName = file.name,
                                currentBytes = bytesRead,
                                currentTotalBytes = totalBytes,
                                message = "Downloading ${file.name}…",
                            ),
                        )
                    }
                    if (file.checksum.isNotBlank() && file.checksum.startsWith("sha256:") &&
                        !checksumMatchesPath(file.checksum, path)
                    ) {
                        java.io.File(path).delete()
                        error("checksum mismatch for ${file.name}")
                    }
                    downloaded += 1
                }
                localStore.commit(file, path)
                if (file.mediaKind == MediaKind.AUDIO) {
                    ensureAudioArtwork(base, session.token, file.id)
                }
                completed += 1
                progress.publish(
                    SyncProgress(
                        phase = SyncPhase.DOWNLOADING,
                        completedFiles = completed,
                        totalFiles = total,
                        currentFileName = file.name,
                        currentBytes = 0L,
                        currentTotalBytes = 0L,
                        message = "Downloading ${file.name}…",
                    ),
                )
            }
            for (file in localStore.library().files) {
                if (file.mediaKind == MediaKind.AUDIO) {
                    ensureAudioArtwork(base, session.token, file.id)
                }
            }
            state.saveLastSyncAt(manifest.generatedAt)
            val summary = when {
                total == 0 -> "Up to date"
                downloaded == 0 -> "Synced $total files"
                else -> "Downloaded $downloaded of $total files"
            }
            progress.publish(
                SyncProgress(
                    phase = SyncPhase.COMPLETED,
                    completedFiles = total,
                    totalFiles = total,
                    message = summary,
                ),
            )
            SyncResult(server, manifest.copy(files = toFetch), downloaded)
        } catch (err: Throwable) {
            if (err.message == "login required") {
                state.clearSession()
                localStore.bindUser(null)
            }
            progress.publish(
                SyncProgress(
                    phase = SyncPhase.FAILED,
                    errorMessage = err.message ?: "Download failed",
                    message = err.message ?: "Download failed",
                ),
            )
            throw err
        }
    }

    private suspend fun flushDeviceExclusions(base: String, token: String, deviceId: String) {
        val pending = exclusions.pendingIds()
        if (pending.isEmpty()) return
        try {
            remote.mergeDeviceExclusions(base, token, deviceId, pending)
            exclusions.markFlushed(pending)
        } catch (_: Throwable) {
            // Keep the offline queue; the next sync or LAN retry will flush.
        }
    }

    private suspend fun ensureAudioArtwork(base: String, token: String, fileId: String) {
        val artPath = localStore.artworkPathFor(fileId)
        val existing = java.io.File(artPath)
        if (existing.isFile && existing.length() > 0L) {
            localStore.rememberArtwork(fileId, artPath)
            return
        }
        val embedded = localStore.captureEmbeddedArtwork(fileId)
        if (!embedded.isNullOrBlank()) {
            val embeddedFile = java.io.File(embedded)
            if (embeddedFile.isFile && embeddedFile.length() > 0L) return
        }
        try {
            remote.downloadTo(lanUrl(base, "/v1/files/$fileId/thumbnail"), token, artPath)
            val downloaded = java.io.File(artPath)
            if (downloaded.isFile && downloaded.length() > 0L) {
                localStore.rememberArtwork(fileId, artPath)
            } else {
                downloaded.delete()
            }
        } catch (_: Throwable) {
            java.io.File(artPath).delete()
        }
    }
}

class CheckAppUpdateUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
    private val version: AppVersion,
) {
    suspend fun execute(): AppRelease? {
        val session = state.session() ?: return null
        val server = state.lastServer() ?: return null
        val latest = remote.latestAppRelease(server.baseUrl, session.token) ?: return null
        return latest.takeIf { it.versionCode > version.currentCode() }
    }
}

class OpenLocalFileUseCase(
    private val opener: ExternalFileOpener,
) {
    fun execute(file: LocalFile) {
        val local = java.io.File(file.path)
        if (!local.isFile) {
            error("File is not on this device yet. Sync first.")
        }
        opener.open(file)
    }
}

class ShareLocalFilesUseCase(
    private val sharer: MediaSharer,
) {
    fun execute(files: List<LocalFile>) {
        if (files.isEmpty()) return
        files.forEach { file ->
            if (!java.io.File(file.path).isFile) {
                error("File is not on this device yet. Sync first.")
            }
        }
        sharer.share(files)
    }
}

class InstallAppUpdateUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
    private val installer: AppUpdateInstaller,
) {
    suspend fun execute(release: AppRelease) {
        val session = state.session() ?: error("login required")
        val server = state.lastServer() ?: error("my-drive server not found on LAN")
        val path = installer.apkPath(release)
        remote.downloadTo(lanUrl(server.baseUrl, release.downloadUrl), session.token, path)
        if (release.checksum.isNotBlank() && release.checksum.startsWith("sha256:") &&
            !checksumMatchesPath(release.checksum, path)
        ) {
            java.io.File(path).delete()
            error("checksum mismatch for app update ${release.versionName}")
        }
        installer.install(path)
    }
}

class CheckServerStatusUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
    private val discovery: DiscoverServerUseCase,
) {
    suspend fun execute(manualHost: String? = null): ServerStatus? {
        val server = when {
            !manualHost.isNullOrBlank() -> parseManualServer(manualHost).also { state.saveServer(it) }
            else -> state.lastServer() ?: runCatching { discovery.execute() }.getOrNull()
        } ?: return null
        return remote.serverStatus(server.baseUrl)
    }
}

class SignOutUseCase(
    private val state: SyncStateRepository,
    private val localStore: LocalMediaStore,
) {
    suspend fun execute() {
        state.clearSession()
        localStore.bindUser(null)
    }
}

class LanAvailabilityUseCase(
    private val connectivity: ConnectivityMonitor,
) {
    fun isAvailable(): Boolean = connectivity.isOnWifi()
}

class ManageLibraryUseCase(
    private val remote: RemoteFileSource,
    private val local: LocalMediaStore,
    private val state: SyncStateRepository,
    private val exclusions: DeviceExclusionStore,
) {
    suspend fun createAlbum(name: String, silo: LibrarySilo): Album {
        val (server, session) = credentials()
        val album = remote.createAlbum(server.baseUrl, session.token, name, silo)
        local.upsertAlbum(album)
        return album
    }

    suspend fun renameAlbum(id: String, name: String): Album {
        val (server, session) = credentials()
        val album = remote.renameAlbum(server.baseUrl, session.token, id, name)
        local.upsertAlbum(album)
        return album
    }

    suspend fun deleteAlbum(id: String) {
        val (server, session) = credentials()
        remote.deleteAlbum(server.baseUrl, session.token, id)
        local.deleteAlbum(id)
    }

    suspend fun renameFile(id: String, name: String) {
        val (server, session) = credentials()
        remote.updateFile(server.baseUrl, session.token, id, name = name)
        local.renameFile(id, name)
    }

    suspend fun assignAlbum(ids: List<String>, albumId: String?) {
        val (server, session) = credentials()
        for (id in ids) {
            remote.updateFile(
                server.baseUrl,
                session.token,
                id,
                albumId = albumId,
                clearAlbum = albumId == null,
            )
            local.assignAlbum(id, albumId)
        }
    }

    /** Remove bytes + Room immediately and exclude from this device. Does not trash the server copy. */
    suspend fun excludeFromDevice(ids: List<String>) {
        if (ids.isEmpty()) return
        local.removeFiles(ids)
        exclusions.enqueue(ids)
        runCatching { flushExclusions() }
    }

    suspend fun flushExclusions() {
        val pending = exclusions.pendingIds()
        if (pending.isEmpty()) return
        val session = state.session() ?: return
        val server = state.lastServer() ?: return
        val deviceId = session.deviceId ?: return
        remote.mergeDeviceExclusions(server.baseUrl, session.token, deviceId, pending)
        exclusions.markFlushed(pending)
    }

    private suspend fun credentials(): Pair<DiscoveredServer, AuthSession> {
        val session = state.session() ?: error("login required")
        val server = state.lastServer() ?: error("my-drive server not found on LAN")
        return server to session
    }
}

class BrowseRemoteLibraryUseCase(
    private val remote: RemoteFileSource,
    private val local: LocalMediaStore,
    private val state: SyncStateRepository,
) {
    suspend fun execute(): LocalLibrary {
        val (server, session) = requireCredentials(state)
        val base = server.baseUrl
        val albums = remote.listAlbums(base, session.token, silo = null)
        val remoteFiles = remote.listFiles(base, session.token, silo = null)
        val known = local.knownIds()
        val albumName = albums.associate { it.id to it.name }
        return LocalLibrary(
            albums = albums,
            files = remoteFiles.map { file ->
                val localPath = local.pathFor(file.id)
                val onDevice = file.id in known && java.io.File(localPath).isFile
                val title = file.name.substringBeforeLast('.').ifBlank { file.name }
                LocalFile(
                    id = file.id,
                    name = file.name,
                    mime = file.mime,
                    mediaKind = file.mediaKind,
                    albumId = file.albumId,
                    albumName = file.albumId?.let { albumName[it] },
                    size = file.size,
                    path = if (onDevice) localPath else "",
                    title = if (file.mediaKind == MediaKind.AUDIO) title else null,
                    modifiedAtMillis = parseIsoMillis(file.createdAt),
                    artworkPath = if (onDevice) {
                        local.artworkPathFor(file.id).takeIf { java.io.File(it).isFile }
                    } else {
                        file.thumbnailUrl?.let { lanUrl(base, it) }
                    },
                    onDevice = onDevice,
                    remoteUrl = lanUrl(base, file.contentUrl.ifBlank { "/v1/files/${file.id}/content" }),
                    thumbnailUrl = file.thumbnailUrl?.let { lanUrl(base, it) },
                    shared = file.shared,
                    access = file.access,
                )
            },
        )
    }
}

class StreamRemoteFileUseCase(
    private val remote: RemoteFileSource,
    private val local: LocalMediaStore,
    private val state: SyncStateRepository,
) {
    /** Original content URL (no mobile variant). Does not commit to Room. */
    suspend fun contentUrl(file: LocalFile): String {
        val server = state.lastServer() ?: error("my-drive server not found on LAN")
        val relative = file.remoteUrl?.substringBefore('?')?.takeIf { it.isNotBlank() }
            ?: "/v1/files/${file.id}/content"
        val withoutMobile = relative.replace("?variant=mobile", "").replace("&variant=mobile", "")
        return lanUrl(server.baseUrl, withoutMobile)
    }

    suspend fun execute(file: LocalFile): LocalFile {
        if (file.hasLocalBytes()) return file
        val url = contentUrl(file)
        if (file.mediaKind == MediaKind.AUDIO || file.isVisual) {
            return file.copy(remoteUrl = url, path = file.path.takeIf { java.io.File(it).isFile }.orEmpty())
        }
        val session = state.session() ?: error("login required")
        val dest = local.cachePathFor(file.id)
        remote.downloadTo(url, session.token, dest)
        return file.copy(path = dest, remoteUrl = url, onDevice = false)
    }
}

class ListUsersUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
) {
    suspend fun execute(): List<UserProfile> {
        val (server, session) = requireCredentials(state)
        return remote.listUsers(server.baseUrl, session.token)
            .filter { it.id != session.userId && it.username != session.username }
    }
}

class ListSharesUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
) {
    suspend fun execute(resourceType: ShareResourceType, resourceId: String): List<ShareGrant> {
        val (server, session) = requireCredentials(state)
        return remote.listShares(server.baseUrl, session.token, resourceType, resourceId)
    }
}

class CreateShareUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
) {
    suspend fun execute(
        resourceType: ShareResourceType,
        resourceId: String,
        granteeId: String,
        permission: SharePermission,
    ): ShareGrant {
        require(granteeId.isNotBlank()) { "Choose a user to share with" }
        val (server, session) = requireCredentials(state)
        return remote.createShare(
            server.baseUrl,
            session.token,
            resourceType,
            resourceId,
            granteeId,
            permission,
        )
    }
}

class RevokeShareUseCase(
    private val remote: RemoteFileSource,
    private val state: SyncStateRepository,
) {
    suspend fun execute(shareId: String) {
        val (server, session) = requireCredentials(state)
        remote.deleteShare(server.baseUrl, session.token, shareId)
    }
}

class InMemoryDeviceExclusionStore : DeviceExclusionStore {
    private val excluded = linkedSetOf<String>()
    private val pending = linkedSetOf<String>()

    override suspend fun excludedIds(): Set<String> = excluded.toSet()

    override suspend fun pendingIds(): Set<String> = pending.toSet()

    override suspend fun enqueue(ids: Collection<String>) {
        excluded += ids
        pending += ids
    }

    override suspend fun markFlushed(ids: Collection<String>) {
        pending.removeAll(ids.toSet())
    }
}

private suspend fun requireCredentials(state: SyncStateRepository): Pair<DiscoveredServer, AuthSession> {
    val session = state.session() ?: error("login required")
    val server = state.lastServer() ?: error("my-drive server not found on LAN")
    return server to session
}

private fun parseIsoMillis(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}

fun reuseLocalFile(file: ManifestFile, path: String): Boolean {
    val dest = java.io.File(path)
    if (!dest.isFile || dest.length() <= 0L) return false
    if (file.checksum.isBlank() || !file.checksum.startsWith("sha256:")) return true
    if (checksumMatchesPath(file.checksum, path)) return true
    dest.delete()
    return false
}

fun absoluteUrl(base: String, url: String): String =
    if (url.startsWith("http")) url else base.trimEnd('/') + "/" + url.trimStart('/')

fun lanUrl(base: String, url: String): String {
    if (!url.startsWith("http")) {
        return absoluteUrl(base, url)
    }
    return try {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase()
        if (host.isNullOrBlank() || host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1") {
            val path = uri.rawPath ?: "/"
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            base.trimEnd('/') + path + query
        } else {
            url
        }
    } catch (_: Exception) {
        absoluteUrl(base, url)
    }
}

fun checksumMatches(expected: String, bytes: ByteArray): Boolean {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = digest.joinToString("") { "%02x".format(it) }
    val value = expected.substringAfter("sha256:")
    return value.equals(hex, ignoreCase = true)
}

fun checksumMatchesPath(expected: String, path: String): Boolean {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    java.io.File(path).inputStream().use { input ->
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
        }
    }
    val hex = digest.digest().joinToString("") { "%02x".format(it) }
    val value = expected.substringAfter("sha256:")
    return value.equals(hex, ignoreCase = true)
}
