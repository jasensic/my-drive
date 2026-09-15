package com.jasensic.mydrive.domain

class SyncFilesUseCase(
    private val connectivity: ConnectivityMonitor,
    private val discovery: ServerDiscovery,
    private val remote: RemoteFileSource,
    private val localStore: LocalMediaStore,
    private val state: SyncStateRepository,
    private val deviceName: String,
) {
    suspend fun execute(username: String? = null, password: String? = null): SyncManifest {
        if (!connectivity.isOnWifi()) {
            connectivity.awaitWifi()
        }
        val server = discovery.find() ?: error("my-drive server not found on LAN")
        val base = "http://${server.host}:${server.port}"
        var session = state.session()
        if (session == null) {
            require(!username.isNullOrBlank() && !password.isNullOrBlank()) { "login required" }
            session = remote.login(base, username, password)
            val deviceId = remote.registerDevice(base, session.token, deviceName)
            session = session.copy(deviceId = deviceId)
            state.saveSession(session)
        }
        val deviceId = session.deviceId ?: error("device is not registered")
        val have = localStore.knownIds()
        val manifest = remote.fetchManifest(base, session.token, deviceId, state.lastSyncAt(), have)
        for (file in manifest.files) {
            val bytes = remote.download(absoluteUrl(base, file.url), session.token)
            if (file.checksum.isNotBlank() && file.checksum.startsWith("sha256:") &&
                !checksumMatches(file.checksum, bytes)
            ) {
                error("checksum mismatch for ${file.name}")
            }
            localStore.save(file, bytes)
        }
        state.saveLastSyncAt(manifest.generatedAt)
        return manifest
    }
}

fun absoluteUrl(base: String, url: String): String =
    if (url.startsWith("http")) url else base.trimEnd('/') + "/" + url.trimStart('/')

fun checksumMatches(expected: String, bytes: ByteArray): Boolean {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = digest.joinToString("") { "%02x".format(it) }
    val value = expected.substringAfter("sha256:")
    return value.equals(hex, ignoreCase = true)
}
