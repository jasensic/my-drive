package com.jasensic.mydrive.data

import com.jasensic.mydrive.BuildConfig
import com.jasensic.mydrive.domain.AppUpdateInstaller
import com.jasensic.mydrive.domain.AppVersion
import com.jasensic.mydrive.domain.AudioPlayer
import com.jasensic.mydrive.domain.CheckAppUpdateUseCase
import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.DiscoverServerUseCase
import com.jasensic.mydrive.domain.ExternalFileOpener
import com.jasensic.mydrive.domain.InstallAppUpdateUseCase
import com.jasensic.mydrive.domain.ListLocalLibraryUseCase
import com.jasensic.mydrive.domain.LoadAppStateUseCase
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ManageLibraryUseCase
import com.jasensic.mydrive.domain.MediaSharer
import com.jasensic.mydrive.domain.OpenLocalFileUseCase
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.ShareLocalFilesUseCase
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.ThemePreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortBindings {
    @Binds abstract fun connectivity(impl: AndroidConnectivityMonitor): ConnectivityMonitor
    @Binds abstract fun discovery(impl: NsdServerDiscovery): ServerDiscovery
    @Binds abstract fun remote(impl: RetrofitRemoteFileSource): RemoteFileSource
    @Binds abstract fun local(impl: RoomLocalMediaStore): LocalMediaStore
    @Binds abstract fun state(impl: DataStoreSyncState): SyncStateRepository
    @Binds abstract fun installer(impl: AndroidAppUpdateInstaller): AppUpdateInstaller
    @Binds abstract fun audioPlayer(impl: ExoPlayerAudioPlayer): AudioPlayer
    @Binds abstract fun fileOpener(impl: AndroidExternalFileOpener): ExternalFileOpener
    @Binds abstract fun mediaSharer(impl: AndroidMediaSharer): MediaSharer
    @Binds abstract fun themePrefs(impl: DataStoreThemePreferences): ThemePreferences
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvides {
    @Provides
    @Singleton
    fun appDb(provider: DbProvider) = provider.db

    @Provides
    @Singleton
    fun appVersion(): AppVersion = object : AppVersion {
        override fun currentCode() = BuildConfig.VERSION_CODE
        override fun currentName() = BuildConfig.VERSION_NAME
    }

    @Provides
    fun discoverUseCase(
        connectivity: ConnectivityMonitor,
        discovery: ServerDiscovery,
        state: SyncStateRepository,
    ) = DiscoverServerUseCase(connectivity, discovery, state)

    @Provides
    fun listLibraryUseCase(local: LocalMediaStore) = ListLocalLibraryUseCase(local)

    @Provides
    fun loadAppStateUseCase(
        state: SyncStateRepository,
        local: LocalMediaStore,
        discovery: ServerDiscovery,
    ) = LoadAppStateUseCase(state, local, discovery)

    @Provides
    fun syncUseCase(
        connectivity: ConnectivityMonitor,
        discovery: DiscoverServerUseCase,
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
    ) = SyncFilesUseCase(connectivity, discovery, remote, local, state, android.os.Build.MODEL)

    @Provides
    fun checkUpdateUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
        version: AppVersion,
    ) = CheckAppUpdateUseCase(remote, state, version)

    @Provides
    fun installUpdateUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
        installer: AppUpdateInstaller,
    ) = InstallAppUpdateUseCase(remote, state, installer)

    @Provides
    fun openLocalFileUseCase(opener: ExternalFileOpener) = OpenLocalFileUseCase(opener)

    @Provides
    fun shareLocalFilesUseCase(sharer: MediaSharer) = ShareLocalFilesUseCase(sharer)

    @Provides
    fun manageLibraryUseCase(
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
    ) = ManageLibraryUseCase(remote, local, state)
}
