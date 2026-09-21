package com.jasensic.mydrive.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.content.Context
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.MediaSharer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaSharer @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSharer {
    override fun share(files: List<LocalFile>) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.size)
        files.forEach { file ->
            uris += FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(file.path),
            )
        }
        val mime = commonMime(files)
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(send, if (files.size == 1) files.first().name else "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun commonMime(files: List<LocalFile>): String {
        val types = files.map { it.mime.ifBlank { "*/*" } }.distinct()
        if (types.size == 1) return types.first()
        val prefix = types.map { it.substringBefore('/') }.distinct()
        return if (prefix.size == 1) "${prefix.first()}/*" else "*/*"
    }
}
