package com.jasensic.mydrive.domain

interface ConnectivityMonitor {
    suspend fun awaitWifi()
    fun isOnWifi(): Boolean
}

interface ServerDiscovery {
    suspend fun find(timeoutMs: Long = 8_000): DiscoveredServer?
}

interface RemoteFileSource {
    suspend fun serverStatus(baseUrl: String): ServerStatus
    suspend fun setup(baseUrl: String, username: String, password: String): AuthSession
    suspend fun register(baseUrl: String, username: String, password: String): AuthSession
    suspend fun login(baseUrl: String, username: String, password: String): AuthSession
    suspend fun listUsers(baseUrl: String, token: String): List<UserProfile>
    suspend fun registerDevice(baseUrl: String, token: String, name: String): String
    suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ): SyncManifest
    suspend fun downloadTo(
        url: String,
        token: String,
        destinationPath: String,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)? = null,
    )
    suspend fun latestAppRelease(baseUrl: String, token: String): AppRelease?
    suspend fun listAlbums(baseUrl: String, token: String, silo: LibrarySilo?): List<Album>
    suspend fun listFiles(baseUrl: String, token: String, silo: LibrarySilo?): List<RemoteFile>
    suspend fun createAlbum(baseUrl: String, token: String, name: String, silo: LibrarySilo): Album
    suspend fun renameAlbum(baseUrl: String, token: String, id: String, name: String): Album
    suspend fun deleteAlbum(baseUrl: String, token: String, id: String)
    suspend fun updateFile(
        baseUrl: String,
        token: String,
        id: String,
        name: String? = null,
        albumId: String? = null,
        clearAlbum: Boolean = false,
    ): ManifestFile
    suspend fun mergeDeviceExclusions(
        baseUrl: String,
        token: String,
        deviceId: String,
        fileIds: Collection<String>,
    ): Set<String>
    suspend fun listShares(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType?,
        resourceId: String?,
    ): List<ShareGrant>
    suspend fun createShare(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType,
        resourceId: String,
        granteeId: String,
        permission: SharePermission,
    ): ShareGrant
    suspend fun deleteShare(baseUrl: String, token: String, shareId: String)
}

interface LocalMediaStore {
    /** Isolate Room + filesDir/media for this account. Null userId while logged out yields an empty catalog. */
    fun bindUser(userId: String?) {}
    fun pathFor(fileId: String): String
    fun artworkPathFor(fileId: String): String
    fun cachePathFor(fileId: String): String = pathFor("cache-$fileId")
    suspend fun commit(file: ManifestFile, path: String)
    suspend fun knownIds(): Set<String>
    /** Fast catalog from local storage. Must not decode tags or artwork. */
    suspend fun library(): LocalLibrary
    /** Fill cached tags/artwork for rows that still need it. Returns the updated catalog. */
    suspend fun enrichMetadata(): LocalLibrary = library()
    /** Copy an embedded picture to [artworkPathFor]. Returns the path if a JPEG now exists. */
    suspend fun captureEmbeddedArtwork(fileId: String): String? = null
    suspend fun rememberArtwork(fileId: String, artworkPath: String) {}
    suspend fun replaceAlbums(albums: List<Album>)
    suspend fun upsertAlbum(album: Album)
    suspend fun deleteAlbum(id: String)
    suspend fun renameFile(id: String, name: String)
    suspend fun assignAlbum(id: String, albumId: String?)
    suspend fun removeFiles(ids: Collection<String>)
}

interface SyncStateRepository {
    suspend fun lastSyncAt(): String?
    suspend fun saveLastSyncAt(value: String)
    suspend fun saveSession(session: AuthSession)
    suspend fun session(): AuthSession?
    fun cachedToken(): String? = null
    suspend fun rememberedDeviceId(userId: String): String? = null
    suspend fun saveServer(server: DiscoveredServer)
    suspend fun lastServer(): DiscoveredServer?
    suspend fun clearSession()
}

interface DeviceExclusionStore {
    suspend fun excludedIds(): Set<String>
    suspend fun pendingIds(): Set<String>
    suspend fun enqueue(ids: Collection<String>)
    suspend fun markFlushed(ids: Collection<String>)
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

interface MediaSharer {
    fun share(files: List<LocalFile>)
}

interface ThemePreferences {
    fun observe(): kotlinx.coroutines.flow.Flow<ThemeMode>
    suspend fun set(mode: ThemeMode)
}
