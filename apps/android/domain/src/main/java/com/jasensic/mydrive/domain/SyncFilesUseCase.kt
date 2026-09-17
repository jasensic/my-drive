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
) {
    suspend fun execute(
        username: String? = null,
        password: String? = null,
        manualHost: String? = null,
    ): SyncResult {
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
        return try {
            val have = localStore.knownIds()
            val manifest = remote.fetchManifest(base, session.token, deviceId, state.lastSyncAt(), have)
            localStore.replaceAlbums(manifest.albums)
            var downloaded = 0
            for (file in manifest.files) {
                val path = localStore.pathFor(file.id)
                remote.downloadTo(lanUrl(base, file.url), session.token, path)
                if (file.checksum.isNotBlank() && file.checksum.startsWith("sha256:") &&
                    !checksumMatchesPath(file.checksum, path)
                ) {
                    java.io.File(path).delete()
                    error("checksum mismatch for ${file.name}")
                }
                localStore.commit(file, path)
                downloaded += 1
            }
            state.saveLastSyncAt(manifest.generatedAt)
            SyncResult(server, manifest, downloaded)
        } catch (err: Throwable) {
            if (err.message == "login required") {
                state.clearSession()
            }
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
