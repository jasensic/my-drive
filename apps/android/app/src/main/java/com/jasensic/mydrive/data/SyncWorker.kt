package com.jasensic.mydrive.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jasensic.mydrive.domain.SyncFilesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncFiles: SyncFilesUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val user = inputData.getString("username")
        val pass = inputData.getString("password")
        syncFiles.execute(user, pass)
        Result.success()
    }.getOrElse { Result.retry() }
}
