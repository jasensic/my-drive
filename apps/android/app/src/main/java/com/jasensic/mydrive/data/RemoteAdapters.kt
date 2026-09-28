package com.jasensic.mydrive.data

import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.RemoteFile
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.ServerStatus
import com.jasensic.mydrive.domain.ShareGrant
import com.jasensic.mydrive.domain.SharePermission
import com.jasensic.mydrive.domain.ShareResourceType
import com.jasensic.mydrive.domain.SyncManifest
import com.jasensic.mydrive.domain.LibrarySilo
import com.jasensic.mydrive.domain.UserProfile
import com.jasensic.mydrive.domain.parseLibrarySilo
import com.jasensic.mydrive.domain.parseMediaKind
import com.jasensic.mydrive.domain.parseResourceAccess
import com.jasensic.mydrive.domain.parseSharePermission
import com.jasensic.mydrive.domain.parseShareResourceType
import com.jasensic.mydrive.domain.wireValue
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StatusDto(@Json(name = "setup_required") val setupRequired: Boolean = false)
data class CredentialsDto(val username: String, val password: String)
data class AuthDto(val token: String, val user: UserDto)
data class UserDto(val id: String, val username: String)
data class NameDto(val name: String)
data class DeviceDto(val id: String, val name: String)
data class DeviceExclusionsRequestDto(@Json(name = "file_ids") val fileIds: List<String>)
data class DeviceExclusionsDto(@Json(name = "file_ids") val fileIds: List<String> = emptyList())
data class ManifestRequestDto(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "last_sync_at") val lastSyncAt: String?,
    @Json(name = "have_file_ids") val haveFileIds: List<String>,
)
data class AlbumDto(
    val id: String,
    val name: String,
    val silo: String = "photos",
    val shared: Boolean = false,
    val access: String = "owner",
)
data class CreateAlbumDto(val name: String, val silo: String)
data class FileDto(
    val id: String,
    val name: String,
    val size: Long = 0,
    val mime: String = "application/octet-stream",
    val checksum: String = "",
    @Json(name = "media_kind") val mediaKind: String = "other",
    @Json(name = "album_id") val albumId: String? = null,
    @Json(name = "content_url") val contentUrl: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val shared: Boolean = false,
    val access: String = "owner",
)
data class ShareDto(
    val id: String,
    @Json(name = "resource_type") val resourceType: String = "file",
    @Json(name = "resource_id") val resourceId: String = "",
    @Json(name = "owner_id") val ownerId: String = "",
    @Json(name = "grantee_id") val granteeId: String = "",
    @Json(name = "grantee_username") val granteeUsername: String = "",
    val permission: String = "read",
)
data class CreateShareDto(
    @Json(name = "resource_type") val resourceType: String,
    @Json(name = "resource_id") val resourceId: String,
    @Json(name = "grantee_id") val granteeId: String,
    val permission: String,
)
data class ManifestDto(
    @Json(name = "generated_at") val generatedAt: String,
    val files: List<ManifestFileDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val removed: List<String> = emptyList(),
)
data class ManifestFileDto(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val checksum: String,
    val url: String,
    @Json(name = "media_kind") val mediaKind: String = "other",
    @Json(name = "album_id") val albumId: String? = null,
)
data class AppReleaseDto(
    val id: String,
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "version_name") val versionName: String,
    val changelog: String = "",
    val checksum: String = "",
    val size: Long = 0,
    @Json(name = "download_url") val downloadUrl: String,
)

interface DriveApi {
    @GET("/v1/status")
    suspend fun status(): StatusDto

    @POST("/v1/setup")
    suspend fun setup(@Body body: CredentialsDto): AuthDto

    @POST("/v1/register")
    suspend fun register(@Body body: CredentialsDto): AuthDto

    @POST("/v1/login")
    suspend fun login(@Body body: CredentialsDto): AuthDto

    @GET("/v1/users")
    suspend fun listUsers(@Header("Authorization") authorization: String): List<UserDto>

    @POST("/v1/devices")
    suspend fun registerDevice(
        @Header("Authorization") authorization: String,
        @Body body: NameDto,
    ): DeviceDto

    @PUT("/v1/devices/{id}/exclusions")
    suspend fun putExclusions(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body body: DeviceExclusionsRequestDto,
    ): DeviceExclusionsDto

    @POST("/v1/sync/manifest")
    suspend fun manifest(
        @Header("Authorization") authorization: String,
        @Body body: ManifestRequestDto,
    ): ManifestDto

    @GET("/v1/app/releases/latest")
    suspend fun latestRelease(
        @Header("Authorization") authorization: String,
    ): AppReleaseDto

    @GET("/v1/albums")
    suspend fun listAlbums(
        @Header("Authorization") authorization: String,
        @Query("silo") silo: String?,
    ): List<AlbumDto>

    @GET("/v1/files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Query("silo") silo: String?,
    ): List<FileDto>

    @POST("/v1/albums")
    suspend fun createAlbum(
        @Header("Authorization") authorization: String,
        @Body body: CreateAlbumDto,
    ): AlbumDto

    @PATCH("/v1/albums/{id}")
    suspend fun renameAlbum(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Body body: NameDto,
    ): AlbumDto

    @DELETE("/v1/albums/{id}")
    suspend fun deleteAlbum(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )

    @GET("/v1/shares")
    suspend fun listShares(
        @Header("Authorization") authorization: String,
        @Query("resource_type") resourceType: String?,
        @Query("resource_id") resourceId: String?,
    ): List<ShareDto>

    @POST("/v1/shares")
    suspend fun createShare(
        @Header("Authorization") authorization: String,
        @Body body: CreateShareDto,
    ): ShareDto

    @DELETE("/v1/shares/{id}")
    suspend fun deleteShare(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
    )
}

@Singleton
class RetrofitRemoteFileSource @Inject constructor() : RemoteFileSource {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun api(baseUrl: String): DriveApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DriveApi::class.java)

    override suspend fun serverStatus(baseUrl: String): ServerStatus =
        wrapAuth { ServerStatus(api(baseUrl).status().setupRequired) }

    override suspend fun setup(baseUrl: String, username: String, password: String): AuthSession =
        wrapAuth { api(baseUrl).setup(CredentialsDto(username, password)).toSession() }

    override suspend fun register(baseUrl: String, username: String, password: String): AuthSession =
        wrapAuth { api(baseUrl).register(CredentialsDto(username, password)).toSession() }

    override suspend fun login(baseUrl: String, username: String, password: String): AuthSession =
        wrapAuth { api(baseUrl).login(CredentialsDto(username, password)).toSession() }

    override suspend fun listUsers(baseUrl: String, token: String): List<UserProfile> =
        wrapAuth { api(baseUrl).listUsers("Bearer $token").map { UserProfile(it.id, it.username) } }

    override suspend fun registerDevice(baseUrl: String, token: String, name: String): String =
        wrapAuth { api(baseUrl).registerDevice("Bearer $token", NameDto(name)).id }

    override suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ): SyncManifest = wrapAuth {
        val dto = api(baseUrl).manifest(
            "Bearer $token",
            ManifestRequestDto(deviceId, lastSyncAt, haveFileIds.toList()),
        )
        SyncManifest(
            generatedAt = dto.generatedAt,
            files = dto.files.map {
                ManifestFile(
                    id = it.id,
                    name = it.name,
                    size = it.size,
                    mime = it.mime,
                    checksum = it.checksum,
                    url = it.url,
                    mediaKind = parseMediaKind(it.mediaKind),
                    albumId = it.albumId,
                )
            },
            albums = dto.albums.map { it.toAlbum() },
            removed = dto.removed,
        )
    }

    override suspend fun downloadTo(
        url: String,
        token: String,
        destinationPath: String,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)?,
    ) {
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401) error("login required")
                if (!resp.isSuccessful) error("download failed: ${resp.code}")
                val body = resp.body ?: error("empty body")
                val contentLength = body.contentLength()
                val dest = File(destinationPath)
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            totalRead += read
                            onProgress?.invoke(totalRead, contentLength)
                        }
                    }
                }
            }
        }
    }

    override suspend fun latestAppRelease(baseUrl: String, token: String): AppRelease? =
        try {
            val dto = api(baseUrl).latestRelease("Bearer $token")
            AppRelease(
                id = dto.id,
                versionCode = dto.versionCode,
                versionName = dto.versionName,
                changelog = dto.changelog,
                checksum = dto.checksum,
                size = dto.size,
                downloadUrl = dto.downloadUrl,
            )
        } catch (ex: HttpException) {
            if (ex.code() == 404) null else if (ex.code() == 401) error("login required") else throw ex
        }

    override suspend fun createAlbum(baseUrl: String, token: String, name: String, silo: LibrarySilo): Album =
        wrapAuth {
            val dto = api(baseUrl).createAlbum("Bearer $token", CreateAlbumDto(name, silo.wireValue()))
            Album(dto.id, dto.name, parseLibrarySilo(dto.silo), dto.shared, parseResourceAccess(dto.access))
        }

    override suspend fun renameAlbum(baseUrl: String, token: String, id: String, name: String): Album =
        wrapAuth {
            val dto = api(baseUrl).renameAlbum("Bearer $token", id, NameDto(name))
            Album(dto.id, dto.name, parseLibrarySilo(dto.silo), dto.shared, parseResourceAccess(dto.access))
        }

    override suspend fun deleteAlbum(baseUrl: String, token: String, id: String) {
        wrapAuth { api(baseUrl).deleteAlbum("Bearer $token", id) }
    }

    override suspend fun updateFile(
        baseUrl: String,
        token: String,
        id: String,
        name: String?,
        albumId: String?,
        clearAlbum: Boolean,
    ): ManifestFile = wrapAuth {
        val json = org.json.JSONObject()
        if (name != null) json.put("name", name)
        when {
            clearAlbum -> json.put("album_id", org.json.JSONObject.NULL)
            albumId != null -> json.put("album_id", albumId)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(absoluteApi(baseUrl, "/v1/files/$id"))
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) error("login required")
            if (!resp.isSuccessful) error(resp.body?.string()?.ifBlank { "update failed: ${resp.code}" } ?: "update failed: ${resp.code}")
            val payload = resp.body?.string() ?: error("empty body")
            val dto = moshi.adapter(FileDto::class.java).fromJson(payload) ?: error("invalid file")
            ManifestFile(
                id = dto.id,
                name = dto.name,
                size = dto.size,
                mime = dto.mime,
                checksum = dto.checksum,
                url = dto.contentUrl,
                mediaKind = parseMediaKind(dto.mediaKind),
                albumId = dto.albumId,
            )
        }
    }

    override suspend fun listAlbums(baseUrl: String, token: String, silo: LibrarySilo?): List<Album> =
        wrapAuth {
            api(baseUrl).listAlbums("Bearer $token", silo?.wireValue()).map { it.toAlbum() }
        }

    override suspend fun listFiles(baseUrl: String, token: String, silo: LibrarySilo?): List<RemoteFile> =
        wrapAuth {
            api(baseUrl).listFiles("Bearer $token", silo?.wireValue()).map { it.toRemoteFile() }
        }

    override suspend fun mergeDeviceExclusions(
        baseUrl: String,
        token: String,
        deviceId: String,
        fileIds: Collection<String>,
    ): Set<String> = wrapAuth {
        api(baseUrl).putExclusions(
            "Bearer $token",
            deviceId,
            DeviceExclusionsRequestDto(fileIds.toList()),
        ).fileIds.toSet()
    }

    override suspend fun listShares(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType?,
        resourceId: String?,
    ): List<ShareGrant> = wrapAuth {
        api(baseUrl).listShares(
            "Bearer $token",
            resourceType?.wireValue(),
            resourceId,
        ).map { it.toGrant() }
    }

    override suspend fun createShare(
        baseUrl: String,
        token: String,
        resourceType: ShareResourceType,
        resourceId: String,
        granteeId: String,
        permission: SharePermission,
    ): ShareGrant = wrapAuth {
        api(baseUrl).createShare(
            "Bearer $token",
            CreateShareDto(
                resourceType = resourceType.wireValue(),
                resourceId = resourceId,
                granteeId = granteeId,
                permission = permission.wireValue(),
            ),
        ).toGrant()
    }

    override suspend fun deleteShare(baseUrl: String, token: String, shareId: String) {
        wrapAuth { api(baseUrl).deleteShare("Bearer $token", shareId) }
    }

    private fun absoluteApi(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + path

    private suspend fun <T> wrapAuth(block: suspend () -> T): T =
        try {
            block()
        } catch (ex: HttpException) {
            if (ex.code() == 401) error("login required") else throw ex
        }
}

private fun AuthDto.toSession() = AuthSession(token, user.username, null, user.id)

private fun AlbumDto.toAlbum() =
    Album(id, name, parseLibrarySilo(silo), shared, parseResourceAccess(access))

private fun FileDto.toRemoteFile() = RemoteFile(
    id = id,
    name = name,
    size = size,
    mime = mime,
    checksum = checksum,
    mediaKind = parseMediaKind(mediaKind),
    albumId = albumId,
    contentUrl = contentUrl.ifBlank { "/v1/files/$id/content" },
    thumbnailUrl = thumbnailUrl,
    shared = shared,
    access = parseResourceAccess(access),
    createdAt = createdAt,
)

private fun ShareDto.toGrant() = ShareGrant(
    id = id,
    resourceType = parseShareResourceType(resourceType),
    resourceId = resourceId,
    ownerId = ownerId,
    granteeId = granteeId,
    granteeUsername = granteeUsername,
    permission = parseSharePermission(permission),
)
