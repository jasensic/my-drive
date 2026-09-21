package com.jasensic.mydrive.domain

class DiscoverServerUseCase(
    private val connectivity: ConnectivityMonitor,
    private val discovery: ServerDiscovery,
    private val state: SyncStateRepository,
) {
    suspend fun execute(manualHost: String? = null): DiscoveredServer {
        if (!connectivity.isOnWifi()) {
            connectivity.awaitWifi()
        }
        val server = if (!manualHost.isNullOrBlank()) {
            parseManualServer(manualHost)
        } else {
            discovery.find() ?: state.lastServer() ?: error("my-drive server not found on LAN")
        }
        state.saveServer(server)
        return server
    }
}

class ListLocalLibraryUseCase(
    private val localStore: LocalMediaStore,
) {
    suspend fun execute(): LocalLibrary = localStore.library()
}

data class AppSnapshot(
    val server: DiscoveredServer?,
    val lastSync: String?,
    val loggedIn: Boolean,
    val library: LocalLibrary,
)

class LoadAppStateUseCase(
    private val state: SyncStateRepository,
    private val localStore: LocalMediaStore,
    private val discovery: ServerDiscovery,
) {
    suspend fun execute(discover: Boolean): AppSnapshot {
        val known = state.lastServer()
        val server = if (discover) {
            runCatching { discovery.find() }.getOrNull() ?: known
        } else {
            known
        }
        return AppSnapshot(
            server = server,
            lastSync = state.lastSyncAt(),
            loggedIn = state.session() != null,
            library = localStore.library(),
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
) {
    suspend fun execute(
        username: String? = null,
        password: String? = null,
        manualHost: String? = null,
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
                val loggedIn = remote.login(base, username, password)
                val deviceId = remote.registerDevice(base, loggedIn.token, deviceName)
                session = loggedIn.copy(deviceId = deviceId)
                state.saveSession(session)
            }
            val deviceId = session.deviceId ?: error("device is not registered")
            progress.publish(
                SyncProgress(phase = SyncPhase.PREPARING, message = "Preparing downloads…"),
            )
            val have = localStore.knownIds()
            val lastSync = state.lastSyncAt().takeIf { have.isNotEmpty() }
            val manifest = remote.fetchManifest(base, session.token, deviceId, lastSync, have)
            localStore.replaceAlbums(manifest.albums)
            val total = manifest.files.size
            var downloaded = 0
            var completed = 0
            for (file in manifest.files) {
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
            SyncResult(server, manifest, downloaded)
        } catch (err: Throwable) {
            if (err.message == "login required") {
                state.clearSession()
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

class ManageLibraryUseCase(
    private val remote: RemoteFileSource,
    private val local: LocalMediaStore,
    private val state: SyncStateRepository,
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

    suspend fun trashFiles(ids: List<String>) {
        val (server, session) = credentials()
        for (id in ids) {
            remote.trashFile(server.baseUrl, session.token, id)
        }
        local.removeFiles(ids)
    }

    private suspend fun credentials(): Pair<DiscoveredServer, AuthSession> {
        val session = state.session() ?: error("login required")
        val server = state.lastServer() ?: error("my-drive server not found on LAN")
        return server to session
    }
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
