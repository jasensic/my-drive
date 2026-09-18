package com.jasensic.mydrive.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.jasensic.mydrive.domain.DateSectionKind
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.dateSectionKind
import com.jasensic.mydrive.domain.localDateFromEpochDay
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--:--"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun formatModified(millis: Long): String {
    if (millis <= 0L) return ""
    val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}

fun formatDateHeader(epochDay: Long, todayEpochDay: Long): String {
    return when (dateSectionKind(epochDay, todayEpochDay)) {
        DateSectionKind.TODAY -> "Today"
        DateSectionKind.YESTERDAY -> "Yesterday"
        DateSectionKind.DATE -> localDateFromEpochDay(epochDay)
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }
}

fun fileTypeIcon(file: LocalFile): ImageVector {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val mime = file.mime.lowercase()
    return when {
        mime.contains("pdf") || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        mime.contains("zip") || ext in setOf("zip", "7z", "rar", "gz", "tgz") -> Icons.Outlined.FolderZip
        mime.startsWith("text") || ext in setOf("txt", "md", "log", "json", "xml", "csv") -> Icons.AutoMirrored.Outlined.TextSnippet
        ext in setOf("xls", "xlsx", "ods") -> Icons.Outlined.TableChart
        ext in setOf("doc", "docx", "odt") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}
