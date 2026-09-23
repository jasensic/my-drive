package com.jasensic.mydrive.data

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.jasensic.mydrive.BuildConfig
import com.jasensic.mydrive.domain.AppUpdateInstaller
import com.jasensic.mydrive.domain.AppVersion
import com.jasensic.mydrive.domain.AudioPlayer
import com.jasensic.mydrive.domain.BrowseRemoteLibraryUseCase
import com.jasensic.mydrive.domain.CheckAppUpdateUseCase
import com.jasensic.mydrive.domain.CheckServerStatusUseCase
import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.CreateShareUseCase
import com.jasensic.mydrive.domain.DeviceExclusionStore
import com.jasensic.mydrive.domain.DiscoverServerUseCase
import com.jasensic.mydrive.domain.ExternalFileOpener
import com.jasensic.mydrive.domain.InstallAppUpdateUseCase
import com.jasensic.mydrive.domain.LanAvailabilityUseCase
import com.jasensic.mydrive.domain.ListLocalLibraryUseCase
import com.jasensic.mydrive.domain.ListSharesUseCase
import com.jasensic.mydrive.domain.ListUsersUseCase
import com.jasensic.mydrive.domain.LoadAppStateUseCase
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ManageLibraryUseCase
import com.jasensic.mydrive.domain.MediaSharer
import com.jasensic.mydrive.domain.OpenLocalFileUseCase
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.RevokeShareUseCase
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.ShareLocalFilesUseCase
import com.jasensic.mydrive.domain.SignOutUseCase
import com.jasensic.mydrive.domain.StreamRemoteFileUseCase
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncScheduler
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.ThemePreferences
import com.jasensic.mydrive.domain.InMemorySyncProgressStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortBindings {
    @Binds abstract fun connectivity(impl: AndroidConnectivityMonitor): ConnectivityMonitor
    @Binds abstract fun discovery(impl: NsdServerDiscovery): ServerDiscovery
    @Binds abstract fun remote(impl: RetrofitRemoteFileSource): RemoteFileSource
    @Binds abstract fun local(impl: RoomLocalMediaStore): LocalMediaStore
    @Binds abstract fun state(impl: DataStoreSyncState): SyncStateRepository
    @Binds abstract fun exclusions(impl: DataStoreDeviceExclusions): DeviceExclusionStore
    @Binds abstract fun installer(impl: AndroidAppUpdateInstaller): AppUpdateInstaller
    @Binds abstract fun audioPlayer(impl: ExoPlayerAudioPlayer): AudioPlayer
    @Binds abstract fun fileOpener(impl: AndroidExternalFileOpener): ExternalFileOpener
    @Binds abstract fun mediaSharer(impl: AndroidMediaSharer): MediaSharer
    @Binds abstract fun themePrefs(impl: DataStoreThemePreferences): ThemePreferences
    @Binds abstract fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvides {
    @Provides
    @Singleton
    fun syncProgressStore(): SyncProgressStore = InMemorySyncProgressStore()

    @Provides
    @Singleton
    fun appVersion(): AppVersion = object : AppVersion {
        override fun currentCode() = BuildConfig.VERSION_CODE
        override fun currentName() = BuildConfig.VERSION_NAME
    }

    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        state: SyncStateRepository,
    ): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val token = state.cachedToken()
                val next = if (!token.isNullOrBlank() && request.header("Authorization").isNullOrBlank()) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(next)
            }
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
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
    ) = LoadAppStateUseCase(state, local)

    @Provides
    fun syncUseCase(
        connectivity: ConnectivityMonitor,
        discovery: DiscoverServerUseCase,
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
        progress: SyncProgressStore,
        exclusions: DeviceExclusionStore,
    ) = SyncFilesUseCase(
        connectivity,
        discovery,
        remote,
        local,
        state,
        android.os.Build.MODEL,
        progress,
        exclusions,
    )

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
        exclusions: DeviceExclusionStore,
    ) = ManageLibraryUseCase(remote, local, state, exclusions)

    @Provides
    fun checkServerStatusUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
        discovery: DiscoverServerUseCase,
    ) = CheckServerStatusUseCase(remote, state, discovery)

    @Provides
    fun signOutUseCase(
        state: SyncStateRepository,
        local: LocalMediaStore,
    ) = SignOutUseCase(state, local)

    @Provides
    fun lanAvailabilityUseCase(connectivity: ConnectivityMonitor) = LanAvailabilityUseCase(connectivity)

    @Provides
    fun browseRemoteLibraryUseCase(
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
    ) = BrowseRemoteLibraryUseCase(remote, local, state)

    @Provides
    fun streamRemoteFileUseCase(
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
    ) = StreamRemoteFileUseCase(remote, local, state)

    @Provides
    fun listUsersUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
    ) = ListUsersUseCase(remote, state)

    @Provides
    fun listSharesUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
    ) = ListSharesUseCase(remote, state)

    @Provides
    fun createShareUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
    ) = CreateShareUseCase(remote, state)

    @Provides
    fun revokeShareUseCase(
        remote: RemoteFileSource,
        state: SyncStateRepository,
    ) = RevokeShareUseCase(remote, state)
}
