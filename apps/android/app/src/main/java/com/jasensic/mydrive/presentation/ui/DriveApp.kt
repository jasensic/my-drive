package com.jasensic.mydrive.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.jasensic.mydrive.domain.PlaybackState
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.presentation.HubTab
import com.jasensic.mydrive.presentation.Screen
import com.jasensic.mydrive.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveApp(
    state: UiState,
    themeMode: ThemeMode,
    playback: PlaybackState,
    onSignIn: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onTab: (HubTab) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onConnection: () -> Unit,
    onPlay: (List<com.jasensic.mydrive.domain.LocalFile>, String) -> Unit,
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
) {
    when (val screen = state.screen) {
        Screen.Connect -> ConnectScreen(state, onSignIn, onScanLan, onOpenLibrary)
        Screen.NowPlaying -> {
            BackHandler(onBack = onCloseNowPlaying)
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
            BackHandler(onBack = onCloseViewer)
            MediaViewerScreen(
                files = state.library.photos,
                currentId = screen.fileId,
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
    onPlay: (List<com.jasensic.mydrive.domain.LocalFile>, String) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val title = when (state.tab) {
        HubTab.MUSIC -> "Music"
        HubTab.PHOTOS -> "Photos"
        HubTab.FILES -> "Files"
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title)
                            Text(
                                "${state.files.size} on device · ${state.serverLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSync, enabled = state.progress == null) {
                            Icon(Icons.Outlined.Sync, contentDescription = "Sync")
                        }
                        IconButton(onClick = { settings = true }) {
                            BadgedBox(badge = { if (state.availableUpdate != null) Badge() }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    scrollBehavior = scroll,
                )
                if (state.progress != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
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
                    HubTab.MUSIC -> MusicScreen(state.library.music, onPlay, padding)
                    HubTab.PHOTOS -> PhotosScreen(state.library.photos, onOpenPhoto, padding)
                    HubTab.FILES -> FilesScreen(state.library.documents, onOpenFile, padding)
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
        )
    }
}
