package com.jasensic.mydrive.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jasensic.mydrive.domain.LibrarySource
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.SharePermission
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.albumsInSilo
import com.jasensic.mydrive.presentation.HubTab
import com.jasensic.mydrive.presentation.Screen
import com.jasensic.mydrive.presentation.UiState
import com.jasensic.mydrive.presentation.silo
import com.jasensic.mydrive.presentation.theme.Motion
import com.jasensic.mydrive.presentation.theme.rememberHaptics

private enum class HubDialog { None, Actions, Rename, Move, CreateAlbum, RenameAlbum }

/** What the content area shows. A change of tab slides sideways, opening an album pushes. */
private data class HubPage(val tab: HubTab, val albumId: String?, val artistName: String?) {
    val isDetail: Boolean get() = albumId != null || artistName != null
}

private fun musicQueueAction(state: UiState, onTracks: (List<LocalFile>) -> Unit): (() -> Unit)? {
    if (state.tab != HubTab.MUSIC) return null
    val tracks = state.selectedFiles.filter { it.mediaKind == MediaKind.AUDIO }
    if (tracks.isEmpty()) return null
    return { onTracks(tracks) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveApp(
    state: UiState,
    themeMode: ThemeMode,
    playback: PlaybackState,
    onSignIn: (String, String, String) -> Unit,
    onSetup: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onTab: (HubTab) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onConnection: () -> Unit,
    onPlay: (List<LocalFile>, String) -> Unit,
    onPlayNext: (List<LocalFile>) -> Unit,
    onAddToQueue: (List<LocalFile>) -> Unit,
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
        when (state.screen) {
            Screen.NowPlaying -> onCloseNowPlaying()
            is Screen.Viewer -> onCloseViewer()
            else -> onBack()
        }
    }
    val viewerId = (state.screen as? Screen.Viewer)?.fileId
    // Keeps the last photo around so the viewer can finish its exit animation.
    val lastViewerId = remember { arrayOfNulls<String>(1) }
    if (viewerId != null) lastViewerId[0] = viewerId

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The hub stays composed under the viewer and Now Playing, so lists keep their scroll position.
        AnimatedContent(
            targetState = state.screen == Screen.Connect,
            transitionSpec = { fadeIn(tween(Motion.Medium)) togetherWith fadeOut(tween(Motion.Fast)) },
            label = "connectOrHub",
        ) { connecting ->
            if (connecting) {
                ConnectScreen(state, onSignIn, onSetup, onScanLan, onOpenLibrary)
            } else {
                HubScreen(
                    state = state,
                    themeMode = themeMode,
                    playback = playback,
                    onTab = onTab,
                    onSync = onSync,
                    onUpdate = onUpdate,
                    onTheme = onTheme,
                    onConnection = onConnection,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onOpenPhoto = onOpenPhoto,
                    onOpenFile = onOpenFile,
                    onOpenNowPlaying = onOpenNowPlaying,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
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
        }

        AnimatedVisibility(
            visible = viewerId != null,
            enter = fadeIn(tween(Motion.Medium)) + scaleIn(Motion.sheet(), initialScale = 0.92f),
            exit = fadeOut(tween(Motion.Medium)) + scaleOut(tween(Motion.Medium), targetScale = 0.96f),
        ) {
            val photos = if (state.albumId == null) {
                state.library.photos
            } else {
                state.library.photos.filter { it.albumId == state.albumId }
            }
            lastViewerId[0]?.let { id ->
                MediaViewerScreen(
                    files = photos,
                    currentId = id,
                    authToken = state.authToken,
                    onPage = onViewerPage,
                    onPauseAudio = onPauseAudio,
                    onClose = onCloseViewer,
                )
            }
        }

        AnimatedVisibility(
            visible = state.screen == Screen.NowPlaying,
            enter = slideInVertically(Motion.sheet()) { it },
            exit = slideOutVertically(tween(Motion.Slow)) { it },
        ) {
            NowPlayingScreen(
                playback = playback,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onPrev = onPrev,
                onNext = onNext,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                onRemoveQueued = onRemoveQueued,
                onMoveQueued = onMoveQueued,
                onPlayQueued = onPlayQueued,
                onClose = onCloseNowPlaying,
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    onPlayNext: (List<LocalFile>) -> Unit,
    onAddToQueue: (List<LocalFile>) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
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
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val selecting = state.selectedIds.isNotEmpty()
    val siloAlbums = albumsInSilo(state.albums, state.tab.silo())
    val currentAlbum = siloAlbums.find { it.id == state.albumId }
    val contentPad = PaddingValues(bottom = 12.dp)
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                HubTopBar(
                    state = state,
                    selecting = selecting,
                    currentAlbumName = currentAlbum?.name,
                    onLibrarySource = onLibrarySource,
                    onClearSelection = onClearSelection,
                    onSettings = { settingsOpen = true },
                    onNewAlbum = { dialog = HubDialog.CreateAlbum },
                    onRenameAlbum = { dialog = HubDialog.RenameAlbum },
                    onShareAlbum = onShareWithAccount,
                )
                ErrorBanner(state.error)
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = playback.isActive,
                    enter = slideInVertically(Motion.spring()) { it / 2 } + fadeIn(),
                    exit = slideOutVertically(tween(Motion.Medium)) { it / 2 } + fadeOut(),
                ) {
                    MiniPlayer(
                        playback = playback,
                        onPlayPause = onPlayPause,
                        onSkipNext = onNext,
                        onOpen = onOpenNowPlaying,
                    )
                }
                AnimatedContent(
                    targetState = selecting,
                    transitionSpec = {
                        (slideInVertically(Motion.spring()) { it / 2 } + fadeIn(tween(Motion.Fast))) togetherWith
                            (slideOutVertically(tween(Motion.Fast)) { it / 2 } + fadeOut(tween(Motion.Fast)))
                    },
                    label = "tabsOrSelection",
                ) { isSelecting ->
                    if (isSelecting) {
                        SelectionToolbar(
                            onShare = onShareSelected,
                            onShareWithAccount = onShareWithAccount,
                            onDelete = onTrashSelected,
                            onMore = {
                                haptics.lift()
                                dialog = HubDialog.Actions
                            },
                        )
                    } else {
                        TabBar(selected = state.tab, onSelect = onTab)
                    }
                }
            }
        },
    ) { padding ->
        val page = HubPage(state.tab, state.albumId, state.artistName)
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                val order = HubTab.entries
                val direction = when {
                    targetState.tab != initialState.tab ->
                        if (order.indexOf(targetState.tab) > order.indexOf(initialState.tab)) 1 else -1
                    targetState.isDetail && !initialState.isDetail -> 1
                    else -> -1
                }
                (slideInHorizontally(Motion.spring()) { direction * it / 5 } + fadeIn(tween(Motion.Medium))) togetherWith
                    (slideOutHorizontally(tween(Motion.Medium)) { -direction * it / 5 } + fadeOut(tween(Motion.Fast))) using
                    SizeTransform(clip = false)
            },
            label = "hubPage",
        ) { shown ->
            when (shown.tab) {
                HubTab.MUSIC -> MusicScreen(
                    tracks = state.library.music,
                    albums = siloAlbums,
                    albumId = shown.albumId,
                    artistName = shown.artistName,
                    selectedIds = state.selectedIds,
                    playingId = playback.current?.id,
                    isPlaying = playback.playing,
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
                    albumId = shown.albumId,
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
                HubTab.FILES, HubTab.SETTINGS -> FilesScreen(
                    files = state.library.documents,
                    albums = siloAlbums,
                    albumId = shown.albumId,
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
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { SheetGrabber(Modifier.padding(vertical = 10.dp)) },
        ) {
            SettingsPage(
                state = state,
                themeMode = themeMode,
                onTheme = onTheme,
                onSync = onSync,
                onUpdate = onUpdate,
                onConnection = {
                    settingsOpen = false
                    onConnection()
                },
                onSignOut = {
                    settingsOpen = false
                    onSignOut()
                },
            )
        }
    }
    when (dialog) {
        HubDialog.None -> Unit
        HubDialog.Actions -> LibraryActionSheet(
            count = state.selectedIds.size,
            canRename = state.selectedIds.size == 1,
            onPlayNext = musicQueueAction(state) { tracks ->
                dialog = HubDialog.None
                onPlayNext(tracks)
                onClearSelection()
            },
            onAddToQueue = musicQueueAction(state) { tracks ->
                dialog = HubDialog.None
                onAddToQueue(tracks)
                onClearSelection()
            },
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

/** One thin bar: where the library comes from on the left, status and settings on the right. */
@Composable
private fun HubTopBar(
    state: UiState,
    selecting: Boolean,
    currentAlbumName: String?,
    onLibrarySource: (LibrarySource) -> Unit,
    onClearSelection: () -> Unit,
    onSettings: () -> Unit,
    onNewAlbum: () -> Unit,
    onRenameAlbum: () -> Unit,
    onShareAlbum: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = selecting,
        transitionSpec = { fadeIn(tween(Motion.Fast)) togetherWith fadeOut(tween(Motion.Fast)) },
        label = "topBar",
    ) { isSelecting ->
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                TextButton(onClick = onClearSelection) { Text("Cancel", style = MaterialTheme.typography.bodyLarge) }
                Text(
                    if (state.selectedIds.size == 1) "1 selected" else "${state.selectedIds.size} selected",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(Modifier.width(72.dp))
            } else {
                SegmentedControl(
                    options = listOf("Device", "Server"),
                    selectedIndex = if (state.librarySource == LibrarySource.DEVICE) 0 else 1,
                    onSelect = { index -> onLibrarySource(if (index == 0) LibrarySource.DEVICE else LibrarySource.SERVER) },
                    enabled = listOf(true, state.lanAvailable),
                    modifier = Modifier.width(156.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    SyncPill(state)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        DropdownMenuItem(text = { Text("New album") }, onClick = { menuOpen = false; onNewAlbum() })
                        if (currentAlbumName != null) {
                            DropdownMenuItem(text = { Text("Rename “$currentAlbumName”") }, onClick = { menuOpen = false; onRenameAlbum() })
                            DropdownMenuItem(text = { Text("Share album") }, onClick = { menuOpen = false; onShareAlbum() })
                        }
                    }
                }
                IconButton(onClick = onSettings) {
                    BadgedBox(badge = { if (state.availableUpdate != null) Badge() }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Replaces the tab bar while items are selected, like the Photos app. */
@Composable
private fun SelectionToolbar(
    onShare: () -> Unit,
    onShareWithAccount: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.background.copy(alpha = 0.94f))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.outlineVariant))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolbarAction(Icons.Outlined.Share, "Share", onShare)
            ToolbarAction(Icons.Outlined.PersonAdd, "Share with account", onShareWithAccount)
            ToolbarAction(Icons.Outlined.Delete, "Remove from this device", onDelete, destructive = true)
            ToolbarAction(Icons.Outlined.MoreHoriz, "Manage", onMore)
        }
    }
}

@Composable
private fun ToolbarAction(icon: ImageVector, description: String, onClick: () -> Unit, destructive: Boolean = false) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
