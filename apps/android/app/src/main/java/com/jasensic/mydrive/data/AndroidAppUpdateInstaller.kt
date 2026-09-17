package com.jasensic.mydrive.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AppUpdateInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppUpdateInstaller {
    override fun apkPath(release: AppRelease): String {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        return File(dir, "my-drive-${release.versionCode}.apk").absolutePath
    }

    override suspend fun install(apkPath: String) = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            error("Allow installs from my-drive, then tap Update again")
        }
        val apk = File(apkPath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
