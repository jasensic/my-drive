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
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.Builder().setLanguage("es").setRegion("ES").build()
private val localDateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanish)
private val localLongDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)

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
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(localDateTime)
}

fun formatLocalInstant(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val instant = parseInstant(value) ?: return value
    return instant.atZone(ZoneId.systemDefault()).format(localDateTime)
}

fun formatDateHeader(epochDay: Long, todayEpochDay: Long): String {
    return when (dateSectionKind(epochDay, todayEpochDay)) {
        DateSectionKind.TODAY -> "Hoy"
        DateSectionKind.YESTERDAY -> "Ayer"
        DateSectionKind.DATE -> localDateFromEpochDay(epochDay).format(localLongDate)
    }
}

private fun parseInstant(value: String): Instant? {
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
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
