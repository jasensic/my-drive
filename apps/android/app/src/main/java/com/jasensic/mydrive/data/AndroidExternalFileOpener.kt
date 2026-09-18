package com.jasensic.mydrive.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jasensic.mydrive.domain.ExternalFileOpener
import com.jasensic.mydrive.domain.LocalFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidExternalFileOpener @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExternalFileOpener {
    override fun open(file: LocalFile) {
        val disk = File(file.path)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            disk,
        )
        val mime = guessMime(file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(view, file.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun guessMime(file: LocalFile): String {
        val mime = file.mime.trim()
        if (mime.isNotBlank() && mime != "application/octet-stream") return mime
        return when (file.name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "zip" -> "application/zip"
            "gz", "tgz" -> "application/gzip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "apk" -> "application/vnd.android.package-archive"
            else -> mime.ifBlank { "*/*" }
        }
    }
}
