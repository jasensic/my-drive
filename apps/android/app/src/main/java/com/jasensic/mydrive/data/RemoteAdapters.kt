package com.jasensic.mydrive.data

import com.jasensic.mydrive.domain.Album
import com.jasensic.mydrive.domain.AppRelease
import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.SyncManifest
import com.jasensic.mydrive.domain.parseMediaKind
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CredentialsDto(val username: String, val password: String)
data class AuthDto(val token: String, val user: UserDto)
data class UserDto(val id: String, val username: String)
data class NameDto(val name: String)
data class DeviceDto(val id: String, val name: String)
data class ManifestRequestDto(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "last_sync_at") val lastSyncAt: String?,
    @Json(name = "have_file_ids") val haveFileIds: List<String>,
)
data class AlbumDto(val id: String, val name: String)
data class ManifestDto(
    @Json(name = "generated_at") val generatedAt: String,
    val files: List<ManifestFileDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
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
    @POST("/v1/login")
    suspend fun login(@Body body: CredentialsDto): AuthDto

    @POST("/v1/devices")
    suspend fun registerDevice(
        @Header("Authorization") authorization: String,
        @Body body: NameDto,
    ): DeviceDto

    @POST("/v1/sync/manifest")
    suspend fun manifest(
        @Header("Authorization") authorization: String,
        @Body body: ManifestRequestDto,
    ): ManifestDto

    @GET("/v1/app/releases/latest")
    suspend fun latestRelease(
        @Header("Authorization") authorization: String,
    ): AppReleaseDto
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

    override suspend fun login(baseUrl: String, username: String, password: String): AuthSession =
        wrapAuth {
            val dto = api(baseUrl).login(CredentialsDto(username, password))
            AuthSession(dto.token, dto.user.username, null)
        }

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
            albums = dto.albums.map { Album(it.id, it.name) },
        )
    }

    override suspend fun downloadTo(url: String, token: String, destinationPath: String) {
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401) error("login required")
                if (!resp.isSuccessful) error("download failed: ${resp.code}")
                val body = resp.body ?: error("empty body")
                val dest = File(destinationPath)
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
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

    private suspend fun <T> wrapAuth(block: suspend () -> T): T =
        try {
            block()
        } catch (ex: HttpException) {
            if (ex.code() == 401) error("login required") else throw ex
        }
}
