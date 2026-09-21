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
    var lastSyncSeen: String? = "unset"
    var latest: AppRelease? = null
    var failAuth = false
    override suspend fun login(baseUrl: String, username: String, password: String): AuthSession {
        logins += 1
        return AuthSession("token", username, null)
    }

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
        SyncManifest(
            generatedAt = "2026-09-15T12:00:00Z",
            files = listOf(
                ManifestFile("1", "a.jpg", 3, "image/jpeg", "", "/v1/files/1/content", MediaKind.PHOTO, "album-1"),
            ).filter { it.id !in haveFileIds },
            albums = listOf(Album("album-1", "Vacation")),
        )
    }

    override suspend fun downloadTo(
        url: String,
        token: String,
        destinationPath: String,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)?,
    ) {
        downloaded += 1
        java.io.File(destinationPath).apply {
            parentFile?.mkdirs()
            val bytes = byteArrayOf(1, 2, 3)
            writeBytes(bytes)
            onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        }
    }

    override suspend fun latestAppRelease(baseUrl: String, token: String) = latest

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

    override suspend fun trashFile(baseUrl: String, token: String, id: String) = Unit
}

class FakeStore : LocalMediaStore {
    val saved = mutableListOf<String>()
    val albums = mutableListOf<Album>()
    private val root = Files.createTempDirectory("mydrive-test").toFile()

    override fun pathFor(fileId: String): String = java.io.File(root, fileId).absolutePath

    override suspend fun commit(file: ManifestFile, path: String) {
        saved += file.id
    }

    override suspend fun knownIds(): Set<String> = saved.toSet()

    override suspend fun library() = LocalLibrary(
        albums.toList(),
        saved.map {
            LocalFile(it, "$it.jpg", "image/jpeg", MediaKind.PHOTO, "album-1", "Vacation", 3, pathFor(it))
        },
    )

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
    override suspend fun lastSyncAt() = last
    override suspend fun saveLastSyncAt(value: String) {
        last = value
    }
    override suspend fun saveSession(session: AuthSession) {
        stored = session
    }
    override suspend fun session() = stored
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

private fun useCase(
    remote: FakeRemote = FakeRemote(),
    store: FakeStore = FakeStore(),
    state: FakeState = FakeState(),
    server: DiscoveredServer? = DiscoveredServer("192.168.1.10", 8080, "my-drive"),
) = SyncFilesUseCase(
    FakeConnectivity(),
    DiscoverServerUseCase(FakeConnectivity(), FakeDiscovery(server), state),
    remote,
    store,
    state,
    "Phone A",
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
