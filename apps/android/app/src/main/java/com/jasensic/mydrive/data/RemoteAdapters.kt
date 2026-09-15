package com.jasensic.mydrive.data

import com.jasensic.mydrive.domain.AuthSession
import com.jasensic.mydrive.domain.ManifestFile
import com.jasensic.mydrive.domain.RemoteFileSource
import com.jasensic.mydrive.domain.SyncManifest
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
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
data class ManifestDto(
    @Json(name = "generated_at") val generatedAt: String,
    val files: List<ManifestFileDto>,
)
data class ManifestFileDto(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val checksum: String,
    val url: String,
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
}

@Singleton
class RetrofitRemoteFileSource @Inject constructor() : RemoteFileSource {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val http = OkHttpClient()

    private fun api(baseUrl: String): DriveApi =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DriveApi::class.java)

    override suspend fun login(baseUrl: String, username: String, password: String): AuthSession {
        val dto = api(baseUrl).login(CredentialsDto(username, password))
        return AuthSession(dto.token, dto.user.username, null)
    }

    override suspend fun registerDevice(baseUrl: String, token: String, name: String): String =
        api(baseUrl).registerDevice("Bearer $token", NameDto(name)).id

    override suspend fun fetchManifest(
        baseUrl: String,
        token: String,
        deviceId: String,
        lastSyncAt: String?,
        haveFileIds: Set<String>,
    ): SyncManifest {
        val dto = api(baseUrl).manifest(
            "Bearer $token",
            ManifestRequestDto(deviceId, lastSyncAt, haveFileIds.toList()),
        )
        return SyncManifest(
            dto.generatedAt,
            dto.files.map {
                ManifestFile(it.id, it.name, it.size, it.mime, it.checksum, it.url)
            },
        )
    }

    override suspend fun download(url: String, token: String): ByteArray {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("download failed: ${resp.code}")
            return resp.body?.bytes() ?: error("empty body")
        }
    }
}
