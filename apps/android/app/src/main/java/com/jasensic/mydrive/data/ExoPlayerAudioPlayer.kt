package com.jasensic.mydrive.data

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jasensic.mydrive.domain.AudioPlayer
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.RepeatMode
import com.jasensic.mydrive.domain.songTitle
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

@Singleton
class ExoPlayerAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    private val pending = ArrayDeque<() -> Unit>()
    private var queue: List<LocalFile> = emptyList()
    private var controller: MediaController? = null
    private var connecting = false
    private var pendingSeekMs: Long? = null
    private var ticker: Job? = null

    init {
        ticker = scope.launch {
            while (isActive) {
                if (controller?.playWhenReady == true) emitState()
                delay(250)
            }
        }
    }

    override fun observe(): Flow<PlaybackState> = _state.asStateFlow()

    override fun playQueue(files: List<LocalFile>, startId: String) {
        runWhenReady {
            val tracks = files.filter { it.mediaKind == MediaKind.AUDIO && File(it.path).isFile }
            if (tracks.isEmpty()) return@runWhenReady
            val player = controller ?: return@runWhenReady
            queue = tracks
            val start = tracks.indexOfFirst { it.id == startId }.let { if (it < 0) 0 else it }
            player.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
            player.prepare()
            player.play()
            emitState()
        }
    }

    override fun playPause() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            if (queue.isEmpty()) return@runWhenReady
            if (player.isPlaying) player.pause() else player.play()
            emitState()
        }
    }

    override fun pause() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            if (player.isPlaying) player.pause()
            emitState()
        }
    }

    override fun seekTo(positionMs: Long) {
        runWhenReady {
            val target = positionMs.coerceAtLeast(0L)
            pendingSeekMs = target
            controller?.seekTo(target)
            _state.value = _state.value.copy(positionMs = target)
        }
    }

    override fun skipToNext() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0)
            emitState()
        }
    }

    override fun skipToPrevious() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            if (player.currentPosition > 3_000L || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
            } else {
                player.seekToPreviousMediaItem()
            }
            emitState()
        }
    }

    override fun toggleShuffle() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            player.shuffleModeEnabled = !player.shuffleModeEnabled
            emitState()
        }
    }

    override fun cycleRepeat() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            val next = when (_state.value.repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            player.repeatMode = exoRepeat(next)
            emitState()
        }
    }

    override fun stop() {
        runWhenReady {
            val player = controller ?: return@runWhenReady
            player.stop()
            player.clearMediaItems()
            queue = emptyList()
            emitState()
        }
    }

    private fun runWhenReady(block: () -> Unit) {
        scope.launch {
            if (controller != null) {
                block()
            } else {
                pending.addLast(block)
                connectIfNeeded()
            }
        }
    }

    private fun connectIfNeeded() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrElse { error ->
                    Log.e(TAG, "Media session connection failed", error)
                    connecting = false
                    return@addListener
                }
                controller = connected
                connecting = false
                connected.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        emitState()
                    }
                })
                val queued = pending.toList()
                pending.clear()
                queued.forEach { it() }
                emitState()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun emitState() {
        val player = controller ?: return
        try {
            val mediaId = player.currentMediaItem?.mediaId
            val resolved = mediaId?.let { id -> queue.indexOfFirst { it.id == id } } ?: -1
            val index = when {
                queue.isEmpty() -> -1
                resolved >= 0 -> resolved
                player.currentMediaItemIndex in queue.indices -> player.currentMediaItemIndex
                else -> -1
            }
            val reported = player.currentPosition.coerceAtLeast(0L)
            val pending = pendingSeekMs
            val position = if (pending != null && kotlin.math.abs(reported - pending) > 750) {
                pending
            } else {
                pendingSeekMs = null
                reported
            }
            _state.value = PlaybackState(
                queue = queue,
                currentIndex = index,
                playing = player.isPlaying,
                positionMs = position,
                durationMs = player.duration.takeIf { it > 0 } ?: queue.getOrNull(index)?.durationMs ?: 0L,
                shuffle = player.shuffleModeEnabled,
                repeat = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
            )
        } catch (_: IllegalStateException) {
            controller = null
        }
    }

    private fun exoRepeat(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private companion object {
        const val TAG = "ExoPlayerAudioPlayer"
    }
}

private fun LocalFile.toMediaItem(): MediaItem {
    val art = artworkPath?.let(::File)?.takeIf { it.isFile }
    val metadata = MediaMetadata.Builder()
        .setTitle(songTitle())
        .setArtist(displayArtist)
        .setAlbumTitle(displayAlbum)
        .setArtworkUri(art?.let { Uri.fromFile(it) })
        .setIsPlayable(true)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(metadata)
        .build()
}
