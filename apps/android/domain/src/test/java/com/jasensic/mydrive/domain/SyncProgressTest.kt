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
    fun fractionRefinesWithCurrentFileBytes() {
        // 1 of 2 files done + half of the current file => 0.5 + 0.25 = 0.75
        assertEquals(0.75f, syncProgressFraction(1, 2, currentBytes = 50, currentTotalBytes = 100))
    }

    @Test
    fun fractionIgnoresBytesWhenAllFilesCompleted() {
        assertEquals(1f, syncProgressFraction(2, 2, currentBytes = 50, currentTotalBytes = 100))
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
