package com.jasensic.mydrive.domain

interface ConnectivityMonitor {
    suspend fun awaitWifi()
    fun isOnWifi(): Boolean
}

interface ServerDiscovery {
    suspend fun find(timeoutMs: Long = 5_000): DiscoveredServer?
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
    suspend fun download(url: String, token: String): ByteArray
}

interface LocalMediaStore {
    suspend fun save(file: ManifestFile, bytes: ByteArray)
    suspend fun knownIds(): Set<String>
}

interface SyncStateRepository {
    suspend fun lastSyncAt(): String?
    suspend fun saveLastSyncAt(value: String)
    suspend fun saveSession(session: AuthSession)
    suspend fun session(): AuthSession?
}
