package com.jasensic.mydrive.presentation

import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaKind
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: DriveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by vm.ui.collectAsState()
                    when (val screen = state.screen) {
                        Screen.Connect -> ConnectScreen(state, vm::sync, vm::scanLan, vm::openLibrary)
                        Screen.Library -> LibraryScreen(
                            state = state,
                            onAlbum = vm::selectAlbum,
                            onOpen = vm::openFile,
                            onSync = vm::syncExisting,
                            onUpdate = vm::installUpdate,
                        )
                        is Screen.Viewer -> {
                            val visible = state.visibleFiles
                            val current = visible.find { it.id == screen.fileId } ?: state.currentFile
                            if (current == null) {
                                LibraryScreen(
                                    state = state,
                                    onAlbum = vm::selectAlbum,
                                    onOpen = vm::openFile,
                                    onSync = vm::syncExisting,
                                    onUpdate = vm::installUpdate,
                                )
                            } else {
                                val index = visible.indexOfFirst { it.id == current.id }
                                ViewerScreen(
                                    file = current,
                                    position = index + 1,
                                    total = visible.size,
                                    hasPrev = index > 0,
                                    hasNext = index >= 0 && index < visible.lastIndex,
                                    onBack = vm::closeViewer,
                                    onPrev = { vm.stepViewer(-1) },
                                    onNext = { vm.stepViewer(1) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(
    state: UiState,
    onSignIn: (String, String, String) -> Unit,
    onScanLan: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("my-drive", style = MaterialTheme.typography.headlineSmall)
        Text("Server: ${state.serverLabel}")
        Text("Last sync: ${state.lastSync ?: "never"}")
        OutlinedTextField(host, { host = it }, label = { Text("Host (optional, e.g. 192.168.1.10:8080)") }, modifier = Modifier.fillMaxWidth())
        if (state.loggedIn) {
            Text(
                "You are already signed in. The app scans the Wi-Fi for `_mydrive._tcp` and syncs without asking for a password again.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { onScanLan(host) }, enabled = state.progress == null) {
                Text("Scan LAN and sync")
            }
        } else {
            Text(
                "Sign in once. After that, keep the phone on the same Wi-Fi: the app finds the server by scanning the network.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSignIn(user, pass, host) }, enabled = state.progress == null) {
                Text("Sign in and download")
            }
        }
        if (state.files.isNotEmpty()) {
            TextButton(onClick = onOpenLibrary) { Text("Open library (${state.files.size} files)") }
        }
        state.progress?.let {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(it)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LibraryScreen(
    state: UiState,
    onAlbum: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onSync: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineSmall)
                Text("Server ${state.serverLabel}")
                Text("${state.files.size} files · last sync ${state.lastSync ?: "never"}")
            }
            OutlinedButton(onClick = onSync) { Text("Sync") }
        }
        state.availableUpdate?.let { release ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Update available: ${release.versionName}")
                    if (release.changelog.isNotBlank()) Text(release.changelog, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onUpdate, enabled = state.progress == null) { Text("Install update") }
                }
            }
        }
        state.progress?.let {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(it)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedAlbumId == null,
                    onClick = { onAlbum(null) },
                    label = { Text("All") },
                )
            }
            items(state.albums, key = { it.id }) { album ->
                FilterChip(
                    selected = state.selectedAlbumId == album.id,
                    onClick = { onAlbum(album.id) },
                    label = { Text(album.name) },
                )
            }
        }
        if (state.visibleFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files downloaded yet. Use Sync to pull the library.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.visibleFiles, key = { it.id }) { file ->
                    FileCard(file, onClick = { onOpen(file.id) })
                }
            }
        }
    }
}

@Composable
private fun FileCard(file: LocalFile, onClick: () -> Unit) {
    Card(Modifier.clickable(onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                when (file.mediaKind) {
                    MediaKind.PHOTO -> AsyncImage(
                        model = File(file.path),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    MediaKind.VIDEO -> Text("Video", modifier = Modifier.padding(8.dp))
                    MediaKind.AUDIO -> Text("Audio", modifier = Modifier.padding(8.dp))
                    MediaKind.OTHER -> Text("File", modifier = Modifier.padding(8.dp))
                }
            }
            Text(file.name, modifier = Modifier.padding(8.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ViewerScreen(
    file: LocalFile,
    position: Int,
    total: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Library") }
            Spacer(Modifier.weight(1f))
            Text("$position / $total", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPrev, enabled = hasPrev) { Text("Prev") }
            TextButton(onClick = onNext, enabled = hasNext) { Text("Next") }
        }
        Text(file.name, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        Text(
            file.albumName ?: "No album",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            key(file.id) {
                when (file.mediaKind) {
                    MediaKind.PHOTO -> AsyncImage(
                        model = File(file.path),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                    MediaKind.VIDEO, MediaKind.AUDIO -> AndroidView(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoPath(file.path)
                                val controller = MediaController(context)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                start()
                            }
                        },
                    )
                    MediaKind.OTHER -> Text("Saved locally as ${file.name}", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
