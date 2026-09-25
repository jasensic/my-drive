package com.jasensic.mydrive.domain

/** Drop one queued track. Returns null when the id is not in the queue. */
fun editQueueRemove(queue: List<LocalFile>, fileId: String): List<LocalFile>? {
    if (queue.none { it.id == fileId }) return null
    return queue.filterNot { it.id == fileId }
}

/**
 * Move a queued track. Indices are playlist positions.
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

/** Playlist index to resume after an edit, following the track that was playing. */
fun queueIndexAfterEdit(queue: List<LocalFile>, currentId: String?): Int {
    if (queue.isEmpty()) return -1
    val index = currentId?.let { id -> queue.indexOfFirst { it.id == id } } ?: -1
    return if (index >= 0) index else 0
}
