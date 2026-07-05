package com.lifeos.feature.nas.data

import com.lifeos.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class NasFile(val name: String, val path: String, val isDir: Boolean, val sizeBytes: Long)

/** Synology DSM Web API: auth + FileStation listing (§8.1, Module 8). */
@Singleton
class DsmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun login(baseUrl: String, user: String, password: String): String =
        withContext(dispatchers.io) {
            val url = "$baseUrl/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login" +
                "&account=${encode(user)}&passwd=${encode(password)}&session=FileStation&format=sid"
            val body = get(url)
            val response = json.decodeFromString<AuthResponse>(body)
            if (!response.success || response.data?.sid == null) {
                throw IOException("DSM login failed (code ${response.error?.code ?: "?"})")
            }
            response.data.sid
        }

    suspend fun listShares(baseUrl: String, sid: String): List<NasFile> =
        withContext(dispatchers.io) {
            val body = get(
                "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.List&version=2" +
                    "&method=list_share&_sid=$sid",
            )
            parseFiles(body)
        }

    suspend fun listFolder(baseUrl: String, sid: String, path: String): List<NasFile> =
        withContext(dispatchers.io) {
            val body = get(
                "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.List&version=2" +
                    "&method=list&folder_path=${encode(path)}&_sid=$sid",
            )
            parseFiles(body)
        }

    /** Health ping for the ServerApps view ([src 23]): is a LAN service up? */
    suspend fun ping(url: String): Boolean = withContext(dispatchers.io) {
        try {
            okHttpClient.newBuilder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(url).build())
                .execute()
                .use { it.code < 500 }
        } catch (e: Exception) {
            false
        }
    }

    private fun get(url: String): String =
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DSM returned HTTP ${response.code}")
            response.body.string()
        }

    internal fun parseFiles(body: String): List<NasFile> {
        val response = json.decodeFromString<ListResponse>(body)
        if (!response.success) throw IOException("FileStation error ${response.error?.code ?: "?"}")
        val entries = response.data?.shares ?: response.data?.files ?: emptyList()
        return entries.map {
            NasFile(
                name = it.name,
                path = it.path,
                isDir = it.isdir,
                sizeBytes = it.additional?.size ?: 0,
            )
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    @Serializable
    internal data class AuthResponse(
        val success: Boolean = false,
        val data: AuthData? = null,
        val error: DsmError? = null,
    )

    @Serializable
    internal data class AuthData(val sid: String? = null)

    @Serializable
    internal data class DsmError(val code: Int = 0)

    @Serializable
    internal data class ListResponse(
        val success: Boolean = false,
        val data: ListData? = null,
        val error: DsmError? = null,
    )

    @Serializable
    internal data class ListData(
        val shares: List<FileDto>? = null,
        val files: List<FileDto>? = null,
    )

    @Serializable
    internal data class FileDto(
        val name: String = "",
        val path: String = "",
        val isdir: Boolean = false,
        val additional: AdditionalDto? = null,
    )

    @Serializable
    internal data class AdditionalDto(val size: Long? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
