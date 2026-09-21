package com.garagelog.app.data.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A web page Claude consulted while answering, surfaced so answers are checkable. */
@Serializable
data class ClaudeSource(val title: String, val url: String)

sealed interface ClaudeEvent {
    data class TextDelta(val text: String) : ClaudeEvent
    /** Claude started a web search — drives the "Searching the web…" progress state. */
    data object Searching : ClaudeEvent
    data class SourcesFound(val sources: List<ClaudeSource>) : ClaudeEvent
}

data class ClaudeMessage(val role: String, val content: String)

data class ClaudeRequest(
    val system: String,
    val messages: List<ClaudeMessage>,
    val webSearch: Boolean = true,
    val maxTokens: Int = 8000,
    val model: String = DirectClaudeClient.DIAGNOSIS_MODEL,
)

enum class ClaudeErrorKind { NoApiKey, Auth, RateLimit, Overloaded, Network, Timeout, Other }

class ClaudeException(val kind: ClaudeErrorKind, message: String) : Exception(message) {
    /** Phrased for a person looking at their truck, not a stack trace. */
    val userMessage: String
        get() = when (kind) {
            ClaudeErrorKind.NoApiKey -> "No API key set. Add one in Settings → $ASSISTANT_NAME."
            ClaudeErrorKind.Auth -> "That API key was rejected. Check it in Settings → $ASSISTANT_NAME."
            ClaudeErrorKind.RateLimit -> "Rate limited by the API. Wait a moment and try again."
            ClaudeErrorKind.Overloaded -> "$ASSISTANT_NAME is overloaded right now. Try again shortly."
            ClaudeErrorKind.Network -> "Couldn't reach $ASSISTANT_NAME. Check your connection."
            ClaudeErrorKind.Timeout -> "That took longer than $ASSISTANT_NAME is allowed. Try again."
            ClaudeErrorKind.Other -> message ?: "Something went wrong talking to $ASSISTANT_NAME."
        }
}

/**
 * The app's one path to Claude. [DirectClaudeClient] talks to the API with a key held on the
 * device, which is the right trade for a single-user install. If this app is ever published,
 * a proxy-backed implementation swaps in here without any caller changing — nothing above this
 * interface knows a key exists.
 */
interface ClaudeClient {
    fun stream(request: ClaudeRequest): Flow<ClaudeEvent>
}

class DirectClaudeClient(
    private val keyStore: AiKeyStore,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : ClaudeClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun stream(request: ClaudeRequest): Flow<ClaudeEvent> = flow {
        val apiKey = keyStore.apiKey()
        if (apiKey.isNullOrBlank()) throw ClaudeException(ClaudeErrorKind.NoApiKey, "No API key")

        val httpRequest = Request.Builder()
            .url(MESSAGES_URL)
            .post(json.encodeToString(MessagesRequest.serializer(), request.toWire()).toRequestBody(JSON_MEDIA))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .build()

        val response = try {
            httpClient.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw networkFailure(e)
        }

        response.use {
            if (!it.isSuccessful) throw errorFor(it.code, it.body.string())
            val source = it.body.source()
            while (true) {
                // Cancellation must propagate as CancellationException, never be reported as a
                // network problem — the two look identical at the socket but mean opposite things.
                currentCoroutineContext().ensureActive()
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()
                    throw networkFailure(e)
                } ?: break
                if (!line.startsWith(DATA_PREFIX)) continue
                val payload = line.removePrefix(DATA_PREFIX).trim()
                if (payload.isEmpty()) continue
                emitEventsFor(payload) { event -> emit(event) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * SSE frames are parsed loosely on purpose: block types we don't render (thinking, citation
     * deltas, usage) are skipped rather than failing the stream, so a new block type shipping
     * server-side can't break an answer mid-sentence.
     */
    private suspend inline fun emitEventsFor(payload: String, emit: (ClaudeEvent) -> Unit) {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        when (root["type"]?.jsonPrimitive?.contentOrNullSafe()) {
            "content_block_delta" -> {
                val delta = root["delta"]?.jsonObject ?: return
                if (delta["type"]?.jsonPrimitive?.contentOrNullSafe() == "text_delta") {
                    delta["text"]?.jsonPrimitive?.contentOrNullSafe()?.let { emit(ClaudeEvent.TextDelta(it)) }
                }
            }
            "content_block_start" -> {
                val block = root["content_block"]?.jsonObject ?: return
                when (block["type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    "server_tool_use" -> emit(ClaudeEvent.Searching)
                    "web_search_tool_result" -> parseSources(block)?.let { emit(ClaudeEvent.SourcesFound(it)) }
                }
            }
            "error" -> {
                val message = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNullSafe()
                throw ClaudeException(ClaudeErrorKind.Other, message ?: "Stream error")
            }
        }
    }

    /** A failed search returns an error object here instead of a result array — not an exception. */
    private fun parseSources(block: JsonObject): List<ClaudeSource>? {
        val content = block["content"] ?: return null
        val array = runCatching { content.jsonArray }.getOrNull() ?: return null
        return array.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() } ?: url
            ClaudeSource(title = title, url = url)
        }.takeIf { it.isNotEmpty() }
    }

    /**
     * A timeout and a dead connection both surface as [IOException] but mean different things to
     * someone staring at the screen — "check your connection" is actively misleading when the
     * request was simply taking a long time.
     */
    private fun networkFailure(e: IOException): ClaudeException = when (e) {
        is java.net.SocketTimeoutException ->
            ClaudeException(ClaudeErrorKind.Timeout, e.message ?: "Timed out")
        else -> ClaudeException(ClaudeErrorKind.Network, e.message ?: "Network error")
    }

    private fun errorFor(code: Int, body: String): ClaudeException {
        val apiMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
                ?.jsonPrimitive?.contentOrNullSafe()
        }.getOrNull()
        val kind = when (code) {
            401, 403 -> ClaudeErrorKind.Auth
            429 -> ClaudeErrorKind.RateLimit
            529 -> ClaudeErrorKind.Overloaded
            in 500..599 -> ClaudeErrorKind.Overloaded
            else -> ClaudeErrorKind.Other
        }
        return ClaudeException(kind, apiMessage ?: "API error $code")
    }

    private fun ClaudeRequest.toWire() = MessagesRequest(
        model = model,
        maxTokens = maxTokens,
        system = listOf(SystemBlock(text = system, cacheControl = CacheControl())),
        messages = messages.map { WireMessage(role = it.role, content = it.content) },
        tools = if (webSearch) listOf(WireTool()) else null,
    )

    companion object {
        /**
         * A diagnosis gets the strongest model on purpose: it's one call, it reasons over the
         * vehicle's whole history, and being wrong about a brake problem costs far more than the
         * token difference.
         */
        const val DIAGNOSIS_MODEL = "claude-opus-5"

        /**
         * Chat is back-and-forth and mostly lookups — specs, capacities, "is this normal" — where
         * Sonnet answers as well and returns sooner. Ethan asked for this one specifically.
         */
        const val CHAT_MODEL = "claude-sonnet-5"
        private const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val DATA_PREFIX = "data:"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Web search plus thinking can run well past a default read timeout before the first byte. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            // A hard ceiling so a stalled call can't hang forever, but generous: a diagnosis that
            // runs several web searches was brushing up against the old six-minute cap.
            .callTimeout(12, TimeUnit.MINUTES)
            .build()
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

@Serializable
private data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = true,
    val system: List<SystemBlock>,
    val messages: List<WireMessage>,
    val tools: List<WireTool>? = null,
)

@Serializable
private data class SystemBlock(
    val type: String = "text",
    val text: String,
    @SerialName("cache_control") val cacheControl: CacheControl? = null,
)

@Serializable
private data class CacheControl(val type: String = "ephemeral")

@Serializable
private data class WireMessage(val role: String, val content: String)

/** Server-side web search: runs on Anthropic's infrastructure, no client-side tool loop. */
@Serializable
private data class WireTool(
    val type: String = "web_search_20260209",
    val name: String = "web_search",
    @SerialName("max_uses") val maxUses: Int = 6,
)
