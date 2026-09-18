package com.jasensic.mydrive.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.DiscoveredServer
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.LocalLibrary
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.ThemePreferences
import com.jasensic.mydrive.domain.parseMediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("mydrive")

@Entity(tableName = "local_files")
data class LocalFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mime: String,
    val mediaKind: String,
    val albumId: String?,
    val size: Long,
    val path: String,
)

@Entity(tableName = "local_albums")
data class LocalAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Dao
interface LocalFileDao {
    @Query("SELECT id FROM local_files")
    suspend fun ids(): List<String>

    @Query("SELECT * FROM local_files ORDER BY name")
    suspend fun all(): List<LocalFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalFileEntity)
}

@Dao
interface LocalAlbumDao {
    @Query("SELECT * FROM local_albums ORDER BY name")
    suspend fun all(): List<LocalAlbumEntity>

    @Query("DELETE FROM local_albums")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalAlbumEntity>)
}

@Database(entities = [LocalFileEntity::class, LocalAlbumEntity::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun files(): LocalFileDao
    abstract fun albums(): LocalAlbumDao
}

@Singleton
class RoomLocalMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDb,
) : LocalMediaStore {
    private val files = db.files()
    private val albums = db.albums()
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    override fun pathFor(fileId: String): String = File(mediaDir, fileId).absolutePath

    override suspend fun commit(file: ManifestFile, path: String) {
        files.upsert(
            LocalFileEntity(
                id = file.id,
                name = file.name,
                mime = file.mime,
                mediaKind = file.mediaKind.name.lowercase(),
                albumId = file.albumId,
                size = file.size,
                path = path,
            ),
        )
    }

    override suspend fun knownIds(): Set<String> =
        files.all().mapNotNull { row -> row.id.takeIf { File(row.path).isFile } }.toSet()

    override suspend fun library(): LocalLibrary {
        val albumRows = albums.all()
        val albumById = albumRows.associate { it.id to it.name }
        return LocalLibrary(
            albums = albumRows.map { Album(it.id, it.name) },
            files = files.all().map { row ->
                enrich(
                    LocalFile(
                        id = row.id,
                        name = row.name,
                        mime = row.mime,
                        mediaKind = parseMediaKind(row.mediaKind),
                        albumId = row.albumId,
                        albumName = row.albumId?.let { albumById[it] },
                        size = row.size,
                        path = row.path,
                    ),
                )
            },
        )
    }

    private fun enrich(file: LocalFile): LocalFile {
        val disk = File(file.path)
        val modified = if (disk.isFile) disk.lastModified() else 0L
        return when (file.mediaKind) {
            MediaKind.AUDIO, MediaKind.VIDEO -> file.copy(
                modifiedAtMillis = modified,
                artist = file.artist,
            ).let { readAvMetadata(it, modified) }
            MediaKind.PHOTO -> file.copy(
                modifiedAtMillis = photoTakenAt(disk, file.mime, modified),
                artworkPath = file.path.takeIf { disk.isFile },
            )
            MediaKind.OTHER -> file.copy(modifiedAtMillis = modified)
        }
    }

    private fun readAvMetadata(file: LocalFile, modified: Long): LocalFile {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val artPath = if (file.mediaKind == MediaKind.AUDIO) {
                retriever.embeddedPicture?.let { bytes ->
                    val art = File(mediaDir, "${file.id}.art.jpg")
                    if (!art.isFile) art.writeBytes(bytes)
                    art.absolutePath
                }
            } else {
                null
            }
            file.copy(
                artist = artist,
                albumName = file.albumName ?: album,
                durationMs = duration,
                modifiedAtMillis = modified,
                artworkPath = artPath,
            )
        } catch (_: Exception) {
            file.copy(modifiedAtMillis = modified)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun photoTakenAt(disk: File, mime: String, fallback: Long): Long {
        if (!disk.isFile) return fallback
        val jpeg = mime.contains("jpeg", ignoreCase = true) ||
            mime.contains("jpg", ignoreCase = true) ||
            disk.name.endsWith(".jpg", true) ||
            disk.name.endsWith(".jpeg", true)
        if (!jpeg) return fallback
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(disk)
            val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
            parseExifDate(raw) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseExifDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val formats = listOf(
            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
        )
        for (format in formats) {
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parsed = runCatching { format.parse(raw)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    override suspend fun replaceAlbums(albums: List<Album>) {
        this.albums.clear()
        if (albums.isNotEmpty()) {
            this.albums.upsertAll(albums.map { LocalAlbumEntity(it.id, it.name) })
        }
    }
}

@Singleton
class DataStoreSyncState @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncStateRepository {
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("username")
    private val deviceKey = stringPreferencesKey("deviceId")
    private val syncKey = stringPreferencesKey("lastSync")
    private val hostKey = stringPreferencesKey("serverHost")
    private val portKey = intPreferencesKey("serverPort")
    private val serverNameKey = stringPreferencesKey("serverName")

    override suspend fun lastSyncAt(): String? = context.dataStore.data.first()[syncKey]

    override suspend fun saveLastSyncAt(value: String) {
        context.dataStore.edit { it[syncKey] = value }
    }

    override suspend fun saveSession(session: AuthSession) {
        context.dataStore.edit {
            it[tokenKey] = session.token
            it[userKey] = session.username
            session.deviceId?.let { id -> it[deviceKey] = id }
        }
    }

    override suspend fun session(): AuthSession? {
        val prefs = context.dataStore.data.first()
        val token = prefs[tokenKey] ?: return null
        return AuthSession(token, prefs[userKey] ?: "", prefs[deviceKey])
    }

    override suspend fun saveServer(server: DiscoveredServer) {
        context.dataStore.edit {
            it[hostKey] = server.host
            it[portKey] = server.port
            it[serverNameKey] = server.name
        }
    }

    override suspend fun lastServer(): DiscoveredServer? {
        val prefs = context.dataStore.data.first()
        val host = prefs[hostKey] ?: return null
        val port = prefs[portKey] ?: return null
        return DiscoveredServer(host, port, prefs[serverNameKey] ?: host)
    }

    override suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(userKey)
            it.remove(deviceKey)
        }
    }
}

@Singleton
class DataStoreThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemePreferences {
    private val themeKey = stringPreferencesKey("themeMode")

    override fun observe(): Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            when (prefs[themeKey]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    override suspend fun set(mode: ThemeMode) {
        context.dataStore.edit {
            it[themeKey] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }
}

@Singleton
class DbProvider @Inject constructor(@ApplicationContext context: Context) {
    val db: AppDb = Room.databaseBuilder(context, AppDb::class.java, "mydrive.db")
        .fallbackToDestructiveMigration()
        .build()
}
