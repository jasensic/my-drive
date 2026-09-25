package com.jasensic.mydrive.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.LibrarySource
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.SharePermission
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.albumsInSilo
import com.jasensic.mydrive.presentation.HubTab
import com.jasensic.mydrive.presentation.Screen
import com.jasensic.mydrive.presentation.UiState
import com.jasensic.mydrive.presentation.silo

private enum class HubDialog { None, Actions, Rename, Move, CreateAlbum, RenameAlbum }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveApp(
    state: UiState,
    themeMode: ThemeMode,
    playback: PlaybackState,
    onSignIn: (String, String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onSetup: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onTab: (HubTab) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onConnection: () -> Unit,
    onPlay: (List<LocalFile>, String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCloseViewer: () -> Unit,
    onViewerPage: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onRemoveQueued: (String) -> Unit,
    onMoveQueued: (Int, Int) -> Unit,
    onPlayQueued: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onBack: () -> Boolean,
    onForward: () -> Boolean,
    onOpenAlbum: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectOnly: (String) -> Unit,
    onClearSelection: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameSelected: (String) -> Unit,
    onMoveSelected: (String?) -> Unit,
    onShareSelected: () -> Unit,
    onShareWithAccount: () -> Unit,
    onConfirmShare: (String, SharePermission) -> Unit,
    onRevokeShare: (String) -> Unit,
    onCloseShareSheet: () -> Unit,
    onTrashSelected: () -> Unit,
    onLibrarySource: (LibrarySource) -> Unit,
    onSignOut: () -> Unit,
) {
    BackHandler(enabled = state.canGoBack || state.selectedIds.isNotEmpty() || state.screen is Screen.Viewer || state.screen is Screen.NowPlaying) {
        onBack()
    }
    when (val screen = state.screen) {
        Screen.Connect -> ConnectScreen(state, onSignIn, onRegister, onSetup, onScanLan, onOpenLibrary)
        Screen.NowPlaying -> {
            NowPlayingScreen(
                playback = playback,
                onBack = onCloseNowPlaying,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onPrev = onPrev,
                onNext = onNext,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                onRemoveQueued = onRemoveQueued,
                onMoveQueued = onMoveQueued,
                onPlayQueued = onPlayQueued,
            )
        }
        is Screen.Viewer -> {
            val photos = if (state.albumId == null) {
                state.library.photos
            } else {
                state.library.photos.filter { it.albumId == state.albumId }
            }
            MediaViewerScreen(
                files = photos,
                currentId = screen.fileId,
                authToken = state.authToken,
                onBack = onCloseViewer,
                onPage = onViewerPage,
                onPauseAudio = onPauseAudio,
            )
        }
        Screen.Hub -> HubScreen(
            state = state,
            themeMode = themeMode,
            playback = playback,
            onTab = onTab,
            onSync = onSync,
            onUpdate = onUpdate,
            onTheme = onTheme,
            onConnection = onConnection,
            onPlay = onPlay,
            onOpenPhoto = onOpenPhoto,
            onOpenFile = onOpenFile,
            onOpenNowPlaying = onOpenNowPlaying,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onBack = onBack,
            onForward = onForward,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onToggleSelect = onToggleSelect,
            onSelectOnly = onSelectOnly,
            onClearSelection = onClearSelection,
            onCreateAlbum = onCreateAlbum,
            onRenameAlbum = onRenameAlbum,
            onDeleteAlbum = onDeleteAlbum,
            onRenameSelected = onRenameSelected,
            onMoveSelected = onMoveSelected,
            onShareSelected = onShareSelected,
            onShareWithAccount = onShareWithAccount,
            onTrashSelected = onTrashSelected,
            onLibrarySource = onLibrarySource,
            onSignOut = onSignOut,
        )
    }
    if (state.shareSheet.visible) {
        ShareWithUserDialog(
            title = state.shareSheet.title,
            users = state.shareSheet.users,
            grants = state.shareSheet.grants,
            canManage = state.shareSheet.canManage,
            onShare = onConfirmShare,
            onRevoke = onRevokeShare,
            onDismiss = onCloseShareSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(
    state: UiState,
    themeMode: ThemeMode,
    playback: PlaybackState,
    onTab: (HubTab) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onConnection: () -> Unit,
    onPlay: (List<LocalFile>, String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Boolean,
    onForward: () -> Boolean,
    onOpenAlbum: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectOnly: (String) -> Unit,
    onClearSelection: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (String, String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onRenameSelected: (String) -> Unit,
    onMoveSelected: (String?) -> Unit,
    onShareSelected: () -> Unit,
    onShareWithAccount: () -> Unit,
    onTrashSelected: () -> Unit,
    onLibrarySource: (LibrarySource) -> Unit,
    onSignOut: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(HubDialog.None) }
    val selecting = state.selectedIds.isNotEmpty()
    val libraryTab = state.tab != HubTab.SETTINGS
    val siloAlbums = albumsInSilo(state.albums, state.tab.silo())
    val currentAlbum = siloAlbums.find { it.id == state.albumId }
    val contentPad = PaddingValues(bottom = 12.dp)
    Scaffold(
        bottomBar = {
            Column {
                if (selecting && libraryTab) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onClearSelection) { Text("Clear") }
                        Text(
                            "${state.selectedIds.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = onShareSelected) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = onShareWithAccount) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = "Share with account")
                        }
                        IconButton(onClick = onTrashSelected) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove from this device")
                        }
                        IconButton(onClick = { dialog = HubDialog.Actions }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Manage")
                        }
                    }
                }
                if (playback.isActive) {
                    MiniPlayer(
                        playback = playback,
                        onPlayPause = onPlayPause,
                        onSkipNext = onNext,
                        onOpen = onOpenNowPlaying,
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = state.tab == HubTab.MUSIC,
                        onClick = { onTab(HubTab.MUSIC) },
                        icon = { Icon(Icons.Outlined.LibraryMusic, contentDescription = null) },
                        label = { Text("Music") },
                    )
                    NavigationBarItem(
                        selected = state.tab == HubTab.PHOTOS,
                        onClick = { onTab(HubTab.PHOTOS) },
                        icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                        label = { Text("Photos") },
                    )
                    NavigationBarItem(
                        selected = state.tab == HubTab.FILES,
                        onClick = { onTab(HubTab.FILES) },
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        label = { Text("Files") },
                    )
                    NavigationBarItem(
                        selected = state.tab == HubTab.SETTINGS,
                        onClick = { onTab(HubTab.SETTINGS) },
                        icon = {
                            BadgedBox(badge = { if (state.availableUpdate != null) Badge() }) {
                                Icon(Icons.Outlined.Settings, contentDescription = null)
                            }
                        },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (libraryTab) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.librarySource == LibrarySource.DEVICE,
                        onClick = { onLibrarySource(LibrarySource.DEVICE) },
                        label = { Text("On this device") },
                    )
                    FilterChip(
                        selected = state.librarySource == LibrarySource.SERVER,
                        onClick = { onLibrarySource(LibrarySource.SERVER) },
                        enabled = state.lanAvailable,
                        label = { Text("On server") },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (state.librarySource) {
                            LibrarySource.DEVICE -> "${state.files.size} on this device · ${state.serverLabel}"
                            LibrarySource.SERVER -> "${state.files.size} on server · ${state.serverLabel}"
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.canGoBack) {
                        TextButton(onClick = { onBack() }) { Text("Back") }
                    }
                    if (state.canGoForward) {
                        TextButton(onClick = { onForward() }) { Text("Forward") }
                    }
                    if (currentAlbum != null) {
                        TextButton(onClick = { dialog = HubDialog.RenameAlbum }) { Text("Rename") }
                        TextButton(onClick = onShareWithAccount) { Text("Share") }
                    }
                    TextButton(onClick = { dialog = HubDialog.CreateAlbum }) { Text("New album") }
                }
                SyncStatusBar(state, modifier = Modifier.fillMaxWidth())
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (state.tab) {
                    HubTab.MUSIC -> MusicScreen(
                        tracks = state.library.music,
                        albums = siloAlbums,
                        albumId = state.albumId,
                        artistName = state.artistName,
                        selectedIds = state.selectedIds,
                        onPlay = onPlay,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onToggleSelect = onToggleSelect,
                        onLongPress = { id ->
                            onSelectOnly(id)
                            dialog = HubDialog.Actions
                        },
                        contentPadding = contentPad,
                    )
                    HubTab.PHOTOS -> PhotosScreen(
                        files = state.library.photos,
                        albums = siloAlbums,
                        albumId = state.albumId,
                        selectedIds = state.selectedIds,
                        onOpen = onOpenPhoto,
                        onOpenAlbum = onOpenAlbum,
                        onToggleSelect = onToggleSelect,
                        onLongPress = { id ->
                            onSelectOnly(id)
                            dialog = HubDialog.Actions
                        },
                        contentPadding = contentPad,
                    )
                    HubTab.FILES -> FilesScreen(
                        files = state.library.documents,
                        albums = siloAlbums,
                        albumId = state.albumId,
                        selectedIds = state.selectedIds,
                        onOpen = onOpenFile,
                        onOpenAlbum = onOpenAlbum,
                        onToggleSelect = onToggleSelect,
                        onLongPress = { id ->
                            onSelectOnly(id)
                            dialog = HubDialog.Actions
                        },
                        contentPadding = contentPad,
                    )
                    HubTab.SETTINGS -> SettingsPage(
                        state = state,
                        themeMode = themeMode,
                        onTheme = onTheme,
                        onSync = onSync,
                        onUpdate = onUpdate,
                        onConnection = onConnection,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }
    when (dialog) {
        HubDialog.None -> Unit
        HubDialog.Actions -> LibraryActionSheet(
            count = state.selectedIds.size,
            canRename = state.selectedIds.size == 1,
            onRename = { dialog = HubDialog.Rename },
            onMove = { dialog = HubDialog.Move },
            onShare = {
                dialog = HubDialog.None
                onShareSelected()
            },
            onShareWithAccount = {
                dialog = HubDialog.None
                onShareWithAccount()
            },
            onDelete = {
                dialog = HubDialog.None
                onTrashSelected()
            },
            onDismiss = { dialog = HubDialog.None },
        )
        HubDialog.Rename -> TextPromptDialog(
            title = "Rename",
            initial = state.selectedFiles.firstOrNull()?.name.orEmpty(),
            onConfirm = {
                onRenameSelected(it)
                dialog = HubDialog.None
            },
            onDismiss = { dialog = HubDialog.None },
        )
        HubDialog.Move -> MoveAlbumDialog(
            albums = siloAlbums,
            onMove = {
                onMoveSelected(it)
                dialog = HubDialog.None
            },
            onCreate = {
                onCreateAlbum(it)
                dialog = HubDialog.None
            },
            onDismiss = { dialog = HubDialog.None },
        )
        HubDialog.CreateAlbum -> TextPromptDialog(
            title = "New album",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                onCreateAlbum(it)
                dialog = HubDialog.None
            },
            onDismiss = { dialog = HubDialog.None },
        )
        HubDialog.RenameAlbum -> TextPromptDialog(
            title = "Rename album",
            initial = currentAlbum?.name.orEmpty(),
            onConfirm = { name ->
                currentAlbum?.id?.let { onRenameAlbum(it, name) }
                dialog = HubDialog.None
            },
            onDismiss = { dialog = HubDialog.None },
        )
    }
}
