package com.garagelog.app.data.sync

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class DriveFileRef(val id: String, val appProperties: Map<String, String>)

@Serializable
private data class DriveFileMetadata(
    val name: String? = null,
    val parents: List<String>? = null,
    val appProperties: Map<String, String>? = null,
)

@Serializable
private data class DriveFile(val id: String, val appProperties: Map<String, String>? = null)

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

/**
 * Thin REST v3 wrapper scoped to Drive's hidden appDataFolder space — deliberately not the
 * full `google-api-services-drive` client, since all we need is list/get/create/update/delete
 * against one JSON snapshot file and a handful of photo files.
 */
class DriveApiClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findFileByName(accessToken: String, name: String): DriveFileRef? = withContext(Dispatchers.IO) {
        val url = filesUrl {
            addQueryParameter("q", "name = '$name' and trashed = false")
            addQueryParameter("fields", "files(id,appProperties)")
        }
        val request = authedRequest(accessToken, url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Drive list failed: ${response.code}" }
            decodeFileList(response.body?.string().orEmpty()).firstOrNull()
        }
    }

    suspend fun listFilesByProperty(accessToken: String, key: String, value: String): List<DriveFileRef> =
        withContext(Dispatchers.IO) {
            val url = filesUrl {
                addQueryParameter("q", "appProperties has { key='$key' and value='$value' } and trashed = false")
                addQueryParameter("fields", "files(id,appProperties)")
                addQueryParameter("pageSize", "1000")
            }
            val request = authedRequest(accessToken, url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Drive list failed: ${response.code}" }
                decodeFileList(response.body?.string().orEmpty())
            }
        }

    suspend fun downloadText(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = authedRequest(accessToken, url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Drive download failed: ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    suspend fun downloadToFile(accessToken: String, fileId: String, destination: File) = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = authedRequest(accessToken, url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Drive download failed: ${response.code}" }
            destination.outputStream().use { out -> response.body?.byteStream()?.copyTo(out) }
        }
        Unit
    }

    suspend fun createTextFile(accessToken: String, name: String, content: String): String = withContext(Dispatchers.IO) {
        val metadata = DriveFileMetadata(name = name, parents = listOf("appDataFolder"))
        uploadMultipart(accessToken, metadata, content.toByteArray(Charsets.UTF_8), "application/json")
    }

    suspend fun updateTextFile(accessToken: String, fileId: String, content: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .patch(content.toByteArray(Charsets.UTF_8).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Drive update failed: ${response.code}" }
        }
        Unit
    }

    suspend fun uploadPhoto(accessToken: String, file: File, photoId: String, ownerType: String, ownerId: String): String =
        withContext(Dispatchers.IO) {
            val metadata = DriveFileMetadata(
                name = "$photoId.jpg",
                parents = listOf("appDataFolder"),
                appProperties = mapOf("kind" to "photo", "photoId" to photoId, "ownerType" to ownerType, "ownerId" to ownerId),
            )
            uploadMultipart(accessToken, metadata, file.readBytes(), "image/jpeg")
        }

    suspend fun deleteFile(accessToken: String, fileId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .delete()
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            // 404 just means it's already gone on Drive's side - fine, not an error for our purposes.
            check(response.isSuccessful || response.code == 404) { "Drive delete failed: ${response.code}" }
        }
        Unit
    }

    private fun uploadMultipart(accessToken: String, metadata: DriveFileMetadata, content: ByteArray, contentType: String): String {
        val boundary = "garagelog-${System.currentTimeMillis()}-${content.size}"
        val metadataJson = json.encodeToString(DriveFileMetadata.serializer(), metadata)
        val body = MultipartBody.Builder(boundary)
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content.toRequestBody(contentType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Drive upload failed: ${response.code}" }
            return json.decodeFromString(DriveFile.serializer(), response.body?.string().orEmpty()).id
        }
    }

    private fun decodeFileList(body: String): List<DriveFileRef> =
        json.decodeFromString(DriveFileList.serializer(), body).files.map { DriveFileRef(it.id, it.appProperties ?: emptyMap()) }

    private fun filesUrl(configure: HttpUrl.Builder.() -> Unit): HttpUrl =
        "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .apply(configure)
            .build()

    private fun authedRequest(accessToken: String, url: HttpUrl) =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
}
