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
    val currentFileName: String? = null,
    val currentBytes: Long = 0L,
    val currentTotalBytes: Long = 0L,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = phase == SyncPhase.CONNECTING ||
            phase == SyncPhase.PREPARING ||
            phase == SyncPhase.DOWNLOADING

    val fraction: Float
        get() = syncProgressFraction(completedFiles, totalFiles, currentBytes, currentTotalBytes)

    val percent: Int
        get() = (fraction * 100f).toInt().coerceIn(0, 100)
}

/**
 * Overall 0..1 progress from completed files, refined by the current file's byte ratio.
 */
fun syncProgressFraction(
    completedFiles: Int,
    totalFiles: Int,
    currentBytes: Long = 0L,
    currentTotalBytes: Long = 0L,
): Float {
    if (totalFiles <= 0) return 0f
    val finished = completedFiles.coerceIn(0, totalFiles).toFloat() / totalFiles
    val withinFile = if (currentTotalBytes > 0L && completedFiles < totalFiles) {
        (currentBytes.toFloat() / currentTotalBytes.toFloat()).coerceIn(0f, 1f) / totalFiles
    } else {
        0f
    }
    return (finished + withinFile).coerceIn(0f, 1f)
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

interface SyncScheduler {
    fun enqueue(
        username: String? = null,
        password: String? = null,
        manualHost: String? = null,
    )
}
