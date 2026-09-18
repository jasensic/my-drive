package com.jasensic.mydrive.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun classifyLibrary(files: List<LocalFile>): ClassifiedLibrary {
    val music = mutableListOf<LocalFile>()
    val photos = mutableListOf<LocalFile>()
    val documents = mutableListOf<LocalFile>()
    for (file in files) {
        when (file.mediaKind) {
            MediaKind.AUDIO -> music += file
            MediaKind.PHOTO, MediaKind.VIDEO -> photos += file
            MediaKind.OTHER -> documents += file
        }
    }
    return ClassifiedLibrary(
        music = music.sortedBy { it.name.lowercase() },
        photos = photos.sortedByDescending { it.modifiedAtMillis },
        documents = documents.sortedBy { it.name.lowercase() },
    )
}

fun recentMusic(files: List<LocalFile>, limit: Int = 12): List<LocalFile> =
    files.filter { it.mediaKind == MediaKind.AUDIO }
        .sortedByDescending { it.modifiedAtMillis }
        .take(limit.coerceAtLeast(0))

fun groupMusicByAlbum(files: List<LocalFile>): List<MusicGroup> =
    groupMusic(files) { file ->
        val id = file.albumId?.takeIf { it.isNotBlank() } ?: file.displayAlbum.lowercase()
        id to file.displayAlbum
    }

fun groupMusicByArtist(files: List<LocalFile>): List<MusicGroup> =
    groupMusic(files) { file ->
        val name = file.displayArtist
        name.lowercase() to name
    }

fun groupMusicByFolder(files: List<LocalFile>): List<MusicGroup> =
    groupMusicByAlbum(files)

private fun groupMusic(
    files: List<LocalFile>,
    key: (LocalFile) -> Pair<String, String>,
): List<MusicGroup> =
    files.filter { it.mediaKind == MediaKind.AUDIO }
        .groupBy { key(it) }
        .map { (idName, tracks) ->
            val sorted = tracks.sortedBy { it.name.lowercase() }
            MusicGroup(
                id = idName.first,
                name = idName.second,
                tracks = sorted,
                artworkPath = sorted.firstNotNullOfOrNull { it.artworkPath },
            )
        }
        .sortedBy { it.name.lowercase() }

fun groupVisualMediaByDay(files: List<LocalFile>, zoneId: String): List<DatedMediaGroup> {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.of("UTC") }
    return files.filter { it.isVisual }
        .groupBy { dateOf(it.modifiedAtMillis, zone) }
        .toSortedMap(compareByDescending { it })
        .map { (date, items) ->
            DatedMediaGroup(
                epochDay = date.toEpochDay(),
                files = items.sortedByDescending { it.modifiedAtMillis },
            )
        }
}

fun dateSectionKind(epochDay: Long, todayEpochDay: Long): DateSectionKind =
    when (epochDay) {
        todayEpochDay -> DateSectionKind.TODAY
        todayEpochDay - 1L -> DateSectionKind.YESTERDAY
        else -> DateSectionKind.DATE
    }

fun localDateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

private fun dateOf(millis: Long, zone: ZoneId): LocalDate {
    val safe = if (millis > 0L) millis else 0L
    return Instant.ofEpochMilli(safe).atZone(zone).toLocalDate()
}
