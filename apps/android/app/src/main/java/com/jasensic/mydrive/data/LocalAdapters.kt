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
import com.jasensic.mydrive.domain.SyncStateRepository
import com.jasensic.mydrive.domain.parseMediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    override suspend fun knownIds(): Set<String> = files.ids().toSet()

    override suspend fun library(): LocalLibrary {
        val albumRows = albums.all()
        val albumById = albumRows.associate { it.id to it.name }
        return LocalLibrary(
            albums = albumRows.map { Album(it.id, it.name) },
            files = files.all().map { row ->
                LocalFile(
                    id = row.id,
                    name = row.name,
                    mime = row.mime,
                    mediaKind = parseMediaKind(row.mediaKind),
                    albumId = row.albumId,
                    albumName = row.albumId?.let { albumById[it] },
                    size = row.size,
                    path = row.path,
                )
            },
        )
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
class DbProvider @Inject constructor(@ApplicationContext context: Context) {
    val db: AppDb = Room.databaseBuilder(context, AppDb::class.java, "mydrive.db")
        .fallbackToDestructiveMigration()
        .build()
}
