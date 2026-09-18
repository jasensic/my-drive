package com.jasensic.mydrive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AppVersion
import com.jasensic.mydrive.domain.CheckAppUpdateUseCase
import com.jasensic.mydrive.domain.ClassifiedLibrary
import com.jasensic.mydrive.domain.InstallAppUpdateUseCase
import com.jasensic.mydrive.domain.ListLocalLibraryUseCase
import com.jasensic.mydrive.domain.LoadAppStateUseCase
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.OpenLocalFileUseCase
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.ThemePreferences
import com.jasensic.mydrive.domain.classifyLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data object Connect : Screen()
    data object Hub : Screen()
    data class Viewer(val fileId: String) : Screen()
    data object NowPlaying : Screen()
}

enum class HubTab { MUSIC, PHOTOS, FILES }

data class UiState(
    val screen: Screen = Screen.Connect,
    val tab: HubTab = HubTab.MUSIC,
    val serverLabel: String = "searching…",
    val lastSync: String? = null,
    val loggedIn: Boolean = false,
    val albums: List<Album> = emptyList(),
    val files: List<LocalFile> = emptyList(),
    val progress: String? = null,
    val error: String? = null,
    val availableUpdate: AppRelease? = null,
    val appVersion: String = "",
) {
    val library: ClassifiedLibrary get() = classifyLibrary(files)

    val viewerFile: LocalFile?
        get() = (screen as? Screen.Viewer)?.let { viewer ->
            library.photos.find { it.id == viewer.fileId }
        }
}

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val syncFiles: SyncFilesUseCase,
    private val listLibrary: ListLocalLibraryUseCase,
    private val loadState: LoadAppStateUseCase,
    private val checkUpdate: CheckAppUpdateUseCase,
    private val installUpdate: InstallAppUpdateUseCase,
    private val openLocalFile: OpenLocalFileUseCase,
    themePreferences: ThemePreferences,
    appVersion: AppVersion,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiState(appVersion = "${appVersion.currentName()} (${appVersion.currentCode()})"))
    val ui = _ui.asStateFlow()
    val themeMode = themePreferences.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemeMode.SYSTEM,
    )
    private val prefs = themePreferences

    init {
        viewModelScope.launch {
            refresh(discoverOnStart = true)
            if (_ui.value.loggedIn) {
                syncExisting()
            }
        }
    }

    fun selectTab(tab: HubTab) {
        _ui.value = _ui.value.copy(tab = tab, screen = Screen.Hub)
    }

    fun openNowPlaying() {
        _ui.value = _ui.value.copy(screen = Screen.NowPlaying)
    }

    fun closeNowPlaying() {
        _ui.value = _ui.value.copy(screen = Screen.Hub)
    }

    fun openViewer(fileId: String) {
        _ui.value = _ui.value.copy(screen = Screen.Viewer(fileId))
    }

    fun closeViewer() {
        _ui.value = _ui.value.copy(screen = Screen.Hub)
    }

    fun showConnect() {
        _ui.value = _ui.value.copy(screen = Screen.Connect, error = null, progress = null)
    }

    fun openLibrary() {
        if (_ui.value.files.isNotEmpty() || _ui.value.loggedIn) {
            _ui.value = _ui.value.copy(screen = Screen.Hub)
        }
    }

    fun stepViewer(delta: Int) {
        val visible = _ui.value.library.photos
        val currentId = (_ui.value.screen as? Screen.Viewer)?.fileId ?: return
        val index = visible.indexOfFirst { it.id == currentId }
        if (index < 0) return
        val next = (index + delta).coerceIn(0, visible.lastIndex)
        _ui.value = _ui.value.copy(screen = Screen.Viewer(visible[next].id))
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.set(mode) }
    }

    fun openDocument(fileId: String) {
        viewModelScope.launch {
            val current = _ui.value.files.find { it.id == fileId } ?: return@launch
            runCatching {
                if (!File(current.path).isFile) {
                    _ui.value = _ui.value.copy(progress = "Downloading ${current.name}…", error = null)
                    syncFiles.execute()
                    val library = listLibrary.execute()
                    _ui.value = _ui.value.copy(albums = library.albums, files = library.files)
                    val updated = library.files.find { it.id == fileId }
                        ?: error("File is not on this device yet. Sync first.")
                    openLocalFile.execute(updated)
                } else {
                    openLocalFile.execute(current)
                }
            }.onSuccess {
                _ui.value = _ui.value.copy(progress = null)
            }.onFailure {
                _ui.value = _ui.value.copy(progress = null, error = it.message)
            }
        }
    }

    fun sync(username: String, password: String, manualHost: String) {
        connect(username, password, manualHost)
    }

    fun syncExisting() {
        scanLan("")
    }

    fun scanLan(manualHost: String) {
        connect(null, null, manualHost.ifBlank { null })
    }

    private fun connect(username: String?, password: String?, manualHost: String?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                progress = if (username.isNullOrBlank()) "Searching the LAN…" else "Signing in…",
                error = null,
            )
            runCatching {
                val result = syncFiles.execute(
                    username?.ifBlank { null },
                    password?.ifBlank { null },
                    manualHost?.ifBlank { null },
                )
                val library = listLibrary.execute()
                val update = runCatching { checkUpdate.execute() }.getOrNull()
                Triple(result, library, update)
            }.onSuccess { (result, library, update) ->
                _ui.value = _ui.value.copy(
                    progress = null,
                    error = null,
                    screen = Screen.Hub,
                    loggedIn = true,
                    serverLabel = "${result.server.host}:${result.server.port}",
                    lastSync = result.manifest.generatedAt,
                    albums = library.albums,
                    files = library.files,
                    availableUpdate = update,
                )
            }.onFailure {
                val needsLogin = it.message == "login required"
                _ui.value = _ui.value.copy(
                    progress = null,
                    error = if (needsLogin) "Sign in once to this server" else it.message,
                    loggedIn = if (needsLogin) false else _ui.value.loggedIn,
                    screen = if (needsLogin) Screen.Connect else _ui.value.screen,
                )
            }
        }
    }

    fun installUpdate() {
        val release = _ui.value.availableUpdate ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(progress = "Downloading update ${release.versionName}…", error = null)
            runCatching { installUpdate.execute(release) }
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        progress = "Installed ${release.versionName}. Restart the app if it did not reopen.",
                        availableUpdate = null,
                        error = null,
                    )
                }
                .onFailure {
                    _ui.value = _ui.value.copy(progress = null, error = it.message)
                }
        }
    }

    private suspend fun refresh(discoverOnStart: Boolean) {
        val snapshot = runCatching { loadState.execute(discoverOnStart) }.getOrNull() ?: return
        val update = if (snapshot.loggedIn) runCatching { checkUpdate.execute() }.getOrNull() else null
        _ui.value = _ui.value.copy(
            serverLabel = snapshot.server?.let { "${it.host}:${it.port}" } ?: "not found",
            lastSync = snapshot.lastSync,
            albums = snapshot.library.albums,
            files = snapshot.library.files,
            availableUpdate = update,
            loggedIn = snapshot.loggedIn,
            screen = if (snapshot.loggedIn) Screen.Hub else Screen.Connect,
        )
    }
}
