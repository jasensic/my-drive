package com.jasensic.mydrive.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import com.jasensic.mydrive.presentation.theme.MyDriveTheme
import com.jasensic.mydrive.presentation.ui.DriveApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: DriveViewModel by viewModels()
    private val player: PlayerViewModel by viewModels()
    private var askedForPermissions = false
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.ui.collectAsState()
            val themeMode by vm.themeMode.collectAsState()
            val playback by player.playback.collectAsState()
            LaunchedEffect(Unit) {
                if (askedForPermissions) return@LaunchedEffect
                askedForPermissions = true
                val needed = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.NEARBY_WIFI_DEVICES) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
            }
            MyDriveTheme(themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    DriveApp(
                        state = state,
                        themeMode = themeMode,
                        playback = playback,
                        onSignIn = vm::sync,
                        onRegister = vm::register,
                        onSetup = vm::setupAdmin,
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
                        onRemoveQueued = player::removeFromQueue,
                        onMoveQueued = player::moveQueueItem,
                        onPlayQueued = player::playQueueItem,
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
                        onShareWithAccount = vm::shareWithAccount,
                        onConfirmShare = vm::confirmShare,
                        onRevokeShare = vm::revokeShare,
                        onCloseShareSheet = vm::closeShareSheet,
                        onTrashSelected = vm::trashSelected,
                        onLibrarySource = vm::setLibrarySource,
                        onSignOut = vm::signOut,
                    )
                }
            }
        }
    }
}
