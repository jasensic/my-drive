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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.DeviceExclusionStore
import com.jasensic.mydrive.domain.DiscoveredServer
import com.jasensic.mydrive.domain.LocalFile
import com.jasensic.mydrive.domain.LocalLibrary
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.MediaKind
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.ThemeMode
import com.jasensic.mydrive.domain.ThemePreferences
import com.jasensic.mydrive.domain.parseLibrarySilo
import com.jasensic.mydrive.domain.parseMediaKind
import com.jasensic.mydrive.domain.wireValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    val artist: String? = null,
    val albumArtist: String? = null,
    val title: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long? = null,
    val modifiedAtMillis: Long = 0L,
    val artworkPath: String? = null,
    val metadataReady: Boolean = false,
)

@Entity(tableName = "local_albums")
data class LocalAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val silo: String = "photos",
)

@Dao
interface LocalFileDao {
    @Query("SELECT id FROM local_files")
    suspend fun ids(): List<String>

    @Query("SELECT * FROM local_files ORDER BY name")
    suspend fun all(): List<LocalFileEntity>

    @Query("SELECT * FROM local_files WHERE id = :id")
    suspend fun byId(id: String): LocalFileEntity?

    @Query("SELECT * FROM local_files WHERE metadataReady = 0")
    suspend fun pendingMetadata(): List<LocalFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalFileEntity)

    @Query(
        "UPDATE local_files SET artist = :artist, albumArtist = :albumArtist, title = :title, " +
            "albumTitle = :albumTitle, durationMs = :durationMs, modifiedAtMillis = :modifiedAtMillis, " +
            "artworkPath = :artworkPath, metadataReady = 1 WHERE id = :id",
    )
    suspend fun saveMetadata(
        id: String,
        artist: String?,
        albumArtist: String?,
        title: String?,
        albumTitle: String?,
        durationMs: Long?,
        modifiedAtMillis: Long,
        artworkPath: String?,
    )

    @Query("UPDATE local_files SET artworkPath = :artworkPath WHERE id = :id")
    suspend fun saveArtwork(id: String, artworkPath: String)

    @Query("UPDATE local_files SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE local_files SET albumId = :albumId WHERE id = :id")
    suspend fun assignAlbum(id: String, albumId: String?)

    @Query("DELETE FROM local_files WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("UPDATE local_files SET albumId = NULL WHERE albumId = :albumId")
    suspend fun clearAlbum(albumId: String)
}

@Dao
interface LocalAlbumDao {
    @Query("SELECT * FROM local_albums ORDER BY name")
    suspend fun all(): List<LocalAlbumEntity>

    @Query("DELETE FROM local_albums")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalAlbumEntity)

    @Query("DELETE FROM local_albums WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [LocalFileEntity::class, LocalAlbumEntity::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun files(): LocalFileDao
    abstract fun albums(): LocalAlbumDao
}

@Singleton
class RoomLocalMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalMediaStore {
    private val lock = Any()
    private var userKey: String? = null
    private var db: AppDb? = null
    private var mediaDir: File = File(context.filesDir, "media")

    override fun bindUser(userId: String?) {
        synchronized(lock) {
            if (userId == null) {
                db?.close()
                db = null
                userKey = null
                mediaDir = File(context.filesDir, "media-unbound")
                return
            }
            val key = userId.ifBlank { "legacy" }
            if (key == userKey && db != null) return
            db?.close()
            adoptLegacyIfNeeded(key)
            userKey = key
            db = openDb(key)
            mediaDir = mediaDirFor(key).apply { mkdirs() }
        }
    }

    override fun pathFor(fileId: String): String = File(mediaDir, fileId).absolutePath

    override fun artworkPathFor(fileId: String): String = File(mediaDir, "$fileId.art.jpg").absolutePath

    override fun cachePathFor(fileId: String): String =
        File(context.cacheDir, "stream/$fileId").absolutePath

    private fun files(): LocalFileDao = requireDb().files()
    private fun albums(): LocalAlbumDao = requireDb().albums()

    private fun requireDb(): AppDb = synchronized(lock) {
        db ?: error("local library is not bound to a user")
    }

    private fun currentDb(): AppDb? = synchronized(lock) { db }

    private fun openDb(key: String): AppDb =
        Room.databaseBuilder(context, AppDb::class.java, dbName(key))
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    private fun dbName(key: String) = if (key == "legacy") "mydrive.db" else "mydrive-$key.db"

    private fun mediaDirFor(key: String): File =
        if (key == "legacy") File(context.filesDir, "media")
        else File(context.filesDir, "users/$key/media")

    private fun adoptLegacyIfNeeded(key: String) {
        if (key == "legacy") return
        val targetDb = context.getDatabasePath(dbName(key))
        val legacyDb = context.getDatabasePath("mydrive.db")
        if (!targetDb.exists() && legacyDb.exists()) {
            legacyDb.renameTo(targetDb)
            File(legacyDb.path + "-wal").takeIf { it.exists() }?.renameTo(File(targetDb.path + "-wal"))
            File(legacyDb.path + "-shm").takeIf { it.exists() }?.renameTo(File(targetDb.path + "-shm"))
        }
        val targetMedia = mediaDirFor(key)
        val legacyMedia = File(context.filesDir, "media")
        if (!targetMedia.exists() && legacyMedia.isDirectory) {
            val files = legacyMedia.listFiles()?.filter { it.isFile }.orEmpty()
            if (files.isNotEmpty()) {
                targetMedia.mkdirs()
                files.forEach { it.renameTo(File(targetMedia, it.name)) }
            }
        }
    }

    override suspend fun commit(file: ManifestFile, path: String) {
        val existing = files().byId(file.id)
        val disk = File(path)
        files().upsert(
            LocalFileEntity(
                id = file.id,
                name = file.name,
                mime = file.mime,
                mediaKind = file.mediaKind.name.lowercase(),
                albumId = file.albumId,
                size = file.size,
                path = path,
                artist = existing?.artist,
                albumArtist = existing?.albumArtist,
                title = existing?.title,
                albumTitle = existing?.albumTitle,
                durationMs = existing?.durationMs,
                modifiedAtMillis = existing?.modifiedAtMillis?.takeIf { it > 0L }
                    ?: disk.takeIf { it.isFile }?.lastModified() ?: 0L,
                artworkPath = existing?.artworkPath?.takeIf { File(it).isFile },
                metadataReady = existing?.metadataReady == true,
            ),
        )
    }

    override suspend fun knownIds(): Set<String> {
        val dao = currentDb()?.files() ?: return emptySet()
        return dao.all().mapNotNull { row -> row.id.takeIf { File(row.path).isFile } }.toSet()
    }

    override suspend fun library(): LocalLibrary {
        val database = currentDb() ?: return LocalLibrary(emptyList(), emptyList())
        val albumRows = database.albums().all()
        val albumById = albumRows.associate { it.id to it.name }
        return LocalLibrary(
            albums = albumRows.map { Album(it.id, it.name, parseLibrarySilo(it.silo)) },
            files = database.files().all().map { row -> toLocalFile(row, albumById) },
        )
    }

    override suspend fun enrichMetadata(): LocalLibrary = withContext(Dispatchers.IO) {
        val database = currentDb() ?: return@withContext LocalLibrary(emptyList(), emptyList())
        val albumById = database.albums().all().associate { it.id to it.name }
        for (row in database.files().pendingMetadata()) {
            persistDecodedMetadata(toLocalFile(row, albumById))
        }
        library()
    }

    override suspend fun captureEmbeddedArtwork(fileId: String): String? = withContext(Dispatchers.IO) {
        val row = files().byId(fileId) ?: return@withContext null
        val albumById = albums().all().associate { it.id to it.name }
        val updated = persistDecodedMetadata(toLocalFile(row, albumById))
        updated.artworkPath?.takeIf { File(it).isFile && File(it).length() > 0L }
    }

    override suspend fun rememberArtwork(fileId: String, artworkPath: String) {
        if (File(artworkPath).isFile && File(artworkPath).length() > 0L) {
            files().saveArtwork(fileId, artworkPath)
        }
    }

    private fun toLocalFile(row: LocalFileEntity, albumById: Map<String, String>): LocalFile {
        val disk = File(row.path)
        val kind = parseMediaKind(row.mediaKind)
        val onDevice = disk.isFile
        val modified = when {
            row.modifiedAtMillis > 0L -> row.modifiedAtMillis
            onDevice -> disk.lastModified()
            else -> 0L
        }
        val cachedArt = row.artworkPath?.takeIf { File(it).isFile }
        val sidecar = File(mediaDir, "${row.id}.art.jpg").takeIf { it.isFile && it.length() > 0L }?.absolutePath
        return LocalFile(
            id = row.id,
            name = row.name,
            mime = row.mime,
            mediaKind = kind,
            albumId = row.albumId,
            albumName = row.albumId?.let { albumById[it] } ?: row.albumTitle,
            size = row.size,
            path = row.path,
            artist = row.artist,
            albumArtist = row.albumArtist,
            title = row.title,
            durationMs = row.durationMs,
            modifiedAtMillis = modified,
            artworkPath = cachedArt ?: sidecar ?: row.path.takeIf { kind == MediaKind.PHOTO && onDevice },
            onDevice = onDevice,
        )
    }

    private suspend fun persistDecodedMetadata(file: LocalFile): LocalFile {
        val disk = File(file.path)
        val modified = if (disk.isFile) disk.lastModified() else file.modifiedAtMillis
        val decoded = when (file.mediaKind) {
            MediaKind.AUDIO, MediaKind.VIDEO -> readAvMetadata(file, modified)
            MediaKind.PHOTO -> file.copy(
                modifiedAtMillis = photoTakenAt(disk, file.mime, modified),
                artworkPath = file.path.takeIf { disk.isFile },
            )
            MediaKind.OTHER -> file.copy(modifiedAtMillis = modified)
        }
        files().saveMetadata(
            id = decoded.id,
            artist = decoded.artist,
            albumArtist = decoded.albumArtist,
            title = decoded.title,
            albumTitle = decoded.albumName,
            durationMs = decoded.durationMs,
            modifiedAtMillis = decoded.modifiedAtMillis,
            artworkPath = decoded.artworkPath,
        )
        return decoded
    }

    private fun readAvMetadata(file: LocalFile, modified: Long): LocalFile {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            val albumArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val sidecar = File(mediaDir, "${file.id}.art.jpg")
            val artPath = if (file.mediaKind == MediaKind.AUDIO) {
                retriever.embeddedPicture?.let { bytes ->
                    if (!sidecar.isFile || sidecar.length() == 0L) sidecar.writeBytes(bytes)
                    sidecar.absolutePath
                } ?: sidecar.takeIf { it.isFile && it.length() > 0L }?.absolutePath
                    ?: file.artworkPath?.takeIf { File(it).isFile }
            } else {
                file.artworkPath
            }
            file.copy(
                title = title ?: file.title,
                artist = artist ?: file.artist,
                albumArtist = albumArtist ?: file.albumArtist,
                albumName = file.albumName ?: album,
                durationMs = duration ?: file.durationMs,
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

    override suspend fun replaceAlbums(items: List<Album>) {
        albums().clear()
        if (items.isNotEmpty()) {
            albums().upsertAll(items.map { LocalAlbumEntity(it.id, it.name, it.silo.wireValue()) })
        }
    }

    override suspend fun upsertAlbum(album: Album) {
        albums().upsert(LocalAlbumEntity(album.id, album.name, album.silo.wireValue()))
    }

    override suspend fun deleteAlbum(id: String) {
        files().clearAlbum(id)
        albums().delete(id)
    }

    override suspend fun renameFile(id: String, name: String) {
        files().rename(id, name)
    }

    override suspend fun assignAlbum(id: String, albumId: String?) {
        files().assignAlbum(id, albumId)
    }

    override suspend fun removeFiles(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val list = ids.toList()
        files().all().filter { it.id in ids }.forEach { row ->
            File(row.path).delete()
            File(mediaDir, "${row.id}.art.jpg").delete()
            row.artworkPath?.let { File(it).takeIf { file -> file.absolutePath != row.path }?.delete() }
        }
        files().deleteIds(list)
    }
}

@Singleton
class DataStoreSyncState @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncStateRepository {
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("username")
    private val userIdKey = stringPreferencesKey("userId")
    private val deviceKey = stringPreferencesKey("deviceId")
    private val syncKey = stringPreferencesKey("lastSync")
    private val hostKey = stringPreferencesKey("serverHost")
    private val portKey = intPreferencesKey("serverPort")
    private val serverNameKey = stringPreferencesKey("serverName")
    private val baseUrlKey = stringPreferencesKey("serverBaseUrl")

    @Volatile private var tokenCache: String? = null

    override fun cachedToken(): String? = tokenCache

    override suspend fun lastSyncAt(): String? {
        val prefs = context.dataStore.data.first()
        val userId = prefs[userIdKey]
        if (!userId.isNullOrBlank()) {
            return prefs[stringPreferencesKey("lastSync_$userId")] ?: prefs[syncKey]
        }
        return prefs[syncKey]
    }

    override suspend fun saveLastSyncAt(value: String) {
        context.dataStore.edit {
            val userId = it[userIdKey]
            if (!userId.isNullOrBlank()) {
                it[stringPreferencesKey("lastSync_$userId")] = value
            }
            it[syncKey] = value
        }
    }

    override suspend fun saveSession(session: AuthSession) {
        tokenCache = session.token
        val userId = session.userId
        val deviceId = session.deviceId
        context.dataStore.edit {
            it[tokenKey] = session.token
            it[userKey] = session.username
            if (!userId.isNullOrBlank()) {
                it[userIdKey] = userId
                if (deviceId != null) {
                    it[deviceKey] = deviceId
                    it[stringPreferencesKey("device_$userId")] = deviceId
                }
            } else {
                it.remove(userIdKey)
                if (deviceId != null) {
                    it[deviceKey] = deviceId
                }
            }
        }
    }

    override suspend fun session(): AuthSession? {
        val prefs = context.dataStore.data.first()
        val token = prefs[tokenKey]
        if (token == null) {
            tokenCache = null
            return null
        }
        tokenCache = token
        val userId = prefs[userIdKey]
        val device = prefs[deviceKey] ?: userId?.let { prefs[stringPreferencesKey("device_$it")] }
        return AuthSession(token, prefs[userKey] ?: "", device, userId)
    }

    override suspend fun rememberedDeviceId(userId: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("device_$userId")]

    override suspend fun saveServer(server: DiscoveredServer) {
        context.dataStore.edit {
            it[hostKey] = server.host
            it[portKey] = server.port
            it[serverNameKey] = server.name
            it[baseUrlKey] = server.baseUrl
        }
    }

    override suspend fun lastServer(): DiscoveredServer? {
        val prefs = context.dataStore.data.first()
        val host = prefs[hostKey] ?: return null
        val port = prefs[portKey] ?: return null
        return DiscoveredServer(
            host,
            port,
            prefs[serverNameKey] ?: host,
            advertisedUrl = prefs[baseUrlKey],
        )
    }

    override suspend fun clearSession() {
        tokenCache = null
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(userKey)
            it.remove(deviceKey)
            it.remove(userIdKey)
        }
    }
}

@Singleton
class DataStoreDeviceExclusions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val state: SyncStateRepository,
) : DeviceExclusionStore {
    override suspend fun excludedIds(): Set<String> = read("excluded")

    override suspend fun pendingIds(): Set<String> = read("pending")

    override suspend fun enqueue(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val userId = state.session()?.userId
        context.dataStore.edit {
            it[key("excluded", userId)] = encode(decode(it[key("excluded", userId)]) + ids)
            it[key("pending", userId)] = encode(decode(it[key("pending", userId)]) + ids)
        }
    }

    override suspend fun markFlushed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val userId = state.session()?.userId
        context.dataStore.edit {
            it[key("pending", userId)] = encode(decode(it[key("pending", userId)]) - ids.toSet())
        }
    }

    private suspend fun read(prefix: String): Set<String> {
        val prefs = context.dataStore.data.first()
        return decode(prefs[key(prefix, state.session()?.userId)])
    }

    private fun key(prefix: String, userId: String?) =
        stringPreferencesKey("${prefix}_${userId ?: "legacy"}")

    private fun decode(raw: String?): Set<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun encode(ids: Collection<String>): String =
        ids.toSet().sorted().joinToString(",")
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE local_files ADD COLUMN artist TEXT")
        database.execSQL("ALTER TABLE local_files ADD COLUMN albumArtist TEXT")
        database.execSQL("ALTER TABLE local_files ADD COLUMN title TEXT")
        database.execSQL("ALTER TABLE local_files ADD COLUMN albumTitle TEXT")
        database.execSQL("ALTER TABLE local_files ADD COLUMN durationMs INTEGER")
        database.execSQL("ALTER TABLE local_files ADD COLUMN modifiedAtMillis INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE local_files ADD COLUMN artworkPath TEXT")
        database.execSQL("ALTER TABLE local_files ADD COLUMN metadataReady INTEGER NOT NULL DEFAULT 0")
    }
}
