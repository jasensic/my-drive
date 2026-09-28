package com.jasensic.mydrive.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.jasensic.mydrive.domain.AuthAction
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.domain.SyncProgress
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncScheduler
import com.jasensic.mydrive.domain.wireValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressStore: SyncProgressStore,
) : SyncScheduler {
    override fun enqueue(username: String?, password: String?, manualHost: String?, authAction: AuthAction) {
        if (!progressStore.current().isActive) {
            progressStore.publish(
                SyncProgress(
                    phase = SyncPhase.CONNECTING,
                    message = if (username.isNullOrBlank()) "Searching the LAN…" else "Signing in…",
                ),
            )
        }
        val data = androidx.work.Data.Builder().apply {
            username?.let { putString(SyncWorker.KEY_USERNAME, it) }
            password?.let { putString(SyncWorker.KEY_PASSWORD, it) }
            manualHost?.let { putString(SyncWorker.KEY_HOST, it) }
            putString(SyncWorker.KEY_AUTH_ACTION, authAction.wireValue())
        }.build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun ensureBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(androidx.work.Data.Builder().putBoolean(SyncWorker.KEY_BACKGROUND, true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        registerWifiTrigger()
    }

    override fun enqueueBackground() {
        if (!progressStore.current().isActive) {
            progressStore.publish(
                SyncProgress(
                    phase = SyncPhase.CONNECTING,
                    message = "Searching the LAN…",
                ),
            )
        }
        val data = androidx.work.Data.Builder().putBoolean(SyncWorker.KEY_BACKGROUND, true).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun registerWifiTrigger() {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            0,
            android.content.Intent(context, WifiSyncReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        synchronized(this) {
            runCatching { cm.unregisterNetworkCallback(pending) }
            runCatching { cm.registerNetworkCallback(request, pending) }
        }
    }
}
