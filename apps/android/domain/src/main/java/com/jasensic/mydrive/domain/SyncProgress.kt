package com.jasensic.mydrive.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncPhase {
    IDLE,
    CONNECTING,
    PREPARING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class SyncProgress(
    val phase: SyncPhase = SyncPhase.IDLE,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    /** The file most recently started; several download at once. */
    val currentFileName: String? = null,
    /** Bytes received / expected across every file of this sync, not just one. */
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = phase == SyncPhase.CONNECTING ||
            phase == SyncPhase.PREPARING ||
            phase == SyncPhase.DOWNLOADING

    val fraction: Float
        get() = syncProgressFraction(completedFiles, totalFiles, bytesDone, bytesTotal)

    val percent: Int
        get() = (fraction * 100f).toInt().coerceIn(0, 100)
}

/**
 * Overall 0..1 progress: by bytes when the sizes are known (so one huge video outweighs a
 * hundred thumbnails), otherwise by the number of finished files.
 */
fun syncProgressFraction(
    completedFiles: Int,
    totalFiles: Int,
    bytesDone: Long = 0L,
    bytesTotal: Long = 0L,
): Float {
    if (totalFiles <= 0) return 0f
    if (bytesTotal > 0L) return (bytesDone.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
    return (completedFiles.coerceIn(0, totalFiles).toFloat() / totalFiles).coerceIn(0f, 1f)
}

fun syncProgressLabel(progress: SyncProgress): String =
    when (progress.phase) {
        SyncPhase.IDLE -> ""
        SyncPhase.CONNECTING -> progress.message ?: "Searching the LAN…"
        SyncPhase.PREPARING -> progress.message ?: "Preparing downloads…"
        SyncPhase.DOWNLOADING -> {
            val name = progress.currentFileName ?: "file"
            if (progress.totalFiles > 0) {
                "Downloading $name (${progress.completedFiles}/${progress.totalFiles})"
            } else {
                "Downloading $name…"
            }
        }
        SyncPhase.COMPLETED -> progress.message ?: "Download complete"
        SyncPhase.FAILED -> progress.errorMessage ?: progress.message ?: "Download failed"
    }

interface SyncProgressStore {
    fun observe(): Flow<SyncProgress>
    fun current(): SyncProgress
    fun publish(progress: SyncProgress)
}

class InMemorySyncProgressStore : SyncProgressStore {
    private val state = MutableStateFlow(SyncProgress())

    override fun observe(): Flow<SyncProgress> = state.asStateFlow()

    override fun current(): SyncProgress = state.value

    override fun publish(progress: SyncProgress) {
        state.value = progress
    }
}

/** Credentials never reach the scheduler: sign in first with [SignInUseCase], then enqueue. */
interface SyncScheduler {
    fun enqueue(manualHost: String? = null)

    /** Keep syncing on a schedule while the app is not in the foreground. */
    fun ensureBackgroundSync() {}

    /** Sync started because the phone joined Wi-Fi, not because the user tapped sync. */
    fun enqueueBackground() {}
}

/**
 * True when a signed-in phone has just joined a Wi-Fi network.
 * The same network id does not sync again; the hourly job covers staying on that network.
 */
fun shouldStartWifiSync(previousNetworkId: String?, networkId: String?, loggedIn: Boolean): Boolean {
    if (!loggedIn || networkId.isNullOrBlank()) return false
    return networkId != previousNetworkId
}
