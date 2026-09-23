package com.jasensic.mydrive.data

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.jasensic.mydrive.R
import com.jasensic.mydrive.domain.ListLocalLibraryUseCase
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.LocalLibrary
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.songTitle
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var listLibrary: ListLocalLibraryUseCase
    @Inject lateinit var syncProgress: SyncProgressStore
    @Inject lateinit var syncState: SyncStateRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var librarySession: MediaLibraryService.MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)

        val httpFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val headers = syncState.cachedToken()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                return DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                    .createDataSource()
            }
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, httpFactory)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                0,
                launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        librarySession = MediaLibraryService.MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId(SESSION_ID)
            .apply { if (sessionActivity != null) setSessionActivity(sessionActivity) }
            .build()

        scope.launch {
            syncProgress.observe().collect { progress ->
                if (progress.phase == SyncPhase.COMPLETED) {
                    notifyTreeChanged()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? =
        librarySession

    override fun onDestroy() {
        scope.cancel()
        librarySession?.run {
            player.release()
            release()
        }
        librarySession = null
        super.onDestroy()
    }

    private fun notifyTreeChanged() {
        val session = librarySession ?: return
        listOf(ID_ROOT, ID_ARTISTS, ID_ALBUMS, ID_SONGS).forEach { parentId ->
            session.notifyChildrenChanged(parentId, Int.MAX_VALUE, null)
        }
    }

    private suspend fun downloadedTracks(): List<LocalFile> =
        runCatching { listLibrary.execute() }
            .getOrDefault(LocalLibrary(emptyList(), emptyList()))
            .files
            .filter { it.mediaKind == MediaKind.AUDIO && File(it.path).isFile }
            .sortedBy { it.songTitle().lowercase() }

    private fun artistId(name: String) = "$ID_ARTIST_PREFIX$name"

    private fun albumIdOf(file: LocalFile): String {
        val id = file.albumId?.takeIf { it.isNotBlank() }
        return if (id != null) "$ID_ALBUM_ID_PREFIX$id" else "$ID_ALBUM_NAME_PREFIX${file.displayAlbum}"
    }

    private fun page(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (pageSize <= 0 || page < 0) return ImmutableList.copyOf(items)
        val from = page * pageSize
        if (from >= items.size) return ImmutableList.of()
        return ImmutableList.copyOf(items.subList(from, minOf(from + pageSize, items.size)))
    }

    private fun childrenOf(parentId: String, tracks: List<LocalFile>): List<MediaItem> =
        when (parentId) {
            ID_ROOT -> listOf(
                browsableMediaItem(ID_ARTISTS, getString(R.string.auto_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                browsableMediaItem(ID_ALBUMS, getString(R.string.auto_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsableMediaItem(ID_SONGS, getString(R.string.auto_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            )
            ID_ARTISTS -> tracks
                .groupBy { it.displayArtist }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (name, songs) ->
                    browsableMediaItem(
                        artistId(name),
                        name,
                        MediaMetadata.MEDIA_TYPE_ARTIST,
                        songs.firstNotNullOfOrNull { it.artworkPath },
                        this,
                    )
                }
            ID_ALBUMS -> tracks
                .groupBy { albumIdOf(it) }
                .entries
                .sortedBy { it.value.first().displayAlbum.lowercase() }
                .map { (id, songs) ->
                    browsableMediaItem(
                        id,
                        songs.first().displayAlbum,
                        MediaMetadata.MEDIA_TYPE_ALBUM,
                        songs.firstNotNullOfOrNull { it.artworkPath },
                        this,
                    )
                }
            ID_SONGS -> tracks.map { it.toPlayableMediaItem(this) }
            else -> when {
                parentId.startsWith(ID_ARTIST_PREFIX) -> {
                    val name = parentId.removePrefix(ID_ARTIST_PREFIX)
                    tracks.filter { it.displayArtist == name }.map { it.toPlayableMediaItem(this) }
                }
                parentId.startsWith(ID_ALBUM_ID_PREFIX) || parentId.startsWith(ID_ALBUM_NAME_PREFIX) ->
                    tracks.filter { albumIdOf(it) == parentId }.map { it.toPlayableMediaItem(this) }
                else -> emptyList()
            }
        }

    private fun itemOf(mediaId: String, tracks: List<LocalFile>): MediaItem? {
        if (mediaId == ID_ROOT) {
            return browsableMediaItem(ID_ROOT, getString(R.string.auto_root), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        }
        childrenOf(ID_ROOT, tracks).find { it.mediaId == mediaId }?.let { return it }
        childrenOf(ID_ARTISTS, tracks).find { it.mediaId == mediaId }?.let { return it }
        childrenOf(ID_ALBUMS, tracks).find { it.mediaId == mediaId }?.let { return it }
        return tracks.find { it.id == mediaId }?.toPlayableMediaItem(this)
    }

    private fun expandToPlayable(mediaId: String, tracks: List<LocalFile>): List<MediaItem> {
        tracks.find { it.id == mediaId }?.let { return listOf(it.toPlayableMediaItem(this)) }
        val children = childrenOf(mediaId, tracks)
        if (children.isEmpty()) return emptyList()
        if (children.all { it.mediaMetadata.isPlayable == true }) return children
        return children.flatMap { expandToPlayable(it.mediaId, tracks) }
    }

    private inner class LibraryCallback : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = browsableMediaItem(
                ID_ROOT,
                getString(R.string.auto_root),
                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            )
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = defer {
            val items = page(childrenOf(parentId, downloadedTracks()), page, pageSize)
            items.forEach { it.grantArtworkAndContent(this@PlaybackService, browser.packageName) }
            LibraryResult.ofItemList(items, params)
        }

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = defer {
            val item = itemOf(mediaId, downloadedTracks())
            if (item == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                item.grantArtworkAndContent(this@PlaybackService, browser.packageName)
                LibraryResult.ofItem(item, null)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = defer {
            val tracks = downloadedTracks()
            val resolved = mediaItems.flatMap { item ->
                val playable = expandToPlayable(item.mediaId, tracks)
                if (playable.isEmpty() && item.localConfiguration?.uri != null) listOf(item) else playable
            }.onEach { it.grantArtworkAndContent(this@PlaybackService, controller.packageName) }
            resolved
        }
    }

    private fun <T> defer(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (error: Throwable) {
                future.setException(error)
            }
        }
        return future
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val SESSION_ID = "mydrive-playback"
        const val ID_ROOT = "root"
        const val ID_ARTISTS = "artists"
        const val ID_ALBUMS = "albums"
        const val ID_SONGS = "songs"
        const val ID_ARTIST_PREFIX = "artist:"
        const val ID_ALBUM_ID_PREFIX = "album:id:"
        const val ID_ALBUM_NAME_PREFIX = "album:name:"
    }
}
