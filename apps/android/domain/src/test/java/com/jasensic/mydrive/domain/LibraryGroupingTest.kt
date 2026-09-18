package com.jasensic.mydrive.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibraryGroupingTest {
    private val photoToday = file("p1", "today.jpg", MediaKind.PHOTO, modified = epoch("2026-09-18"))
    private val photoYesterday = file("p2", "yest.jpg", MediaKind.PHOTO, modified = epoch("2026-09-17"))
    private val videoOld = file("v1", "clip.mp4", MediaKind.VIDEO, modified = epoch("2026-08-01"))
    private val trackA = file(
        "a1",
        "Song A.mp3",
        MediaKind.AUDIO,
        albumId = "al-1",
        albumName = "Debut",
        artist = "Ada",
        modified = epoch("2026-09-18"),
        artwork = "/art/a.jpg",
    )
    private val trackB = file(
        "a2",
        "Song B.mp3",
        MediaKind.AUDIO,
        albumId = "al-1",
        albumName = "Debut",
        artist = "Ada",
        modified = epoch("2026-09-10"),
    )
    private val trackC = file(
        "a3",
        "Loose.mp3",
        MediaKind.AUDIO,
        modified = epoch("2026-09-01"),
    )
    private val pdf = file("d1", "notes.pdf", MediaKind.OTHER, modified = epoch("2026-09-18"))

    @Test
    fun classifiesByMediaKind() {
        val classified = classifyLibrary(listOf(photoToday, trackA, pdf, videoOld))
        assertEquals(listOf("a1"), classified.music.map { it.id })
        assertEquals(listOf("p1", "v1"), classified.photos.map { it.id })
        assertEquals(listOf("d1"), classified.documents.map { it.id })
    }

    @Test
    fun groupsAlbumsAndFallsBackWhenMetadataIsMissing() {
        val albums = groupMusicByAlbum(listOf(trackA, trackB, trackC))
        assertEquals(listOf("Debut", UNKNOWN_ALBUM), albums.map { it.name })
        assertEquals(listOf("a1", "a2"), albums.first().tracks.map { it.id })
        assertEquals("/art/a.jpg", albums.first().artworkPath)
        assertEquals(UNKNOWN_ALBUM, albums.last().name)
    }

    @Test
    fun groupsArtists() {
        val artists = groupMusicByArtist(listOf(trackA, trackC))
        assertEquals(listOf("Ada", UNKNOWN_ARTIST), artists.map { it.name })
        assertEquals(1, artists.first().tracks.size)
    }

    @Test
    fun recentMusicIsNewestFirst() {
        val recent = recentMusic(listOf(trackA, trackB, trackC, photoToday), limit = 2)
        assertEquals(listOf("a1", "a2"), recent.map { it.id })
    }

    @Test
    fun groupsVisualMediaByDayNewestFirst() {
        val groups = groupVisualMediaByDay(
            listOf(photoToday, photoYesterday, videoOld, trackA),
            zoneId = "UTC",
        )
        assertEquals(3, groups.size)
        assertEquals(listOf("p1"), groups[0].files.map { it.id })
        assertEquals(listOf("p2"), groups[1].files.map { it.id })
        assertEquals(listOf("v1"), groups[2].files.map { it.id })
        val today = LocalDate.of(2026, 9, 18).toEpochDay()
        assertEquals(DateSectionKind.TODAY, dateSectionKind(groups[0].epochDay, today))
        assertEquals(DateSectionKind.YESTERDAY, dateSectionKind(groups[1].epochDay, today))
        assertEquals(DateSectionKind.DATE, dateSectionKind(groups[2].epochDay, today))
    }

    @Test
    fun folderGroupingReusesAlbumMetadata() {
        val folders = groupMusicByFolder(listOf(trackA, trackC))
        assertEquals(setOf("Debut", UNKNOWN_ALBUM), folders.map { it.name }.toSet())
    }
}

class OpenLocalFileUseCaseTest {
    @Test
    fun opensExistingFile() {
        val temp = java.io.File.createTempFile("mydrive", ".pdf")
        val opener = RecordingOpener()
        OpenLocalFileUseCase(opener).execute(
            file("d1", "notes.pdf", MediaKind.OTHER, path = temp.absolutePath),
        )
        assertEquals("d1", opener.opened.single().id)
        temp.delete()
    }

    @Test
    fun rejectsMissingLocalFile() {
        val opener = RecordingOpener()
        val err = assertFailsWith<IllegalStateException> {
            OpenLocalFileUseCase(opener).execute(
                file("d1", "notes.pdf", MediaKind.OTHER, path = "/missing/notes.pdf"),
            )
        }
        assertTrue(err.message!!.contains("Sync first"))
        assertTrue(opener.opened.isEmpty())
    }
}

private class RecordingOpener : ExternalFileOpener {
    val opened = mutableListOf<LocalFile>()
    override fun open(file: LocalFile) {
        opened += file
    }
}

private fun epoch(isoDate: String): Long =
    LocalDate.parse(isoDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun file(
    id: String,
    name: String,
    kind: MediaKind,
    albumId: String? = null,
    albumName: String? = null,
    artist: String? = null,
    modified: Long = 0L,
    artwork: String? = null,
    path: String = "/tmp/$name",
) = LocalFile(
    id = id,
    name = name,
    mime = "application/octet-stream",
    mediaKind = kind,
    albumId = albumId,
    albumName = albumName,
    size = 10,
    path = path,
    artist = artist,
    durationMs = 1_000,
    modifiedAtMillis = modified,
    artworkPath = artwork,
)
