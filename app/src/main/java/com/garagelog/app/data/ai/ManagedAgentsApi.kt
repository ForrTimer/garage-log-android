package com.garagelog.app.data.ai

import android.os.Looper
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

/** One memory as the store lists it: enough to tell whether its content needs replacing. */
data class RemoteMemory(val id: String, val path: String, val sha256: String)

/**
 * Raw HTTP for the parts of the Managed Agents API the app uses: sessions, their events, and
 * memory stores. Hand-rolled on OkHttp for the same reason as [DirectClaudeClient] — the Java SDK
 * works on Android but adds ~6.6 MB for a handful of JSON calls.
 *
 * Everything here is one request; the conversation logic lives in [BobAgent].
 */
class ManagedAgentsApi(
    private val keyStore: AiKeyStore,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSession(body: JsonObject): JsonObject =
        call(post(url("sessions"), body, AGENTS_BETA))

    suspend fun retrieveSession(sessionId: String): JsonObject =
        call(get(url("sessions", sessionId), AGENTS_BETA))

    /** Returns the events as the server echoed them, with their ids. */
    suspend fun sendEvents(sessionId: String, events: List<JsonObject>): List<JsonObject> {
        val response = call(post(url("sessions", sessionId, "events"), buildJsonObject { put("events", JsonArray(events)) }, AGENTS_BETA))
        return response.array("data")
    }

    /** Every event so far, oldest first — the gap-filler when a dropped stream reconnects. */
    suspend fun listEvents(sessionId: String): List<JsonObject> = paged(url("sessions", sessionId, "events"), AGENTS_BETA)

    /**
     * Opens the live event stream. Returns once the server has answered, so anything sent after
     * this call is guaranteed to show up on it — the stream doesn't replay what came before it.
     * Asks for live previews of the agent's text so answers can appear as they're written.
     */
    suspend fun openStream(sessionId: String): EventStream = withContext(Dispatchers.IO) {
        val streamUrl = url("sessions", sessionId, "events", "stream").newBuilder()
            .addQueryParameter("event_deltas[]", "agent.message")
            .build()
        val response = execute(get(streamUrl, AGENTS_BETA).newBuilder().header("accept", "text/event-stream").build())
        if (!response.isSuccessful) {
            val body = response.body.string()
            response.close()
            throw errorFor(response.code, body)
        }
        EventStream(response, json)
    }

    suspend fun listMemories(storeId: String): List<RemoteMemory> =
        paged(url("memory_stores", storeId, "memories"), MEMORY_BETA).mapNotNull { item ->
            if (item.string("type") != "memory") return@mapNotNull null
            RemoteMemory(
                id = item.string("id") ?: return@mapNotNull null,
                path = item.string("path") ?: return@mapNotNull null,
                sha256 = item.string("content_sha256").orEmpty(),
            )
        }

    /** False when something else created this path first; the next sync settles it. */
    suspend fun createMemory(storeId: String, path: String, content: String): Boolean = try {
        call(post(url("memory_stores", storeId, "memories"), buildJsonObject { put("path", path); put("content", content) }, MEMORY_BETA))
        true
    } catch (e: ClaudeException) {
        if (e.status == 409) false else throw e
    }

    /** POST, not PATCH: the API reference lists PATCH, but the live API answers it with a 405. */
    suspend fun updateMemory(storeId: String, memoryId: String, content: String) {
        call(post(url("memory_stores", storeId, "memories", memoryId), buildJsonObject { put("content", content) }, MEMORY_BETA))
    }

    suspend fun deleteMemory(storeId: String, memoryId: String) {
        try {
            call(Request.Builder().url(url("memory_stores", storeId, "memories", memoryId)).delete().headers(MEMORY_BETA).build())
        } catch (e: ClaudeException) {
            if (e.status != 404) throw e
        }
    }

    /** Follows `next_page` until the list is exhausted. */
    private suspend fun paged(base: HttpUrl, beta: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var page: String? = null
        do {
            val pageUrl = base.newBuilder().apply { page?.let { addQueryParameter("page", it) } }.build()
            val response = call(get(pageUrl, beta))
            all += response.array("data")
            page = response.string("next_page")
        } while (page != null)
        return all
    }

    private fun get(url: HttpUrl, beta: String) = Request.Builder().url(url).get().headers(beta).build()

    private fun post(url: HttpUrl, body: JsonObject, beta: String) =
        Request.Builder().url(url).post(body.toString().toRequestBody(JSON_MEDIA)).headers(beta).build()

    private fun Request.Builder.headers(beta: String): Request.Builder {
        val apiKey = keyStore.apiKey()
        if (apiKey.isNullOrBlank()) throw ClaudeException(ClaudeErrorKind.NoApiKey, "No API key")
        return header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("anthropic-beta", beta)
            .header("content-type", "application/json")
    }

    private suspend fun call(request: Request): JsonObject = withContext(Dispatchers.IO) {
        execute(request).use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw errorFor(response.code, body)
            if (body.isBlank()) JsonObject(emptyMap())
            else runCatching { json.parseToJsonElement(body).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }
    }

    private fun execute(request: Request): Response = try {
        httpClient.newCall(request).execute()
    } catch (e: IOException) {
        throw networkFailure(e)
    }

    private fun errorFor(code: Int, body: String): ClaudeException {
        val apiMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.string("message")
        }.getOrNull()
        val kind = when (code) {
            401, 403 -> ClaudeErrorKind.Auth
            429 -> ClaudeErrorKind.RateLimit
            in 500..599 -> ClaudeErrorKind.Overloaded
            else -> ClaudeErrorKind.Other
        }
        // A 404 on the agent almost always means the API key belongs to a different workspace
        // than the one the agent was created in, which no amount of retrying fixes.
        val message = if (code == 404) "$ASSISTANT_NAME's agent wasn't found for this API key. ${apiMessage.orEmpty()}".trim()
        else apiMessage ?: "API error $code"
        return ClaudeException(kind, message, status = code)
    }

    private fun url(vararg segments: String): HttpUrl =
        BASE_URL.toHttpUrl().newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        /** Sessions and events. Memory stores have their own header, and sending both is a 400. */
        private const val AGENTS_BETA = "managed-agents-2026-04-01"
        private const val MEMORY_BETA = "agent-memory-2026-07-22"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * The event stream sends heartbeats, so a long silence means a dead connection, not a slow
         * answer — a short read timeout is right here, unlike the Messages API stream.
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()

        internal fun networkFailure(e: IOException): ClaudeException = when (e) {
            is java.net.SocketTimeoutException -> ClaudeException(ClaudeErrorKind.Timeout, e.message ?: "Timed out")
            else -> ClaudeException(ClaudeErrorKind.Network, e.message ?: "Network error")
        }
    }
}

/** A live session event stream: one parsed event per [next] call, null when the server closes it. */
class EventStream internal constructor(private val response: Response, private val json: Json) : Closeable {
    private val source: BufferedSource = response.body.source()

    /** Blocking; call off the main thread. Throws [IOException] if the connection drops. */
    fun next(): JsonObject? {
        while (true) {
            val line = source.readUtf8Line() ?: return null
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            return runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
        }
    }

    /**
     * Closing a stream mid-body writes a reset frame, which is network I/O, and the cancel hook
     * can fire on the main thread. Off the main thread it closes inline; on it, in the background.
     */
    override fun close() {
        if (Looper.myLooper() == Looper.getMainLooper()) Thread { runCatching { response.close() } }.start()
        else runCatching { response.close() }
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonElement.textBlocks(): List<String> =
    runCatching { jsonArray }.getOrNull()
        ?.mapNotNull { (it as? JsonObject)?.takeIf { block -> block.string("type") == "text" }?.string("text") }
        .orEmpty()
