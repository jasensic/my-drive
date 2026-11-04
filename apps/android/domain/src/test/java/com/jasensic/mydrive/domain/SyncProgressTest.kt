package com.jasensic.mydrive.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncProgressTest {
    @Test
    fun fractionIsZeroWhenTotalUnknown() {
        assertEquals(0f, syncProgressFraction(0, 0))
        assertEquals(0f, syncProgressFraction(1, 0))
    }

    @Test
    fun fractionUsesCompletedFiles() {
        assertEquals(0.5f, syncProgressFraction(1, 2))
        assertEquals(1f, syncProgressFraction(3, 3))
    }

    @Test
    fun fractionFollowsBytesWhenSizesAreKnown() {
        // One of two files done, but it was the small one: bytes say 10%, not 50%.
        assertEquals(0.1f, syncProgressFraction(1, 2, bytesDone = 10, bytesTotal = 100))
    }

    @Test
    fun fractionNeverExceedsOne() {
        assertEquals(1f, syncProgressFraction(2, 2, bytesDone = 150, bytesTotal = 100))
    }

    @Test
    fun labelIncludesFileCountsWhileDownloading() {
        val label = syncProgressLabel(
            SyncProgress(
                phase = SyncPhase.DOWNLOADING,
                completedFiles = 2,
                totalFiles = 5,
                currentFileName = "song.mp3",
            ),
        )
        assertEquals("Downloading song.mp3 (2/5)", label)
    }

    @Test
    fun activeOnlyDuringInProgressPhases() {
        assertTrue(SyncProgress(phase = SyncPhase.CONNECTING).isActive)
        assertTrue(SyncProgress(phase = SyncPhase.PREPARING).isActive)
        assertTrue(SyncProgress(phase = SyncPhase.DOWNLOADING).isActive)
        assertFalse(SyncProgress(phase = SyncPhase.COMPLETED).isActive)
        assertFalse(SyncProgress(phase = SyncPhase.FAILED).isActive)
        assertFalse(SyncProgress().isActive)
    }

    @Test
    fun storePublishesLatestProgress() = kotlinx.coroutines.test.runTest {
        val store = InMemorySyncProgressStore()
        store.publish(SyncProgress(phase = SyncPhase.DOWNLOADING, completedFiles = 1, totalFiles = 4))
        assertEquals(1, store.current().completedFiles)
        assertEquals(SyncPhase.DOWNLOADING, store.current().phase)
    }
}
