package com.jasensic.mydrive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AppVersion
import com.jasensic.mydrive.domain.AuthAction
import com.jasensic.mydrive.domain.BrowseRemoteLibraryUseCase
import com.jasensic.mydrive.domain.CheckAppUpdateUseCase
import com.jasensic.mydrive.domain.CheckServerStatusUseCase
import com.jasensic.mydrive.domain.ClassifiedLibrary
import com.jasensic.mydrive.domain.CreateShareUseCase
import com.jasensic.mydrive.domain.InstallAppUpdateUseCase
import com.jasensic.mydrive.domain.LanAvailabilityUseCase
import com.jasensic.mydrive.domain.LibrarySilo
import com.jasensic.mydrive.domain.LibrarySource
import com.jasensic.mydrive.domain.ListLocalLibraryUseCase
import com.jasensic.mydrive.domain.ListSharesUseCase
import com.jasensic.mydrive.domain.ListUsersUseCase
import com.jasensic.mydrive.domain.LoadAppStateUseCase
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.ManageLibraryUseCase
import com.jasensic.mydrive.domain.OpenLocalFileUseCase
import com.jasensic.mydrive.domain.ResourceAccess
import com.jasensic.mydrive.domain.RevokeShareUseCase
import com.jasensic.mydrive.domain.ShareGrant
import com.jasensic.mydrive.domain.ShareLocalFilesUseCase
import com.jasensic.mydrive.domain.SharePermission
import com.jasensic.mydrive.domain.ShareResourceType
import com.jasensic.mydrive.domain.SignOutUseCase
import com.jasensic.mydrive.domain.StreamRemoteFileUseCase
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.domain.SyncProgress
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncScheduler
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.ThemePreferences
import com.jasensic.mydrive.domain.UserProfile
import com.jasensic.mydrive.domain.classifyLibrary
import com.jasensic.mydrive.domain.hasLocalBytes
import com.jasensic.mydrive.domain.syncProgressLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data object Connect : Screen()
    data object Hub : Screen()
    data class Viewer(val fileId: String) : Screen()
    data object NowPlaying : Screen()
}

enum class HubTab { MUSIC, PHOTOS, FILES }

fun HubTab.silo(): LibrarySilo =
    when (this) {
        HubTab.MUSIC -> LibrarySilo.MUSIC
        HubTab.PHOTOS -> LibrarySilo.PHOTOS
        HubTab.FILES -> LibrarySilo.FILES
    }

private data class NavFrame(
    val screen: Screen,
    val tab: HubTab,
    val albumId: String?,
    val artistName: String?,
)

data class ShareSheetState(
    val visible: Boolean = false,
    val resourceType: ShareResourceType = ShareResourceType.FILE,
    val resourceId: String = "",
    val title: String = "",
    val users: List<UserProfile> = emptyList(),
    val grants: List<ShareGrant> = emptyList(),
    val canManage: Boolean = true,
)

data class UiState(
    val screen: Screen = Screen.Connect,
    val tab: HubTab = HubTab.MUSIC,
    val albumId: String? = null,
    val artistName: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val serverLabel: String = "searching…",
    val lastSync: String? = null,
    val loggedIn: Boolean = false,
    val username: String = "",
    val authToken: String? = null,
    val setupRequired: Boolean = false,
    val librarySource: LibrarySource = LibrarySource.DEVICE,
    val lanAvailable: Boolean = false,
    val albums: List<Album> = emptyList(),
    val files: List<LocalFile> = emptyList(),
    val syncProgress: SyncProgress = SyncProgress(),
    val progress: String? = null,
    val error: String? = null,
    val availableUpdate: AppRelease? = null,
    val appVersion: String = "",
    val shareSheet: ShareSheetState = ShareSheetState(),
) {
    val library: ClassifiedLibrary get() = classifyLibrary(files)

    val isBusy: Boolean
        get() = syncProgress.isActive || progress != null

    val statusMessage: String?
        get() = when {
            syncProgress.isActive ||
                syncProgress.phase == SyncPhase.COMPLETED ||
                syncProgress.phase == SyncPhase.FAILED ->
                syncProgressLabel(syncProgress).ifBlank { null }
            else -> progress
        }

    val viewerFile: LocalFile?
        get() = (screen as? Screen.Viewer)?.let { viewer ->
            library.photos.find { it.id == viewer.fileId }
        }

    val selectedFiles: List<LocalFile>
        get() = files.filter { it.id in selectedIds }
}

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncProgressStore: SyncProgressStore,
    private val listLibrary: ListLocalLibraryUseCase,
    private val loadState: LoadAppStateUseCase,
    private val checkUpdate: CheckAppUpdateUseCase,
    private val installUpdate: InstallAppUpdateUseCase,
    private val openLocalFile: OpenLocalFileUseCase,
    private val manageLibrary: ManageLibraryUseCase,
    private val shareFiles: ShareLocalFilesUseCase,
    private val checkStatus: CheckServerStatusUseCase,
    private val signOut: SignOutUseCase,
    private val lan: LanAvailabilityUseCase,
    private val browseRemote: BrowseRemoteLibraryUseCase,
    private val streamRemote: StreamRemoteFileUseCase,
    private val listUsers: ListUsersUseCase,
    private val listShares: ListSharesUseCase,
    private val createShare: CreateShareUseCase,
    private val revokeShare: RevokeShareUseCase,
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
    private val backStack = ArrayDeque<NavFrame>()
    private val forwardStack = ArrayDeque<NavFrame>()

    init {
        viewModelScope.launch {
            syncProgressStore.observe().collect { progress ->
                handleSyncProgress(progress)
            }
        }
        viewModelScope.launch {
            paintLocalLibrary()
            if (_ui.value.loggedIn) {
                viewModelScope.launch { enrichLibraryInBackground() }
                viewModelScope.launch { checkUpdateInBackground() }
                viewModelScope.launch { runCatching { manageLibrary.flushExclusions() } }
                syncExisting()
            } else {
                viewModelScope.launch { probeServerStatus() }
            }
        }
    }

    private suspend fun handleSyncProgress(progress: SyncProgress) {
        _ui.value = _ui.value.copy(
            syncProgress = progress,
            error = when (progress.phase) {
                SyncPhase.FAILED -> progress.errorMessage ?: progress.message
                SyncPhase.CONNECTING, SyncPhase.PREPARING, SyncPhase.DOWNLOADING -> null
                else -> _ui.value.error
            },
        )
        when (progress.phase) {
            SyncPhase.COMPLETED -> {
                val library = runCatching { listLibrary.execute() }.getOrNull()
                val snapshot = runCatching { loadState.execute() }.getOrNull()
                navigate(currentFrame().copy(screen = Screen.Hub), record = _ui.value.screen != Screen.Hub)
                _ui.value = _ui.value.copy(
                    syncProgress = progress,
                    error = null,
                    loggedIn = true,
                    serverLabel = snapshot?.server?.let { "${it.host}:${it.port}" } ?: _ui.value.serverLabel,
                    lastSync = snapshot?.lastSync ?: _ui.value.lastSync,
                    albums = if (_ui.value.librarySource == LibrarySource.DEVICE) {
                        library?.albums ?: _ui.value.albums
                    } else {
                        _ui.value.albums
                    },
                    files = if (_ui.value.librarySource == LibrarySource.DEVICE) {
                        library?.files ?: _ui.value.files
                    } else {
                        _ui.value.files
                    },
                    lanAvailable = lan.isAvailable(),
                    username = snapshot?.username ?: _ui.value.username,
                    authToken = snapshot?.token ?: _ui.value.authToken,
                )
                if (_ui.value.librarySource == LibrarySource.SERVER) {
                    viewModelScope.launch { refreshLibrary() }
                }
                viewModelScope.launch { checkUpdateInBackground() }
                viewModelScope.launch { enrichLibraryInBackground() }
                delay(1_500)
                if (syncProgressStore.current().phase == SyncPhase.COMPLETED) {
                    syncProgressStore.publish(SyncProgress())
                }
            }
            SyncPhase.FAILED -> {
                val needsLogin = progress.errorMessage == "login required" || progress.message == "login required"
                _ui.value = _ui.value.copy(
                    syncProgress = progress,
                    error = if (needsLogin) "Sign in once to this server" else progress.errorMessage,
                    loggedIn = if (needsLogin) false else _ui.value.loggedIn,
                    screen = if (needsLogin) Screen.Connect else _ui.value.screen,
                    setupRequired = _ui.value.setupRequired ||
                        (progress.errorMessage?.contains("setup has not been completed") == true),
                )
                delay(2_000)
                if (syncProgressStore.current().phase == SyncPhase.FAILED) {
                    syncProgressStore.publish(SyncProgress())
                }
            }
            else -> Unit
        }
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) {
            if (_ui.value.selectedIds.isNotEmpty()) {
                clearSelection()
                return true
            }
            return false
        }
        forwardStack.addLast(currentFrame())
        applyFrame(backStack.removeLast())
        return true
    }

    fun goForward(): Boolean {
        if (forwardStack.isEmpty()) return false
        backStack.addLast(currentFrame())
        applyFrame(forwardStack.removeLast())
        return true
    }

    fun selectTab(tab: HubTab) {
        if (_ui.value.tab == tab && _ui.value.screen == Screen.Hub && _ui.value.albumId == null && _ui.value.artistName == null) {
            return
        }
        navigate(currentFrame().copy(screen = Screen.Hub, tab = tab, albumId = null, artistName = null))
    }

    fun openAlbum(albumId: String?) {
        navigate(currentFrame().copy(screen = Screen.Hub, albumId = albumId, artistName = null))
    }

    fun openArtist(name: String?) {
        navigate(currentFrame().copy(screen = Screen.Hub, artistName = name, albumId = null))
    }

    fun openNowPlaying() {
        if (_ui.value.screen == Screen.NowPlaying) return
        navigate(currentFrame().copy(screen = Screen.NowPlaying))
    }

    fun closeNowPlaying() {
        if (!goBack()) {
            navigate(currentFrame().copy(screen = Screen.Hub), record = false)
        }
    }

    fun openViewer(fileId: String) {
        if (_ui.value.screen is Screen.Viewer) {
            _ui.value = _ui.value.copy(screen = Screen.Viewer(fileId))
            return
        }
        navigate(currentFrame().copy(screen = Screen.Viewer(fileId)))
    }

    fun closeViewer() {
        if (!goBack()) {
            navigate(currentFrame().copy(screen = Screen.Hub), record = false)
        }
    }

    fun showConnect() {
        navigate(currentFrame().copy(screen = Screen.Connect))
    }

    fun openLibrary() {
        if (_ui.value.files.isNotEmpty() || _ui.value.loggedIn) {
            if (!goBack() || _ui.value.screen == Screen.Connect) {
                navigate(currentFrame().copy(screen = Screen.Hub), record = false)
            }
        }
    }

    fun toggleSelect(id: String) {
        val next = _ui.value.selectedIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _ui.value = _ui.value.copy(selectedIds = next)
    }

    fun selectOnly(id: String) {
        _ui.value = _ui.value.copy(selectedIds = setOf(id))
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selectedIds = emptySet())
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assign = _ui.value.selectedIds.toList()
            runCatching {
                val album = manageLibrary.createAlbum(trimmed, _ui.value.tab.silo())
                if (assign.isNotEmpty()) {
                    manageLibrary.assignAlbum(assign, album.id)
                }
                album
            }
                .onSuccess { album ->
                    clearSelection()
                    refreshLibrary()
                    if (assign.isEmpty()) {
                        openAlbum(album.id)
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun renameAlbum(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { manageLibrary.renameAlbum(id, trimmed) }
                .onSuccess { refreshLibrary() }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun deleteAlbum(id: String) {
        viewModelScope.launch {
            runCatching { manageLibrary.deleteAlbum(id) }
                .onSuccess {
                    if (_ui.value.albumId == id) openAlbum(null)
                    refreshLibrary()
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun renameSelected(name: String) {
        val id = _ui.value.selectedIds.singleOrNull() ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { manageLibrary.renameFile(id, trimmed) }
                .onSuccess {
                    clearSelection()
                    refreshLibrary()
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun moveSelected(albumId: String?) {
        val ids = _ui.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { manageLibrary.assignAlbum(ids, albumId) }
                .onSuccess {
                    clearSelection()
                    refreshLibrary()
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun trashSelected() {
        val ids = _ui.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { manageLibrary.excludeFromDevice(ids) }
                .onSuccess {
                    clearSelection()
                    refreshLibrary()
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun shareSelected() {
        val files = _ui.value.selectedFiles
        runCatching { shareFiles.execute(files) }
            .onFailure { _ui.value = _ui.value.copy(error = it.message) }
    }

    fun shareWithAccount() {
        val album = _ui.value.albums.find { it.id == _ui.value.albumId }
        val selected = _ui.value.selectedFiles
        when {
            selected.size == 1 -> openShareSheet(
                ShareResourceType.FILE,
                selected.first().id,
                selected.first().name,
                selected.first().access,
            )
            album != null && selected.isEmpty() -> openShareSheet(
                ShareResourceType.ALBUM,
                album.id,
                album.name,
                album.access,
            )
            else -> _ui.value = _ui.value.copy(error = "Select one file, or open an album, to share with an account")
        }
    }

    fun closeShareSheet() {
        _ui.value = _ui.value.copy(shareSheet = ShareSheetState())
    }

    fun confirmShare(granteeId: String, permission: SharePermission) {
        val sheet = _ui.value.shareSheet
        if (!sheet.visible) return
        viewModelScope.launch {
            runCatching {
                createShare.execute(sheet.resourceType, sheet.resourceId, granteeId, permission)
                listShares.execute(sheet.resourceType, sheet.resourceId)
            }.onSuccess { grants ->
                _ui.value = _ui.value.copy(
                    shareSheet = sheet.copy(grants = grants),
                    error = null,
                )
            }.onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun revokeShare(shareId: String) {
        val sheet = _ui.value.shareSheet
        viewModelScope.launch {
            runCatching {
                revokeShare.execute(shareId)
                listShares.execute(sheet.resourceType, sheet.resourceId)
            }.onSuccess { grants ->
                _ui.value = _ui.value.copy(shareSheet = sheet.copy(grants = grants), error = null)
            }.onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun setLibrarySource(source: LibrarySource) {
        if (source == _ui.value.librarySource) return
        if (source == LibrarySource.SERVER && !lan.isAvailable()) {
            _ui.value = _ui.value.copy(error = "On server needs Wi-Fi", lanAvailable = false)
            return
        }
        _ui.value = _ui.value.copy(librarySource = source, error = null, lanAvailable = lan.isAvailable())
        viewModelScope.launch { refreshLibrary() }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { signOut.execute() }
            backStack.clear()
            forwardStack.clear()
            _ui.value = _ui.value.copy(
                screen = Screen.Connect,
                loggedIn = false,
                username = "",
                authToken = null,
                albums = emptyList(),
                files = emptyList(),
                selectedIds = emptySet(),
                librarySource = LibrarySource.DEVICE,
                lastSync = null,
                error = null,
                shareSheet = ShareSheetState(),
            )
            probeServerStatus()
        }
    }

    fun stepViewer(delta: Int) {
        val visible = _ui.value.library.photos.let { photos ->
            val albumId = _ui.value.albumId
            if (albumId == null) photos else photos.filter { it.albumId == albumId }
        }
        val currentId = (_ui.value.screen as? Screen.Viewer)?.fileId ?: return
        val index = visible.indexOfFirst { it.id == currentId }
        if (index < 0) return
        val next = (index + delta).coerceIn(0, visible.lastIndex)
        _ui.value = _ui.value.copy(screen = Screen.Viewer(visible[next].id))
        syncNavFlags()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.set(mode) }
    }

    fun openDocument(fileId: String) {
        viewModelScope.launch {
            val current = _ui.value.files.find { it.id == fileId } ?: return@launch
            runCatching {
                val ready = if (current.hasLocalBytes()) {
                    current
                } else if (_ui.value.librarySource == LibrarySource.SERVER || !current.remoteUrl.isNullOrBlank()) {
                    streamRemote.execute(current)
                } else {
                    _ui.value = _ui.value.copy(error = null)
                    syncScheduler.enqueue()
                    val result = syncProgressStore.observe().first {
                        it.phase == SyncPhase.COMPLETED || it.phase == SyncPhase.FAILED
                    }
                    if (result.phase == SyncPhase.FAILED) {
                        error(result.errorMessage ?: "Sync failed")
                    }
                    val library = listLibrary.execute()
                    _ui.value = _ui.value.copy(albums = library.albums, files = library.files)
                    library.files.find { it.id == fileId }
                        ?: error("File is not on this device yet. Sync first.")
                }
                openLocalFile.execute(ready)
            }.onFailure {
                _ui.value = _ui.value.copy(error = it.message)
            }
        }
    }

    fun sync(username: String, password: String, manualHost: String) {
        connect(username, password, manualHost, AuthAction.LOGIN)
    }

    fun register(username: String, password: String, manualHost: String) {
        connect(username, password, manualHost, AuthAction.REGISTER)
    }

    fun setupAdmin(username: String, password: String, manualHost: String) {
        connect(username, password, manualHost, AuthAction.SETUP)
    }

    fun syncExisting() {
        scanLan("")
    }

    fun scanLan(manualHost: String) {
        connect(null, null, manualHost.ifBlank { null }, AuthAction.LOGIN)
    }

    private fun connect(username: String?, password: String?, manualHost: String?, authAction: AuthAction) {
        _ui.value = _ui.value.copy(error = null)
        syncScheduler.enqueue(
            username?.ifBlank { null },
            password?.ifBlank { null },
            manualHost?.ifBlank { null },
            authAction,
        )
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

    private suspend fun paintLocalLibrary() {
        val snapshot = runCatching { loadState.execute() }.getOrNull() ?: return
        _ui.value = _ui.value.copy(
            serverLabel = snapshot.server?.let { "${it.host}:${it.port}" }
                ?: if (snapshot.loggedIn) "offline" else "searching…",
            lastSync = snapshot.lastSync,
            albums = snapshot.library.albums,
            files = snapshot.library.files,
            loggedIn = snapshot.loggedIn,
            username = snapshot.username,
            authToken = snapshot.token,
            lanAvailable = lan.isAvailable(),
            librarySource = LibrarySource.DEVICE,
            screen = if (snapshot.loggedIn) Screen.Hub else Screen.Connect,
        )
        syncNavFlags()
    }

    private suspend fun probeServerStatus(manualHost: String? = null) {
        val status = runCatching { checkStatus.execute(manualHost) }.getOrNull() ?: return
        val server = runCatching { loadState.execute() }.getOrNull()?.server
        _ui.value = _ui.value.copy(
            setupRequired = status.setupRequired,
            lanAvailable = lan.isAvailable(),
            serverLabel = server?.let { "${it.host}:${it.port}" } ?: _ui.value.serverLabel,
        )
    }

    private fun openShareSheet(
        type: ShareResourceType,
        resourceId: String,
        title: String,
        access: ResourceAccess,
    ) {
        viewModelScope.launch {
            val canManage = access == ResourceAccess.OWNER
            val users = runCatching { listUsers.execute() }.getOrElse { emptyList() }
            val grants = runCatching { listShares.execute(type, resourceId) }.getOrElse { emptyList() }
            _ui.value = _ui.value.copy(
                shareSheet = ShareSheetState(
                    visible = true,
                    resourceType = type,
                    resourceId = resourceId,
                    title = title,
                    users = users,
                    grants = grants,
                    canManage = canManage,
                ),
                error = null,
            )
        }
    }

    private suspend fun enrichLibraryInBackground() {
        if (_ui.value.librarySource != LibrarySource.DEVICE) return
        val library = runCatching { listLibrary.enrich() }.getOrNull() ?: return
        _ui.value = _ui.value.copy(albums = library.albums, files = library.files)
    }

    private suspend fun checkUpdateInBackground() {
        val update = runCatching { checkUpdate.execute() }.getOrNull()
        _ui.value = _ui.value.copy(availableUpdate = update)
    }

    private suspend fun refreshLibrary() {
        val source = _ui.value.librarySource
        val library = runCatching {
            if (source == LibrarySource.SERVER && lan.isAvailable()) {
                browseRemote.execute()
            } else {
                listLibrary.execute()
            }
        }.getOrNull() ?: return
        _ui.value = _ui.value.copy(
            albums = library.albums,
            files = library.files,
            error = null,
            lanAvailable = lan.isAvailable(),
        )
    }

    private fun currentFrame() = NavFrame(
        screen = _ui.value.screen,
        tab = _ui.value.tab,
        albumId = _ui.value.albumId,
        artistName = _ui.value.artistName,
    )

    private fun navigate(next: NavFrame, record: Boolean = true) {
        val current = currentFrame()
        if (current == next) return
        if (record) {
            backStack.addLast(current)
            forwardStack.clear()
        }
        applyFrame(next)
    }

    private fun applyFrame(frame: NavFrame) {
        _ui.value = _ui.value.copy(
            screen = frame.screen,
            tab = frame.tab,
            albumId = frame.albumId,
            artistName = frame.artistName,
            selectedIds = emptySet(),
            canGoBack = backStack.isNotEmpty(),
            canGoForward = forwardStack.isNotEmpty(),
        )
    }

    private fun syncNavFlags() {
        _ui.value = _ui.value.copy(
            canGoBack = backStack.isNotEmpty(),
            canGoForward = forwardStack.isNotEmpty(),
        )
    }
}
