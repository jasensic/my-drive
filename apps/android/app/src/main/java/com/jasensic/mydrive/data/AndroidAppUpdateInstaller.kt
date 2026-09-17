package com.jasensic.mydrive.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AppUpdateInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class AndroidAppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppUpdateInstaller {
    override fun apkPath(release: AppRelease): String {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        return File(dir, "my-drive-${release.versionCode}.apk").absolutePath
    }

    override suspend fun install(apkPath: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            error("Allow installs from my-drive, then tap Update again")
        }
        val apk = File(apkPath)
        require(apk.isFile && apk.length() > 0L) { "downloaded APK is missing: $apkPath" }
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val digest = sha256(apk)
        val header = buildString {
            appendLine("package=${context.packageName}")
            appendLine("apk=$apkPath")
            appendLine("size=${apk.length()}")
            appendLine("sha256=$digest")
            appendLine("installedVersionName=${current?.versionName}")
            appendLine("installedVersionCode=${installedVersionCode(current)}")
        }
        logFile(apk.parentFile).writeText(header)
        Log.i(TAG, header.replace('\n', ' '))

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        val done = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val legacy = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", 0)
                Log.i(TAG, "installer status=$status legacy=$legacy message=$message")
                logFile(apk.parentFile).appendText("status=$status legacy=$legacy message=$message\n")
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(confirm)
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> done.complete(Unit)
                    else -> done.completeExceptionally(
                        IllegalStateException(humanStatus(status, message, legacy)),
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_INSTALL_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val sender = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName),
                    flags,
                ).intentSender
                session.commit(sender)
            }
            withTimeout(10 * 60 * 1000L) { done.await() }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun installedVersionCode(info: android.content.pm.PackageInfo?): Long {
        if (info == null) return -1
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun humanStatus(status: Int, message: String, legacy: Int): String {
        val reason = when (status) {
            PackageInstaller.STATUS_FAILURE -> "generic failure"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "aborted"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked"
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            ->
                "signing key mismatch. Uninstall my-drive, then install this APK. Later updates must use the same keystore."
            PackageInstaller.STATUS_FAILURE_INVALID -> "invalid APK"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "not enough storage"
            else -> "status $status"
        }
        return "Install failed: $reason. $message (legacy=$legacy). See logcat tag $TAG or files/updates/last-install.log"
    }

    private fun logFile(dir: File?) = File(dir ?: context.filesDir, "last-install.log")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "MyDriveUpdate"
        const val ACTION_INSTALL_STATUS = "com.jasensic.mydrive.INSTALL_STATUS"
    }
}
