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
    files.filter { it.isSong() }
        .sortedByDescending { it.modifiedAtMillis }
        .take(limit.coerceAtLeast(0))

fun otherAudio(files: List<LocalFile>): List<LocalFile> =
    files.filter { it.mediaKind == MediaKind.AUDIO && !it.isSong() }
        .sortedBy { it.trackHeadline().lowercase() }

/** Every audio track the device can start: songs first, then other audio, local or streamable. */
fun playableAudio(files: List<LocalFile>): List<LocalFile> {
    val songs = files.filter { it.isSong() }.sortedBy { it.trackHeadline().lowercase() }
    return (songs + otherAudio(files)).filter { it.hasLocalBytes() || !it.remoteUrl.isNullOrBlank() }
}

fun groupMusicByAlbum(files: List<LocalFile>, albums: List<Album> = emptyList()): List<MusicGroup> {
    val grouped = groupMusic(files) { file ->
        val id = file.albumId?.takeIf { it.isNotBlank() } ?: file.displayAlbum.lowercase()
        id to file.displayAlbum
    }
    val byId = grouped.associateBy { it.id }
    val empty = albums
        .filter { it.silo == LibrarySilo.MUSIC && it.id !in byId }
        .map { MusicGroup(it.id, it.name, emptyList(), null) }
    return (grouped + empty).sortedBy { it.name.lowercase() }
}

fun groupMusicByArtist(files: List<LocalFile>): List<MusicGroup> =
    groupMusic(files) { file ->
        val name = file.primaryArtist().ifBlank { UNKNOWN_ARTIST }
        name.lowercase() to name
    }

fun groupMusicByFolder(files: List<LocalFile>): List<MusicGroup> =
    groupMusicByAlbum(files)

fun filesInAlbum(files: List<LocalFile>, albumId: String?): List<LocalFile> =
    if (albumId.isNullOrBlank()) files else files.filter { it.albumId == albumId }

fun albumsInSilo(albums: List<Album>, silo: LibrarySilo): List<Album> =
    albums.filter { it.silo == silo }.sortedBy { it.name.lowercase() }

fun groupFilesByAlbum(files: List<LocalFile>, albums: List<Album>, silo: LibrarySilo): List<MusicGroup> {
    val matching = files.filter { silo.contains(it.mediaKind) }
    val byId = matching.groupBy { it.albumId }
    return albumsInSilo(albums, silo).map { album ->
        val tracks = byId[album.id].orEmpty().sortedBy { it.name.lowercase() }
        MusicGroup(
            id = album.id,
            name = album.name,
            tracks = tracks,
            artworkPath = tracks.firstNotNullOfOrNull { it.artworkPath ?: it.path.takeIf { path -> silo == LibrarySilo.PHOTOS } },
        )
    }
}

private fun groupMusic(
    files: List<LocalFile>,
    key: (LocalFile) -> Pair<String, String>,
): List<MusicGroup> =
    files.filter { it.isSong() }
        .groupBy { key(it) }
        .map { (idName, tracks) ->
            val sorted = tracks.sortedBy { it.trackHeadline().lowercase() }
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
