package com.jasensic.mydrive.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.jasensic.mydrive.R
import com.jasensic.mydrive.domain.SyncFilesUseCase
import com.jasensic.mydrive.domain.SyncPhase
import com.jasensic.mydrive.domain.SyncProgress
import com.jasensic.mydrive.domain.SyncProgressStore
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.WIFI_UNAVAILABLE
import com.jasensic.mydrive.domain.syncProgressLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncFiles: SyncFilesUseCase,
    private val progressStore: SyncProgressStore,
    private val syncState: SyncStateRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val user = inputData.getString(KEY_USERNAME)
        val pass = inputData.getString(KEY_PASSWORD)
        if (user.isNullOrBlank() && pass.isNullOrBlank() && syncState.session() == null) {
            return@coroutineScope Result.success()
        }
        ensureChannel()
        val updates = launch {
            progressStore.observe().collectLatest { progress ->
                when (progress.phase) {
                    SyncPhase.IDLE -> Unit
                    SyncPhase.COMPLETED, SyncPhase.FAILED -> {
                        runCatching { setForeground(foregroundInfo(progress)) }
                        showTerminalNotification(progress)
                    }
                    else -> runCatching { setForeground(foregroundInfo(progress)) }
                }
            }
        }
        try {
            setForeground(foregroundInfo(progressStore.current()))
            val host = inputData.getString(KEY_HOST)
            val action = com.jasensic.mydrive.domain.parseAuthAction(inputData.getString(KEY_AUTH_ACTION))
            syncFiles.execute(user, pass, host, action)
            Result.success()
        } catch (err: Throwable) {
            val message = err.message.orEmpty()
            if (message == "login required" ||
                message == WIFI_UNAVAILABLE ||
                message.contains("not found on LAN")
            ) {
                Result.failure()
            } else {
                Result.retry()
            }
        } finally {
            updates.cancel()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(R.string.sync_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun foregroundInfo(progress: SyncProgress): ForegroundInfo {
        val notification = buildProgressNotification(progress, ongoing = progress.phase.isOngoing())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun SyncPhase.isOngoing(): Boolean =
        this == SyncPhase.CONNECTING || this == SyncPhase.PREPARING || this == SyncPhase.DOWNLOADING

    private fun buildProgressNotification(progress: SyncProgress, ongoing: Boolean): Notification {
        val title = when (progress.phase) {
            SyncPhase.COMPLETED -> appContext.getString(R.string.sync_notification_complete_title)
            SyncPhase.FAILED -> appContext.getString(R.string.sync_notification_failed_title)
            else -> appContext.getString(R.string.sync_notification_title)
        }
        val text = syncProgressLabel(progress).ifBlank {
            appContext.getString(R.string.sync_notification_title)
        }
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        when {
            progress.phase == SyncPhase.DOWNLOADING && progress.totalFiles > 0 -> {
                val max = 1_000
                val value = (progress.fraction * max).toInt().coerceIn(0, max)
                builder.setProgress(max, value, false)
                if (progress.currentTotalBytes > 0L) {
                    builder.setSubText(
                        "${progress.completedFiles}/${progress.totalFiles} · " +
                            formatBytes(progress.currentBytes) + " / " +
                            formatBytes(progress.currentTotalBytes),
                    )
                } else {
                    builder.setSubText("${progress.completedFiles}/${progress.totalFiles}")
                }
            }
            progress.phase == SyncPhase.COMPLETED || progress.phase == SyncPhase.FAILED -> {
                builder.setProgress(0, 0, false)
                builder.setAutoCancel(true)
            }
            else -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun showTerminalNotification(progress: SyncProgress) {
        val notification = buildProgressNotification(progress, ongoing = false)
        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }

    companion object {
        const val UNIQUE_NAME = "mydrive-sync"
        const val PERIODIC_NAME = "mydrive-sync-periodic"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_HOST = "host"
        const val KEY_AUTH_ACTION = "authAction"
        const val CHANNEL_ID = "mydrive_sync"
        const val NOTIFICATION_ID = 42
    }
}
