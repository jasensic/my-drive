package com.jasensic.mydrive.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var settings by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(HubDialog.None) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val selecting = state.selectedIds.isNotEmpty()
    val siloAlbums = albumsInSilo(state.albums, state.tab.silo())
    val currentAlbum = siloAlbums.find { it.id == state.albumId }
    val title = when {
        selecting -> "${state.selectedIds.size} selected"
        currentAlbum != null -> currentAlbum.name
        state.artistName != null -> state.artistName
        state.tab == HubTab.MUSIC -> "Music"
        state.tab == HubTab.PHOTOS -> "Photos"
        else -> "Files"
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (!selecting) {
                                Text(
                                    when (state.librarySource) {
                                        LibrarySource.DEVICE -> "${state.files.size} on this device · ${state.serverLabel}"
                                        LibrarySource.SERVER -> "${state.files.size} on server · ${state.serverLabel}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (selecting) {
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                            }
                        } else {
                            IconButton(onClick = { onBack() }, enabled = state.canGoBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (selecting) {
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
                        } else {
                            IconButton(onClick = { onForward() }, enabled = state.canGoForward) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                            }
                            if (currentAlbum != null) {
                                IconButton(onClick = { dialog = HubDialog.RenameAlbum }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename album")
                                }
                                IconButton(onClick = onShareWithAccount) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = "Share album")
                                }
                            }
                            IconButton(onClick = { dialog = HubDialog.CreateAlbum }) {
                                Icon(Icons.Outlined.Add, contentDescription = "New album")
                            }
                            IconButton(onClick = onSync, enabled = !state.isBusy) {
                                Icon(Icons.Outlined.Sync, contentDescription = "Sync")
                            }
                            IconButton(onClick = { settings = true }) {
                                BadgedBox(badge = { if (state.availableUpdate != null) Badge() }) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                }
                            }
                        }
                    },
                    scrollBehavior = scroll,
                )
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                SyncStatusBar(state, modifier = Modifier.fillMaxWidth())
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        },
        bottomBar = {
            Column {
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
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
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
                        contentPadding = padding,
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
                        contentPadding = padding,
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
                        contentPadding = padding,
                    )
                }
            }
        }
    }
    if (settings) {
        SettingsSheet(
            state = state,
            themeMode = themeMode,
            onDismiss = { settings = false },
            onTheme = onTheme,
            onSync = onSync,
            onUpdate = onUpdate,
            onConnection = {
                settings = false
                onConnection()
            },
            onSignOut = {
                settings = false
                onSignOut()
            },
        )
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
