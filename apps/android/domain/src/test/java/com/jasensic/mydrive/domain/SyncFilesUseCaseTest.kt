package com.jasensic.mydrive.domain

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeConnectivity : ConnectivityMonitor {
    override suspend fun awaitWifi() = Unit
    override fun isOnWifi() = true
}

class FakeDiscovery(private val server: DiscoveredServer?) : ServerDiscovery {
    override suspend fun find(timeoutMs: Long) = server
}

class FakeRemote : RemoteFileSource {
    var downloaded = 0
    var logins = 0
    var registers = 0
    var setups = 0
    var lastSyncSeen: String? = "unset"
    var latest: AppRelease? = null
    var failAuth = false
    var failThumbnails = false
    var failExclusions = false
    var setupRequired = false
    var manifestFiles: List<ManifestFile>? = null
    val downloadedUrls = mutableListOf<String>()
    val exclusionPuts = mutableListOf<List<String>>()
    val listedSilos = mutableListOf<LibrarySilo?>()
    var remoteFiles: List<RemoteFile> = emptyList()
    var remoteAlbums: List<Album> = emptyList()
    var users: List<UserProfile> = listOf(UserProfile("u-2", "bob"))
    val createdShares = mutableListOf<ShareGrant>()
    var deletedShareIds = mutableListOf<String>()

    override suspend fun serverStatus(baseUrl: String) = ServerStatus(setupRequired)

    override suspend fun setup(baseUrl: String, username: String, password: String): AuthSession {
        setups += 1
        return AuthSession("token", username, null, "user-1")
    }

    override suspend fun register(baseUrl: String, username: String, password: String): AuthSession {
        registers += 1
        return AuthSession("token", username, null, "user-1")
    }

    override suspend fun login(baseUrl: String, username: String, password: String): AuthSession {
        logins += 1
        return AuthSession("token", username, null, "user-1")
    }

    override suspend fun listUsers(baseUrl: String, token: String) = users

    override suspend fun registerDevice(baseUrl: String, token: String, name: String) = "dev-1"

    override suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ) = if (failAuth) {
        error("login required")
    } else {
        lastSyncSeen = lastSyncAt
        val files = manifestFiles ?: listOf(
            ManifestFile("1", "a.jpg", 3, "image/jpeg", "", "/v1/files/1/content", MediaKind.PHOTO, "album-1"),
        )
        SyncManifest(
            generatedAt = "2026-09-15T12:00:00Z",
            files = files.filter { it.id !in haveFileIds },
            albums = listOf(Album("album-1", "Vacation")),
        )
    }

    override suspend fun downloadTo(
        url: String,
        token: String,
        destinationPath: String,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)?,
    ) {
        downloadedUrls += url
        if (failThumbnails && url.contains("/thumbnail")) {
            error("download failed: 404")
        }
        downloaded += 1
        java.io.File(destinationPath).apply {
            parentFile?.mkdirs()
            val bytes = byteArrayOf(1, 2, 3)
            writeBytes(bytes)
            onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        }
    }

    override suspend fun latestAppRelease(baseUrl: String, token: String) = latest

    override suspend fun listAlbums(baseUrl: String, token: String, silo: LibrarySilo?) = remoteAlbums

    override suspend fun listFiles(baseUrl: String, token: String, silo: LibrarySilo?): List<RemoteFile> {
        listedSilos += silo
        return remoteFiles
    }

    override suspend fun createAlbum(baseUrl: String, token: String, name: String, silo: LibrarySilo) =
        Album("new-album", name, silo)

    override suspend fun renameAlbum(baseUrl: String, token: String, id: String, name: String) =
        Album(id, name)

    override suspend fun deleteAlbum(baseUrl: String, token: String, id: String) = Unit

    override suspend fun updateFile(
        baseUrl: String,
        token: String,
        id: String,
        name: String?,
        albumId: String?,
        clearAlbum: Boolean,
    ) = ManifestFile(id, name ?: "a.jpg", 3, "image/jpeg", "", "/v1/files/$id/content", MediaKind.PHOTO, if (clearAlbum) null else albumId)

    override suspend fun mergeDeviceExclusions(
        baseUrl: String,
        token: String,
        deviceId: String,
        fileIds: Collection<String>,
    ): Set<String> {
        if (failExclusions) error("offline")
        val sent = fileIds.toList()
        exclusionPuts += sent
        return sent.toSet()
    }

    override suspend fun listShares(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType?,
        resourceId: String?,
    ) = createdShares.filter {
        (resourceType == null || it.resourceType == resourceType) &&
            (resourceId == null || it.resourceId == resourceId)
    }

    override suspend fun createShare(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType,
        resourceId: String,
        granteeId: String,
        permission: SharePermission,
    ): ShareGrant {
        val grant = ShareGrant("share-1", resourceType, resourceId, "user-1", granteeId, "bob", permission)
        createdShares += grant
        return grant
    }

    override suspend fun deleteShare(baseUrl: String, token: String, shareId: String) {
        deletedShareIds += shareId
        createdShares.removeAll { it.id == shareId }
    }
}

class FakeStore : LocalMediaStore {
    val saved = mutableListOf<String>()
    val albums = mutableListOf<Album>()
    val artwork = mutableMapOf<String, String>()
    var boundUserId: String? = UNBOUND
    var embeddedArtwork: (String) -> String? = { null }
    private val committed = mutableMapOf<String, ManifestFile>()
    private val root = Files.createTempDirectory("mydrive-test").toFile()

    override fun bindUser(userId: String?) {
        boundUserId = userId
        if (userId == null) {
            saved.clear()
            albums.clear()
            artwork.clear()
            committed.clear()
        }
    }

    override fun pathFor(fileId: String): String = java.io.File(root, fileId).absolutePath

    override fun artworkPathFor(fileId: String): String = java.io.File(root, "$fileId.art.jpg").absolutePath

    override suspend fun commit(file: ManifestFile, path: String) {
        saved += file.id
        committed[file.id] = file
    }

    override suspend fun knownIds(): Set<String> = saved.toSet()

    override suspend fun library() = LocalLibrary(
        albums.toList(),
        saved.distinct().map { id ->
            val file = committed[id]
            LocalFile(
                id,
                file?.name ?: "$id.jpg",
                file?.mime ?: "image/jpeg",
                file?.mediaKind ?: MediaKind.PHOTO,
                file?.albumId ?: "album-1",
                albums.find { it.id == (file?.albumId ?: "album-1") }?.name ?: "Vacation",
                file?.size ?: 3,
                pathFor(id),
                artworkPath = artwork[id],
            )
        },
    )

    override suspend fun captureEmbeddedArtwork(fileId: String): String? {
        val path = embeddedArtwork(fileId) ?: return null
        rememberArtwork(fileId, path)
        return path
    }

    override suspend fun rememberArtwork(fileId: String, artworkPath: String) {
        artwork[fileId] = artworkPath
    }

    override suspend fun replaceAlbums(albums: List<Album>) {
        this.albums.clear()
        this.albums += albums
    }

    override suspend fun upsertAlbum(album: Album) {
        albums.removeAll { it.id == album.id }
        albums += album
    }

    override suspend fun deleteAlbum(id: String) {
        albums.removeAll { it.id == id }
    }

    override suspend fun renameFile(id: String, name: String) = Unit

    override suspend fun assignAlbum(id: String, albumId: String?) = Unit

    override suspend fun removeFiles(ids: Collection<String>) {
        saved.removeAll(ids.toSet())
    }
}

class FakeState : SyncStateRepository {
    var stored: AuthSession? = null
    var last: String? = null
    var server: DiscoveredServer? = null
    val devices = mutableMapOf<String, String>()
    override suspend fun lastSyncAt() = last
    override suspend fun saveLastSyncAt(value: String) {
        last = value
    }
    override suspend fun saveSession(session: AuthSession) {
        stored = session
        session.userId?.let { id -> session.deviceId?.let { devices[id] = it } }
    }
    override suspend fun session() = stored
    override fun cachedToken() = stored?.token
    override suspend fun rememberedDeviceId(userId: String) = devices[userId]
    override suspend fun saveServer(server: DiscoveredServer) {
        this.server = server
    }
    override suspend fun lastServer() = server
    override suspend fun clearSession() {
        stored = null
    }
}

class FakeVersion(private val code: Int = 1) : AppVersion {
    override fun currentCode() = code
    override fun currentName() = "0.1.0"
}

private const val UNBOUND = "__unbound__"

private fun useCase(
    remote: FakeRemote = FakeRemote(),
    store: FakeStore = FakeStore(),
    state: FakeState = FakeState(),
    server: DiscoveredServer? = DiscoveredServer("192.168.1.10", 8080, "my-drive"),
    exclusions: DeviceExclusionStore = InMemoryDeviceExclusionStore(),
) = SyncFilesUseCase(
    FakeConnectivity(),
    DiscoverServerUseCase(FakeConnectivity(), FakeDiscovery(server), state),
    remote,
    store,
    state,
    "Phone A",
    exclusions = exclusions,
)

class SyncFilesUseCaseTest {
    @Test
    fun downloadsMissingFilesAndRecordsSync() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState()
        val result = useCase(remote, store, state).execute("admin", "password123")
        assertEquals(1, result.downloaded)
        assertEquals(listOf("1"), store.saved)
        assertEquals("dev-1", state.stored?.deviceId)
        assertEquals("2026-09-15T12:00:00Z", state.last)
        assertEquals(1, remote.downloaded)
        assertEquals("Vacation", store.albums.single().name)
        assertEquals("192.168.1.10", state.server?.host)
        assertEquals(1, remote.logins)
    }

    @Test
    fun requiresCredentialsOnlyWhenNoStoredSession() = runTest {
        val err = assertFailsWith<IllegalArgumentException> {
            useCase().execute()
        }
        assertEquals("login required", err.message)
    }

    @Test
    fun reusesStoredSessionAndDoesNotLoginAgain() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        useCase(remote, store, state).execute("admin", "ignored-password")
        assertEquals(0, remote.logins)
        assertEquals("t", state.stored?.token)
    }

    @Test
    fun clearsSessionWhenServerRejectsStoredToken() = runTest {
        val remote = FakeRemote().apply { failAuth = true }
        val state = FakeState().apply { stored = AuthSession("expired", "admin", "dev-1") }
        val err = assertFailsWith<IllegalStateException> {
            useCase(remote, FakeStore(), state).execute()
        }
        assertEquals("login required", err.message)
        assertNull(state.stored)
    }

    @Test
    fun omitsLastSyncWhenLocalCatalogIsEmpty() = runTest {
        val remote = FakeRemote()
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1")
            last = "2026-09-15T12:00:00Z"
        }
        useCase(remote, FakeStore(), state).execute()
        assertNull(remote.lastSyncSeen)
        assertEquals(1, remote.downloaded)
    }

    @Test
    fun recommitsExistingBytesWithoutDownloadingAgain() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        java.io.File(store.pathFor("1")).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val result = useCase(remote, store, state).execute()
        assertEquals(0, remote.downloaded)
        assertEquals(listOf("1"), store.saved)
        assertEquals(0, result.downloaded)
    }

    @Test
    fun ipv6BaseUrlUsesBrackets() {
        assertEquals(
            "http://[fe80::1]:8080",
            DiscoveredServer("fe80::1%wlan0", 8080, "my-drive").baseUrl,
        )
    }

    @Test
    fun skipsFilesAlreadyPresent() = runTest {
        val remote = FakeRemote()
        val store = FakeStore().apply { saved += "1" }
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        val result = useCase(remote, store, state).execute()
        assertTrue(result.manifest.files.isEmpty())
        assertEquals(0, remote.downloaded)
    }

    @Test
    fun usesManualHostWhenMdnsIsUnavailable() = runTest {
        val state = FakeState()
        val result = useCase(
            state = state,
            server = null,
        ).execute("admin", "password123", manualHost = "10.0.0.8:8080")
        assertEquals("10.0.0.8", result.server.host)
        assertEquals(8080, result.server.port)
    }

    @Test
    fun absoluteUrlJoinsRelativePaths() {
        assertEquals(
            "http://192.168.1.10:8080/v1/files/1/content",
            absoluteUrl("http://192.168.1.10:8080", "/v1/files/1/content"),
        )
    }

    @Test
    fun lanUrlRewritesLocalhostToDiscoveredHost() {
        assertEquals(
            "http://192.168.1.10:8080/v1/files/1/content",
            lanUrl("http://192.168.1.10:8080", "http://localhost/v1/files/1/content"),
        )
    }

    @Test
    fun lanUrlKeepsMobileVariantQuery() {
        assertEquals(
            "http://192.168.1.10:8080/v1/files/1/content?variant=mobile",
            lanUrl("http://192.168.1.10:8080", "http://localhost/v1/files/1/content?variant=mobile"),
        )
        assertEquals(
            "http://192.168.1.10:8080/v1/files/1/content?variant=mobile",
            lanUrl("http://192.168.1.10:8080", "/v1/files/1/content?variant=mobile"),
        )
    }

    @Test
    fun parseManualServerAcceptsHostAndPort() {
        val server = parseManualServer("http://192.168.1.5:8080/v1")
        assertEquals("192.168.1.5", server.host)
        assertEquals(8080, server.port)
    }

    @Test
    fun publishesDownloadProgressWhileSyncing() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState()
        val progress = InMemorySyncProgressStore()
        val result = SyncFilesUseCase(
            FakeConnectivity(),
            DiscoverServerUseCase(FakeConnectivity(), FakeDiscovery(DiscoveredServer("192.168.1.10", 8080, "my-drive")), state),
            remote,
            store,
            state,
            "Phone A",
            progress,
        ).execute("admin", "password123")
        assertEquals(1, result.downloaded)
        assertEquals(SyncPhase.COMPLETED, progress.current().phase)
        assertEquals(1, progress.current().totalFiles)
        assertEquals(1, progress.current().completedFiles)
    }

    @Test
    fun wifiTimeoutFailsSyncAndClearsBusyPhase() = runTest {
        val progress = InMemorySyncProgressStore()
        val hanging = object : ConnectivityMonitor {
            override suspend fun awaitWifi() = kotlinx.coroutines.awaitCancellation()
            override fun isOnWifi() = false
        }
        val err = assertFailsWith<IllegalStateException> {
            SyncFilesUseCase(
                hanging,
                DiscoverServerUseCase(hanging, FakeDiscovery(null), FakeState()),
                FakeRemote(),
                FakeStore(),
                FakeState(),
                "Phone A",
                progress,
            ).execute("admin", "password123")
        }
        assertEquals(WIFI_UNAVAILABLE, err.message)
        assertEquals(SyncPhase.FAILED, progress.current().phase)
        assertEquals(WIFI_UNAVAILABLE, progress.current().errorMessage)
    }

    @Test
    fun discoverServerTimesOutWhenWifiNeverArrives() = runTest {
        val hanging = object : ConnectivityMonitor {
            override suspend fun awaitWifi() = kotlinx.coroutines.awaitCancellation()
            override fun isOnWifi() = false
        }
        val err = assertFailsWith<IllegalStateException> {
            DiscoverServerUseCase(hanging, FakeDiscovery(null), FakeState()).execute()
        }
        assertEquals(WIFI_UNAVAILABLE, err.message)
    }

    @Test
    fun loadAppStateReadsSessionAndLibraryWithoutDiscovery() = runTest {
        val store = FakeStore().apply { saved += "1" }
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
            last = "2026-09-15T12:00:00Z"
        }
        val snapshot = LoadAppStateUseCase(state, store).execute()
        assertTrue(snapshot.loggedIn)
        assertEquals("192.168.1.10", snapshot.server?.host)
        assertEquals("2026-09-15T12:00:00Z", snapshot.lastSync)
        assertEquals(listOf("1"), snapshot.library.files.map { it.id })
    }

    @Test
    fun loadAppStateWithoutSessionIsNotLoggedIn() = runTest {
        val snapshot = LoadAppStateUseCase(FakeState(), FakeStore()).execute()
        kotlin.test.assertFalse(snapshot.loggedIn)
        assertNull(snapshot.server)
        assertTrue(snapshot.library.files.isEmpty())
    }

    @Test
    fun downloadsThumbnailWhenAudioHasNoEmbeddedArt() = runTest {
        val remote = FakeRemote().apply {
            manifestFiles = listOf(
                ManifestFile("a1", "song.mp3", 3, "audio/mpeg", "", "/v1/files/a1/content", MediaKind.AUDIO, null),
            )
        }
        val store = FakeStore()
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        useCase(remote, store, state).execute()
        assertTrue(remote.downloadedUrls.any { it.endsWith("/v1/files/a1/thumbnail") })
        assertEquals(store.artworkPathFor("a1"), store.artwork["a1"])
    }

    @Test
    fun skipsThumbnailWhenEmbeddedArtExists() = runTest {
        val remote = FakeRemote().apply {
            manifestFiles = listOf(
                ManifestFile("a1", "song.mp3", 3, "audio/mpeg", "", "/v1/files/a1/content", MediaKind.AUDIO, null),
            )
        }
        val store = FakeStore()
        val art = java.io.File(store.artworkPathFor("a1")).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 9, 9))
        }
        store.embeddedArtwork = { art.absolutePath }
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        useCase(remote, store, state).execute()
        assertTrue(remote.downloadedUrls.none { it.contains("/thumbnail") })
        assertEquals(art.absolutePath, store.artwork["a1"])
    }

    @Test
    fun continuesSyncIfThumbnailIsMissing() = runTest {
        val remote = FakeRemote().apply {
            failThumbnails = true
            manifestFiles = listOf(
                ManifestFile("a1", "song.mp3", 3, "audio/mpeg", "", "/v1/files/a1/content", MediaKind.AUDIO, null),
            )
        }
        val store = FakeStore()
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1") }
        val result = useCase(remote, store, state).execute()
        assertEquals(listOf("a1"), store.saved)
        assertEquals(1, result.downloaded)
        assertTrue(store.artwork["a1"] == null)
    }

    @Test
    fun storesUserIdOnLogin() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState()
        useCase(remote, store, state).execute("admin", "password123")
        assertEquals("user-1", state.stored?.userId)
        assertEquals("user-1", store.boundUserId)
    }

    @Test
    fun registerCreatesAccountInsteadOfLogin() = runTest {
        val remote = FakeRemote()
        val state = FakeState()
        useCase(remote, FakeStore(), state).execute("bob", "password123", authAction = AuthAction.REGISTER)
        assertEquals(1, remote.registers)
        assertEquals(0, remote.logins)
        assertEquals("bob", state.stored?.username)
        assertEquals("user-1", state.stored?.userId)
    }

    @Test
    fun reusesRememberedDeviceIdForTheSameUser() = runTest {
        val remote = FakeRemote()
        val state = FakeState().apply { devices["user-1"] = "dev-kept" }
        useCase(remote, FakeStore(), state).execute("admin", "password123")
        assertEquals("dev-kept", state.stored?.deviceId)
    }

    @Test
    fun skipsExcludedFilesEvenIfManifestOffersThem() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val exclusions = InMemoryDeviceExclusionStore()
        exclusions.enqueue(listOf("1"))
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1", "user-1") }
        val result = useCase(remote, store, state, exclusions = exclusions).execute()
        assertEquals(0, result.downloaded)
        assertTrue(store.saved.isEmpty())
        assertTrue(remote.downloadedUrls.none { it.contains("/v1/files/1/content") })
    }
}

class DeviceExclusionUseCaseTest {
    @Test
    fun excludeRemovesLocalBytesImmediatelyAndQueuesIds() = runTest {
        val remote = FakeRemote()
        val store = FakeStore().apply { saved += "1" }
        java.io.File(store.pathFor("1")).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val exclusions = InMemoryDeviceExclusionStore()
        val state = FakeState()
        ManageLibraryUseCase(remote, store, state, exclusions).excludeFromDevice(listOf("1"))
        assertEquals(emptyList(), store.saved)
        assertEquals(setOf("1"), exclusions.excludedIds())
        assertEquals(setOf("1"), exclusions.pendingIds())
        assertTrue(remote.exclusionPuts.isEmpty())
    }

    @Test
    fun flushSendsPendingExclusionsWhenOnline() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val exclusions = InMemoryDeviceExclusionStore()
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1", "user-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        val use = ManageLibraryUseCase(remote, store, state, exclusions)
        use.excludeFromDevice(listOf("1", "2"))
        assertEquals(listOf(listOf("1", "2")), remote.exclusionPuts)
        assertTrue(exclusions.pendingIds().isEmpty())
        assertEquals(setOf("1", "2"), exclusions.excludedIds())
    }

    @Test
    fun flushKeepsQueueWhenDevicePutFails() = runTest {
        val remote = FakeRemote().apply { failExclusions = true }
        val exclusions = InMemoryDeviceExclusionStore()
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1", "user-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        ManageLibraryUseCase(remote, FakeStore(), state, exclusions).excludeFromDevice(listOf("1"))
        assertEquals(setOf("1"), exclusions.pendingIds())
        assertTrue(remote.exclusionPuts.isEmpty())
    }

    @Test
    fun syncFlushesPendingExclusionsBeforeDownloading() = runTest {
        val remote = FakeRemote()
        val exclusions = InMemoryDeviceExclusionStore()
        exclusions.enqueue(listOf("1"))
        val state = FakeState().apply { stored = AuthSession("t", "admin", "dev-1", "user-1") }
        useCase(remote, FakeStore(), state, exclusions = exclusions).execute()
        assertEquals(listOf(listOf("1")), remote.exclusionPuts)
        assertTrue(exclusions.pendingIds().isEmpty())
    }
}

class BrowseRemoteLibraryUseCaseTest {
    @Test
    fun mapsServerFilesWithoutCommittingLocally() = runTest {
        val remote = FakeRemote().apply {
            remoteAlbums = listOf(Album("al-1", "Live", LibrarySilo.MUSIC, shared = true, access = ResourceAccess.READ))
            remoteFiles = listOf(
                RemoteFile(
                    id = "f1",
                    name = "song.flac",
                    size = 12,
                    mime = "audio/flac",
                    checksum = "",
                    mediaKind = MediaKind.AUDIO,
                    albumId = "al-1",
                    contentUrl = "/v1/files/f1/content",
                    thumbnailUrl = "/v1/files/f1/thumbnail",
                    shared = true,
                    access = ResourceAccess.READ,
                ),
            )
        }
        val store = FakeStore()
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1", "user-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        val library = BrowseRemoteLibraryUseCase(remote, store, state).execute()
        assertTrue(store.saved.isEmpty())
        val file = library.files.single()
        assertEquals("http://192.168.1.10:8080/v1/files/f1/content", file.remoteUrl)
        assertEquals(false, file.onDevice)
        assertTrue(file.shared)
        assertEquals("Live", file.albumName)
    }

    @Test
    fun streamUrlUsesOriginalContentWithoutMobileVariant() = runTest {
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1", "user-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        val file = LocalFile(
            id = "f1",
            name = "song.m4a",
            mime = "audio/mp4",
            mediaKind = MediaKind.AUDIO,
            albumId = null,
            albumName = null,
            size = 1,
            path = "",
            onDevice = false,
            remoteUrl = "http://localhost/v1/files/f1/content?variant=mobile",
        )
        val url = StreamRemoteFileUseCase(FakeRemote(), FakeStore(), state).contentUrl(file)
        assertEquals("http://192.168.1.10:8080/v1/files/f1/content", url)
    }
}

class SharingUseCaseTest {
    @Test
    fun createSharePostsGranteeAndPermission() = runTest {
        val remote = FakeRemote()
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1", "user-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        val grant = CreateShareUseCase(remote, state).execute(
            ShareResourceType.FILE,
            "file-1",
            "u-2",
            SharePermission.READ,
        )
        assertEquals("u-2", grant.granteeId)
        assertEquals(SharePermission.READ, grant.permission)
        assertEquals("file-1", remote.createdShares.single().resourceId)
    }
}

class CheckAppUpdateUseCaseTest {
    @Test
    fun returnsReleaseWhenServerVersionIsNewer() = runTest {
        val remote = FakeRemote().apply {
            latest = AppRelease("r1", 5, "0.5.0", "fixes", "", 10, "/v1/app/releases/r1/apk")
        }
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        val found = CheckAppUpdateUseCase(remote, state, FakeVersion(1)).execute()
        assertEquals(5, found?.versionCode)
    }

    @Test
    fun ignoresReleaseWhenAlreadyUpToDate() = runTest {
        val remote = FakeRemote().apply {
            latest = AppRelease("r1", 1, "0.1.0", "", "", 10, "/apk")
        }
        val state = FakeState().apply {
            stored = AuthSession("t", "admin", "dev-1")
            server = DiscoveredServer("192.168.1.10", 8080, "my-drive")
        }
        assertNull(CheckAppUpdateUseCase(remote, state, FakeVersion(1)).execute())
    }
}
