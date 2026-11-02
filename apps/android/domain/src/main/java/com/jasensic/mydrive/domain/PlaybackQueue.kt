package com.jasensic.mydrive.domain

/** Drop one queued track. Returns null when the id is not in the queue. */
fun editQueueRemove(queue: List<LocalFile>, fileId: String): List<LocalFile>? {
    if (queue.none { it.id == fileId }) return null
    return queue.filterNot { it.id == fileId }
}

/**
 * Move a queued track. Indices are positions in the visible play order.
 * Returns null when `fromIndex` is outside the queue.
 */
fun editQueueMove(queue: List<LocalFile>, fromIndex: Int, toIndex: Int): List<LocalFile>? {
    if (fromIndex !in queue.indices) return null
    val target = toIndex.coerceIn(0, queue.lastIndex)
    if (target == fromIndex) return queue
    val next = queue.toMutableList()
    val item = next.removeAt(fromIndex)
    next.add(target, item)
    return next
}

/**
 * Insert tracks into the visible queue.
 * [insertAfterId] null appends. Tracks already queued move to the insert point.
 * The anchor track stays where it is.
 */
fun placeInQueue(queue: List<LocalFile>, incoming: List<LocalFile>, insertAfterId: String?): List<LocalFile> {
    val audio = incoming
        .filter { it.mediaKind == MediaKind.AUDIO && it.id != insertAfterId }
        .distinctBy { it.id }
    if (audio.isEmpty()) return queue
    val incomingIds = audio.map { it.id }.toSet()
    val kept = queue.filter { it.id !in incomingIds }
    val at = if (insertAfterId == null) {
        kept.size
    } else {
        val anchor = kept.indexOfFirst { it.id == insertAfterId }
        if (anchor < 0) kept.size else anchor + 1
    }
    return kept.toMutableList().apply { addAll(at, audio) }
}

/** Playlist index to resume after an edit, following the track that was playing. */
fun queueIndexAfterEdit(queue: List<LocalFile>, currentId: String?): Int {
    if (queue.isEmpty()) return -1
    val index = currentId?.let { id -> queue.indexOfFirst { it.id == id } } ?: -1
    return if (index >= 0) index else 0
}

/**
 * Map playlist slots through a player window order (shuffle or linear).
 * Falls back to the playlist when the order is incomplete.
 */
fun materializePlayOrder(playlist: List<LocalFile>, windowOrder: List<Int>): List<LocalFile> {
    if (playlist.isEmpty() || windowOrder.isEmpty()) return playlist
    val ordered = windowOrder.mapNotNull { playlist.getOrNull(it) }
    return if (ordered.size == playlist.size) ordered else playlist
}

/** Rotate a shuffle window order so the playing track is first. */
fun rotateOrderToCurrent(windowOrder: List<Int>, currentWindow: Int): List<Int> {
    val at = windowOrder.indexOf(currentWindow)
    if (at <= 0) return windowOrder
    return windowOrder.subList(at, windowOrder.size) + windowOrder.subList(0, at)
}
