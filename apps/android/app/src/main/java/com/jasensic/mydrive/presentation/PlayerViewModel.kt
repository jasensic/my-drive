package com.jasensic.mydrive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasensic.mydrive.domain.AudioPlayer
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: AudioPlayer,
) : ViewModel() {
    val playback = player.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PlaybackState(),
    )

    fun play(files: List<LocalFile>, startId: String) = player.playQueue(files, startId)

    fun playPause() = player.playPause()

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun skipNext() = player.skipToNext()

    fun skipPrevious() = player.skipToPrevious()

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun removeFromQueue(fileId: String) = player.removeFromQueue(fileId)

    fun moveQueueItem(fromIndex: Int, toIndex: Int) = player.moveQueueItem(fromIndex, toIndex)

    fun playQueueItem(fileId: String) = player.playQueueItem(fileId)
}
