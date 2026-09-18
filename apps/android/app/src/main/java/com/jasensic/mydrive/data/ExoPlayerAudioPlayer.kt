package com.jasensic.mydrive.data

import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jasensic.mydrive.domain.AudioPlayer
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Singleton
class ExoPlayerAudioPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : AudioPlayer {
    private val exo = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    private var queue: List<LocalFile> = emptyList()
    private var ticker: Job? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                emitState()
            }
        })
        ticker = scope.launch {
            while (isActive) {
                emitState()
                delay(250)
            }
        }
    }

    override fun observe(): Flow<PlaybackState> = _state.asStateFlow()

    override fun playQueue(files: List<LocalFile>, startId: String) {
        val tracks = files.filter { it.mediaKind == MediaKind.AUDIO && File(it.path).isFile }
        if (tracks.isEmpty()) return
        queue = tracks
        val start = tracks.indexOfFirst { it.id == startId }.let { if (it < 0) 0 else it }
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.fromFile(File(track.path)))
                .build()
        }
        exo.shuffleModeEnabled = _state.value.shuffle
        exo.repeatMode = exoRepeat(_state.value.repeat)
        exo.setMediaItems(items, start, 0L)
        exo.prepare()
        exo.play()
        emitState()
    }

    override fun playPause() {
        if (queue.isEmpty()) return
        if (exo.isPlaying) exo.pause() else exo.play()
        emitState()
    }

    override fun pause() {
        if (exo.isPlaying) exo.pause()
        emitState()
    }

    override fun seekTo(positionMs: Long) {
        exo.seekTo(positionMs.coerceAtLeast(0L))
        emitState()
    }

    override fun skipToNext() {
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.seekTo(0)
        emitState()
    }

    override fun skipToPrevious() {
        if (exo.currentPosition > 3_000L || !exo.hasPreviousMediaItem()) {
            exo.seekTo(0)
        } else {
            exo.seekToPreviousMediaItem()
        }
        emitState()
    }

    override fun toggleShuffle() {
        val enabled = !exo.shuffleModeEnabled
        exo.shuffleModeEnabled = enabled
        emitState()
    }

    override fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exo.repeatMode = exoRepeat(next)
        emitState()
    }

    override fun stop() {
        exo.stop()
        exo.clearMediaItems()
        queue = emptyList()
        emitState()
    }

    private fun emitState() {
        val index = exo.currentMediaItemIndex
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = if (queue.isEmpty() || index !in queue.indices) -1 else index,
            playing = exo.isPlaying,
            positionMs = exo.currentPosition.coerceAtLeast(0L),
            durationMs = exo.duration.takeIf { it > 0 } ?: queue.getOrNull(index)?.durationMs ?: 0L,
            shuffle = exo.shuffleModeEnabled,
            repeat = when (exo.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
        )
    }

    private fun exoRepeat(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }
}
