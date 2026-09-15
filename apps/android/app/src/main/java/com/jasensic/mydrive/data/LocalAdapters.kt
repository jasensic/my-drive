package com.jasensic.mydrive.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.edit
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
import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.LocalMediaStore
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.SyncStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("mydrive")

@Entity(tableName = "local_files")
data class LocalFileEntity(@PrimaryKey val id: String, val name: String)

@Dao
interface LocalFileDao {
    @Query("SELECT id FROM local_files")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalFileEntity)
}

@Database(entities = [LocalFileEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun files(): LocalFileDao
}

@Singleton
class RoomLocalMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDb,
) : LocalMediaStore {
    private val dao = db.files()

    override suspend fun knownIds(): Set<String> = dao.ids().toSet()

    override suspend fun save(file: ManifestFile, bytes: ByteArray) {
        val collection = when {
            file.mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            file.mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            file.mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/my-drive")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(collection, values) ?: error("cannot insert ${file.name}")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        dao.upsert(LocalFileEntity(file.id, file.name))
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
}

@Singleton
class DbProvider @Inject constructor(@ApplicationContext context: Context) {
    val db: AppDb = Room.databaseBuilder(context, AppDb::class.java, "mydrive.db").build()
}
