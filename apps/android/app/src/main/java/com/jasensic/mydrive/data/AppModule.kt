package com.jasensic.mydrive.data

import com.jasensic.mydrive.domain.ConnectivityMonitor
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.ServerDiscovery
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.SyncStateRepository
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
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvides {
    @Provides
    @Singleton
    fun appDb(provider: DbProvider) = provider.db

    @Provides
    fun syncUseCase(
        connectivity: ConnectivityMonitor,
        discovery: ServerDiscovery,
        remote: RemoteFileSource,
        local: LocalMediaStore,
        state: SyncStateRepository,
    ) = SyncFilesUseCase(connectivity, discovery, remote, local, state, android.os.Build.MODEL)
}
