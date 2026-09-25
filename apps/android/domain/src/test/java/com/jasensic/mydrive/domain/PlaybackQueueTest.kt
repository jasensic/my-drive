package com.jasensic.mydrive.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackQueueTest {
    private fun track(id: String) = LocalFile(
        id = id,
        name = "$id.mp3",
        mime = "audio/mpeg",
        mediaKind = MediaKind.AUDIO,
        albumId = null,
        albumName = null,
        size = 1,
        path = "/tmp/$id.mp3",
        title = id,
    )

    @Test
    fun removeDropsOnlyTheRequestedTrack() {
        val queue = listOf(track("a"), track("b"), track("c"))
        val next = editQueueRemove(queue, "b")
        assertEquals(listOf("a", "c"), next?.map { it.id })
        assertNull(editQueueRemove(queue, "missing"))
    }

    @Test
    fun moveReordersAndClamps() {
        val queue = listOf(track("a"), track("b"), track("c"))
        assertEquals(listOf("b", "a", "c"), editQueueMove(queue, 1, 0)?.map { it.id })
        assertEquals(listOf("b", "c", "a"), editQueueMove(queue, 0, 5)?.map { it.id })
        assertEquals(listOf("a", "b", "c"), editQueueMove(queue, 1, 1)?.map { it.id })
        assertNull(editQueueMove(queue, -1, 0))
    }

    @Test
    fun resumeIndexFollowsTheCurrentTrack() {
        val queue = listOf(track("a"), track("c"))
        assertEquals(1, queueIndexAfterEdit(queue, "c"))
        assertEquals(0, queueIndexAfterEdit(queue, "missing"))
        assertEquals(-1, queueIndexAfterEdit(emptyList(), "a"))
    }
}
