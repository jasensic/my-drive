package com.jasensic.mydrive.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.jasensic.mydrive.presentation.theme.MyDriveTheme
import com.jasensic.mydrive.presentation.ui.DriveApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: DriveViewModel by viewModels()
    private val player: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.ui.collectAsState()
            val themeMode by vm.themeMode.collectAsState()
            val playback by player.playback.collectAsState()
            MyDriveTheme(themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    DriveApp(
                        state = state,
                        themeMode = themeMode,
                        playback = playback,
                        onSignIn = vm::sync,
                        onScanLan = vm::scanLan,
                        onOpenLibrary = vm::openLibrary,
                        onTab = vm::selectTab,
                        onSync = vm::syncExisting,
                        onUpdate = vm::installUpdate,
                        onTheme = vm::setThemeMode,
                        onConnection = vm::showConnect,
                        onPlay = player::play,
                        onOpenPhoto = vm::openViewer,
                        onOpenFile = vm::openDocument,
                        onCloseViewer = vm::closeViewer,
                        onViewerPage = vm::openViewer,
                        onOpenNowPlaying = vm::openNowPlaying,
                        onCloseNowPlaying = vm::closeNowPlaying,
                        onPlayPause = player::playPause,
                        onSeek = player::seekTo,
                        onPrev = player::skipPrevious,
                        onNext = player::skipNext,
                        onShuffle = player::toggleShuffle,
                        onRepeat = player::cycleRepeat,
                        onPauseAudio = player::pause,
                        onBack = vm::goBack,
                        onForward = vm::goForward,
                        onOpenAlbum = vm::openAlbum,
                        onOpenArtist = vm::openArtist,
                        onToggleSelect = vm::toggleSelect,
                        onSelectOnly = vm::selectOnly,
                        onClearSelection = vm::clearSelection,
                        onCreateAlbum = vm::createAlbum,
                        onRenameAlbum = vm::renameAlbum,
                        onDeleteAlbum = vm::deleteAlbum,
                        onRenameSelected = vm::renameSelected,
                        onMoveSelected = vm::moveSelected,
                        onShareSelected = vm::shareSelected,
                        onTrashSelected = vm::trashSelected,
                    )
                }
            }
        }
    }
}
