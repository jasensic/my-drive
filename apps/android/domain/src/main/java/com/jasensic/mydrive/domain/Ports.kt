package com.jasensic.mydrive.domain

interface ConnectivityMonitor {
    suspend fun awaitWifi()
    fun isOnWifi(): Boolean
}

interface ServerDiscovery {
    suspend fun find(timeoutMs: Long = 8_000): DiscoveredServer?
}

interface RemoteFileSource {
    suspend fun login(baseUrl: String, username: String, password: String): AuthSession
    suspend fun registerDevice(baseUrl: String, token: String, name: String): String
    suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ): SyncManifest
    suspend fun downloadTo(url: String, token: String, destinationPath: String)
    suspend fun latestAppRelease(baseUrl: String, token: String): AppRelease?
}

interface LocalMediaStore {
    fun pathFor(fileId: String): String
    suspend fun commit(file: ManifestFile, path: String)
    suspend fun knownIds(): Set<String>
    suspend fun library(): LocalLibrary
    suspend fun replaceAlbums(albums: List<Album>)
}

interface SyncStateRepository {
    suspend fun lastSyncAt(): String?
    suspend fun saveLastSyncAt(value: String)
    suspend fun saveSession(session: AuthSession)
    suspend fun session(): AuthSession?
    suspend fun saveServer(server: DiscoveredServer)
    suspend fun lastServer(): DiscoveredServer?
    suspend fun clearSession()
}

interface AppVersion {
    fun currentCode(): Int
    fun currentName(): String
}

interface AppUpdateInstaller {
    fun apkPath(release: AppRelease): String
    suspend fun install(apkPath: String)
}

interface AudioPlayer {
    fun observe(): kotlinx.coroutines.flow.Flow<PlaybackState>
    fun playQueue(files: List<LocalFile>, startId: String)
    fun playPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun toggleShuffle()
    fun cycleRepeat()
    fun pause()
    fun stop()
}

interface ExternalFileOpener {
    fun open(file: LocalFile)
}

interface ThemePreferences {
    fun observe(): kotlinx.coroutines.flow.Flow<ThemeMode>
    suspend fun set(mode: ThemeMode)
}
