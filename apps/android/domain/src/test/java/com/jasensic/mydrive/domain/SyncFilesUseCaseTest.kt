package com.jasensic.mydrive.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    override suspend fun login(baseUrl: String, username: String, password: String) =
        AuthSession("token", username, null)

    override suspend fun registerDevice(baseUrl: String, token: String, name: String) = "dev-1"

    override suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ) = SyncManifest(
        generatedAt = "2026-09-15T12:00:00Z",
        files = listOf(
            ManifestFile("1", "a.jpg", 3, "image/jpeg", "", "/v1/files/1/content"),
        ).filter { it.id !in haveFileIds },
    )

    override suspend fun download(url: String, token: String): ByteArray {
        downloaded += 1
        return byteArrayOf(1, 2, 3)
    }
}

class FakeStore : LocalMediaStore {
    val saved = mutableListOf<String>()
    override suspend fun save(file: ManifestFile, bytes: ByteArray) {
        saved += file.id
    }
    override suspend fun knownIds(): Set<String> = saved.toSet()
}

class FakeState : SyncStateRepository {
    var stored: AuthSession? = null
    var last: String? = null
    override suspend fun lastSyncAt() = last
    override suspend fun saveLastSyncAt(value: String) {
        last = value
    }
    override suspend fun saveSession(session: AuthSession) {
        stored = session
    }
    override suspend fun session() = stored
}

class SyncFilesUseCaseTest {
    @Test
    fun downloadsMissingFilesAndRecordsSync() = runTest {
        val remote = FakeRemote()
        val store = FakeStore()
        val state = FakeState()
        val useCase = SyncFilesUseCase(
            FakeConnectivity(),
            FakeDiscovery(DiscoveredServer("192.168.1.10", 8080, "my-drive")),
            remote,
            store,
            state,
            "Phone A",
        )
        val manifest = useCase.execute("admin", "password123")
        assertEquals(1, manifest.files.size)
        assertEquals(listOf("1"), store.saved)
        assertEquals("dev-1", state.stored?.deviceId)
        assertEquals("2026-09-15T12:00:00Z", state.last)
        assertEquals(1, remote.downloaded)
    }

    @Test
    fun skipsFilesAlreadyPresent() = runTest {
        val remote = FakeRemote()
        val store = FakeStore().apply { saved += "1" }
        val useCase = SyncFilesUseCase(
            FakeConnectivity(),
            FakeDiscovery(DiscoveredServer("192.168.1.10", 8080, "my-drive")),
            remote,
            store,
            FakeState().apply { stored = AuthSession("t", "admin", "dev-1") },
            "Phone A",
        )
        val manifest = useCase.execute()
        assertTrue(manifest.files.isEmpty())
        assertEquals(0, remote.downloaded)
    }

    @Test
    fun absoluteUrlJoinsRelativePaths() {
        assertEquals(
            "http://192.168.1.10:8080/v1/files/1/content",
            absoluteUrl("http://192.168.1.10:8080", "/v1/files/1/content"),
        )
    }
}
