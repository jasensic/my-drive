package com.jasensic.mydrive.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

const val LOGIN_REQUIRED = "login required"

/** Files fetched at the same time. A few streams fill a Wi-Fi link; more only add seeks on the NAS disk. */
const val PARALLEL_DOWNLOADS = 4

/** Free space left alone, so a sync never fills the phone to the last byte. */
const val FREE_SPACE_RESERVE_BYTES = 64L * 1024 * 1024

/** The server answers 404 for a thumbnail it does not have; any other failure may be a passing network problem. */
private const val NO_THUMBNAIL = "download failed: 404"
private const val ATTEMPTS_PER_FILE = 2
private const val PROGRESS_INTERVAL_NANOS = 150_000_000L

internal class DownloadSummary(val downloaded: Int, val failures: List<SyncFailure>)

/**
 * Brings the files of a manifest onto the phone: [PARALLEL_DOWNLOADS] at a time, each one
 * isolated from the others. A file that fails is reported and left for the next sync; only a
 * rejected session ("login required") stops everything.
 */
internal class FileDownloader(
    private val remote: RemoteFileSource,
    private val localStore: LocalMediaStore,
    private val progress: SyncProgressStore,
    private val concurrency: Int = PARALLEL_DOWNLOADS,
) {
    suspend fun run(base: String, token: String, files: List<ManifestFile>): DownloadSummary = coroutineScope {
        val verified = localStore.verifiedChecksums()
        val noArtwork = localStore.artworkMissingIds()
        val tracker = ProgressTracker(progress, files.size, files.sumOf { it.size.coerceAtLeast(0L) })
        val space = SpaceBudget(localStore)
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        val downloaded = AtomicInteger()
        val failures = ConcurrentLinkedQueue<SyncFailure>()
        files.map { file ->
            async {
                gate.withPermit {
                    try {
                        tracker.start(file)
                        if (fetch(base, token, file, verified, noArtwork, tracker, space)) downloaded.incrementAndGet()
                    } catch (err: CancellationException) {
                        throw err
                    } catch (err: Throwable) {
                        if (err.message == LOGIN_REQUIRED) throw err
                        failures += SyncFailure(file.name, err.message ?: "download failed")
                    } finally {
                        tracker.settle(file)
                    }
                }
            }
        }.awaitAll()
        DownloadSummary(downloaded.get(), failures.toList())
    }

    /** True when bytes came over the network; false when the copy on the phone was already good. */
    private suspend fun fetch(
        base: String,
        token: String,
        file: ManifestFile,
        verified: Map<String, String>,
        noArtwork: Set<String>,
        tracker: ProgressTracker,
        space: SpaceBudget,
    ): Boolean {
        val path = localStore.pathFor(file.id)
        val reused = canReuse(file, path, verified)
        if (!reused) {
            space.reserve(file)
            try {
                download(base, token, file, path, tracker)
            } finally {
                space.release(file)
            }
        }
        localStore.commit(file, path)
        if (file.mediaKind == MediaKind.AUDIO) {
            ensureArtwork(base, token, file.id, noArtwork)
        }
        return !reused
    }

    private suspend fun download(base: String, token: String, file: ManifestFile, path: String, tracker: ProgressTracker) {
        var last: Throwable? = null
        repeat(ATTEMPTS_PER_FILE) {
            try {
                // A failed attempt leaves its partial bytes behind and the next one resumes from them.
                remote.downloadTo(lanUrl(base, file.url), token, path) { bytesRead, _ ->
                    tracker.bytes(file, bytesRead)
                }
                if (isSha256(file.checksum) && !hashMatches(file.checksum, path)) {
                    File(path).delete()
                    error("checksum mismatch for ${file.name}")
                }
                return
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                if (err.message == LOGIN_REQUIRED) throw err
                last = err
            }
        }
        throw last ?: error("download failed")
    }

    /**
     * Cover art for an audio file: the one embedded in it, else the server's thumbnail. A server
     * that has none is remembered, so later syncs do not decode tags or ask again.
     */
    suspend fun ensureArtwork(base: String, token: String, fileId: String, noArtwork: Set<String>) {
        val artPath = localStore.artworkPathFor(fileId)
        val existing = File(artPath)
        if (existing.isFile && existing.length() > 0L) {
            localStore.rememberArtwork(fileId, artPath)
            return
        }
        if (fileId in noArtwork) return
        val embedded = localStore.captureEmbeddedArtwork(fileId)
        if (!embedded.isNullOrBlank()) {
            val embeddedFile = File(embedded)
            if (embeddedFile.isFile && embeddedFile.length() > 0L) return
        }
        try {
            remote.downloadTo(lanUrl(base, "/v1/files/$fileId/thumbnail"), token, artPath)
            val downloaded = File(artPath)
            if (downloaded.isFile && downloaded.length() > 0L) {
                localStore.rememberArtwork(fileId, artPath)
            } else {
                downloaded.delete()
                localStore.markArtworkMissing(fileId)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Throwable) {
            if (err.message == LOGIN_REQUIRED) throw err
            File(artPath).delete()
            if (err.message == NO_THUMBNAIL) localStore.markArtworkMissing(fileId)
        }
    }

    /**
     * The stored copy is good when it was verified against this very checksum and still has the
     * right size. Copies from before checksums were recorded are hashed once, then recorded.
     */
    private suspend fun canReuse(file: ManifestFile, path: String, verified: Map<String, String>): Boolean {
        val local = File(path)
        if (!local.isFile || local.length() <= 0L) return false
        if (!isSha256(file.checksum)) return true
        val sizeMatches = file.size <= 0L || local.length() == file.size
        if (sizeMatches && verified[file.id].equals(file.checksum, ignoreCase = true)) return true
        return withContext(Dispatchers.IO) { reuseLocalFile(file, path) }
    }

    private suspend fun hashMatches(checksum: String, path: String): Boolean =
        withContext(Dispatchers.IO) { checksumMatchesPath(checksum, path) }
}

private fun isSha256(checksum: String) = checksum.startsWith("sha256:")

/** Space promised to downloads that are still running, so parallel files cannot all claim the same bytes. */
private class SpaceBudget(private val store: LocalMediaStore) {
    private var reserved = 0L

    @Synchronized
    fun reserve(file: ManifestFile) {
        val need = file.size.coerceAtLeast(0L)
        if (need + reserved + FREE_SPACE_RESERVE_BYTES > store.freeBytes()) {
            error("not enough free space for ${file.name}")
        }
        reserved += need
    }

    @Synchronized
    fun release(file: ManifestFile) {
        reserved -= file.size.coerceAtLeast(0L)
    }
}

/** Folds the progress of the files in flight into one stream of updates, at most a few per second. */
private class ProgressTracker(
    private val store: SyncProgressStore,
    private val totalFiles: Int,
    private val totalBytes: Long,
) {
    private var completed = 0
    private var settledBytes = 0L
    private var currentName: String? = null
    private var lastPublish: Long? = null
    private val inFlight = HashMap<String, Long>()

    @Synchronized
    fun start(file: ManifestFile) {
        currentName = file.name
        inFlight[file.id] = 0L
        publish()
    }

    @Synchronized
    fun bytes(file: ManifestFile, bytesRead: Long) {
        inFlight[file.id] = bytesRead
        publish()
    }

    @Synchronized
    fun settle(file: ManifestFile) {
        inFlight.remove(file.id)
        settledBytes += file.size.coerceAtLeast(0L)
        completed += 1
        publish()
    }

    private fun publish() {
        val now = System.nanoTime()
        val previous = lastPublish
        if (previous != null && now - previous < PROGRESS_INTERVAL_NANOS && completed < totalFiles) return
        lastPublish = now
        store.publish(
            SyncProgress(
                phase = SyncPhase.DOWNLOADING,
                completedFiles = completed,
                totalFiles = totalFiles,
                currentFileName = currentName,
                bytesDone = (settledBytes + inFlight.values.sum()).coerceAtMost(totalBytes),
                bytesTotal = totalBytes,
                message = "Downloading ${currentName ?: "files"}…",
            ),
        )
    }
}
