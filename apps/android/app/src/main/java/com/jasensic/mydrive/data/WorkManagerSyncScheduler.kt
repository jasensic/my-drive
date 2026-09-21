package com.jasensic.mydrive.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.domain.SyncProgress
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressStore: SyncProgressStore,
) : SyncScheduler {
    override fun enqueue(username: String?, password: String?, manualHost: String?) {
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
}
