package com.garagelog.app.data.ai

import android.content.Context
import com.garagelog.app.BuildConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What one turn produced. */
data class BobTurn(
    val text: String,
    val sources: List<ClaudeSource>,
    /** JSON input of each custom tool Bob called, by tool name. */
    val toolInputs: Map<String, String>,
    /** The session hit its spend cap; [text] may be partial, and the thread starts fresh next time. */
    val budgetReached: Boolean,
)

/**
 * Bob as a server-hosted Managed Agent: one agent session per conversation thread, so follow-ups
 * continue where they left off and the instructions are cached instead of resent.
 *
 * Every session mounts two memory stores — Bob's own notes (read-write) and [GarageMirror]'s copy
 * of the app's records (read-only) — and carries a hard spend cap. A thread whose session hits the
 * cap is simply started fresh on the next question; Bob's notes carry anything worth keeping.
 */
class BobAgent(
    context: Context,
    private val api: ManagedAgentsApi,
    private val runHolder: AiRunHolder,
) {
    /** Thread key → session id. Local-only, like the AI tables: a lost mapping just starts fresh. */
    private val sessions = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = listOf(AGENT_ID, ENVIRONMENT_ID, NOTES_STORE_ID, DATA_STORE_ID).all { it.isNotBlank() }

    fun sessionFor(threadKey: String): String? = sessions.getString(threadKey, null)

    fun adoptSession(threadKey: String, sessionId: String) = sessions.edit().putString(threadKey, sessionId).apply()

    fun forget(threadKey: String) = sessions.edit().remove(threadKey).apply()

    fun forgetAll() = sessions.edit().clear().apply()

    /**
     * Runs one turn in [threadKey]'s session, creating it if there isn't a usable one. [opening] is
     * the message for a brand-new session (it must stand on its own); [followUp] is what to send to
     * a session that's still going. [threadKey] null means a one-off session nobody continues.
     *
     * [onCustomTool] answers a custom tool call; its return value is sent back as the result.
     */
    suspend fun runTurn(
        runKey: String,
        threadKey: String?,
        title: String,
        opening: () -> String,
        followUp: String,
        onCustomTool: (name: String, inputJson: String) -> String = { _, _ -> "Received." },
    ): BobTurn {
        if (!isConfigured) {
            throw ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME isn't set up on this build. Run managed-agents/setup.sh, then rebuild.")
        }
        val existing = threadKey?.let { sessionFor(it) }?.takeIf { usable(it) }
        val sessionId = existing ?: createSession(title).also { id -> threadKey?.let { adoptSession(it, id) } }
        val message = if (existing != null) followUp else opening()

        val turn = try {
            stream(runKey, sessionId, message, onCustomTool)
        } catch (e: CancellationException) {
            // The worker was stopped; don't leave the agent working (and billing) for nobody.
            withContext(NonCancellable) { runCatching { api.sendEvents(sessionId, listOf(event("user.interrupt"))) } }
            throw e
        }
        if (turn.budgetReached && threadKey != null) forget(threadKey)
        return turn
    }

    /** A session the next message can go to: not ended, and not paused at its spend cap. */
    private suspend fun usable(sessionId: String): Boolean = try {
        val session = api.retrieveSession(sessionId)
        session.string("status") != "terminated" && session.string("archived_at") == null
    } catch (e: ClaudeException) {
        if (e.status == 404) false else throw e
    }

    private suspend fun createSession(title: String): String {
        val body = buildJsonObject {
            put("agent", AGENT_ID)
            put("environment_id", ENVIRONMENT_ID)
            put("title", title.take(200))
            put(
                "resources",
                buildJsonArray {
                    add(memoryStore(NOTES_STORE_ID, "read_write"))
                    add(memoryStore(DATA_STORE_ID, "read_only"))
                },
            )
            putJsonObject("budget") {
                put("type", "limit")
                putJsonObject("max_list_cost") {
                    put("amount", SESSION_BUDGET_CENTS)
                    put("currency", "USD")
                }
            }
        }
        return api.createSession(body).string("id")
            ?: throw ClaudeException(ClaudeErrorKind.Other, "Couldn't start a session with $ASSISTANT_NAME.")
    }

    private fun memoryStore(id: String, access: String) = buildJsonObject {
        put("type", "memory_store")
        put("memory_store_id", id)
        put("access", access)
    }

    /**
     * Sends [message] and follows the stream until the turn ends. The stream is opened first —
     * it doesn't replay — and if it drops mid-turn, it's reopened and the gap filled from the
     * event list, deduplicated by event id.
     */
    private suspend fun stream(
        runKey: String,
        sessionId: String,
        message: String,
        onCustomTool: (String, String) -> String,
    ): BobTurn {
        val state = TurnState()
        runHolder.start(runKey)
        var stream = api.openStream(sessionId)
        try {
            val sent = api.sendEvents(sessionId, listOf(userMessage(message)))
            state.messageId = sent.firstOrNull()?.string("id")
            var reconnects = 0
            while (!state.done) {
                coroutineContextEnsureActive()
                val event = try {
                    withContext(Dispatchers.IO) { stream.next() }
                } catch (e: IOException) {
                    coroutineContextEnsureActive()
                    if (reconnects++ >= MAX_RECONNECTS) throw ManagedAgentsApi.networkFailure(e)
                    stream.close()
                    delay(1_000L * reconnects)
                    stream = api.openStream(sessionId)
                    // Anything emitted while disconnected, in order, from our message onwards.
                    api.listEvents(sessionId)
                        .dropWhile { state.messageId != null && it.string("id") != state.messageId }
                        .forEach { handle(it, state, runKey, sessionId, onCustomTool) }
                    continue
                }
                if (event == null) {
                    // Server closed the stream without an end-of-turn; reopen as for a drop.
                    if (reconnects++ >= MAX_RECONNECTS) {
                        throw ClaudeException(ClaudeErrorKind.Network, "Lost the connection to $ASSISTANT_NAME.")
                    }
                    stream.close()
                    stream = api.openStream(sessionId)
                    api.listEvents(sessionId)
                        .dropWhile { state.messageId != null && it.string("id") != state.messageId }
                        .forEach { handle(it, state, runKey, sessionId, onCustomTool) }
                    continue
                }
                handle(event, state, runKey, sessionId, onCustomTool)
            }
        } finally {
            stream.close()
        }

        val text = state.answer.joinToString("").trim()
        state.failure?.let { failure -> if (text.isEmpty() && state.toolInputs.isEmpty()) throw failure }
        if (text.isEmpty() && state.toolInputs.isEmpty() && !state.budgetReached) {
            throw ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME didn't answer. Try again.")
        }
        runHolder.finish(runKey)
        return BobTurn(text, state.sources.values.toList(), state.toolInputs, state.budgetReached)
    }

    private class TurnState {
        var messageId: String? = null
        /** Our message has been picked up; idle events before that belong to an earlier turn. */
        var started = false
        var done = false
        var budgetReached = false
        var failure: ClaudeException? = null
        val seen = HashSet<String>()
        /** Text since the last tool call — earlier text is "let me check" narration, not the answer. */
        val answer = mutableListOf<String>()
        val sources = LinkedHashMap<String, ClaudeSource>()
        val toolInputs = HashMap<String, String>()
        /** Tool-use event id → tool name, to recognise which results are web search results. */
        val toolNames = HashMap<String, String>()
    }

    private suspend fun handle(
        event: JsonObject,
        state: TurnState,
        runKey: String,
        sessionId: String,
        onCustomTool: (String, String) -> String,
    ) {
        val type = event.string("type") ?: return
        // Live previews carry no id of their own and are never replayed, so they skip dedupe.
        when (type) {
            "event_start" -> return
            "event_delta" -> {
                val text = event.obj("delta")?.obj("content")?.takeIf { it.string("type") == "text" }?.string("text")
                if (text != null) runHolder.appendDelta(runKey, text)
                return
            }
        }
        val id = event.string("id")
        if (id != null && !state.seen.add(id)) return

        when (type) {
            "user.message" -> if (id == state.messageId || state.messageId == null) state.started = true
            "session.status_running" -> state.started = true
            "agent.message" -> {
                state.started = true
                state.answer += event["content"]?.textBlocks().orEmpty()
            }
            "agent.tool_use" -> {
                val name = event.string("name").orEmpty()
                id?.let { state.toolNames[it] = name }
                state.answer.clear()
                runHolder.start(runKey)
                if (name == "web_search" || name == "web_fetch") runHolder.markSearching(runKey) else runHolder.markReading(runKey)
            }
            "agent.tool_result" -> {
                val toolName = event.string("tool_use_id")?.let { state.toolNames[it] }
                if (toolName == null || toolName == "web_search") collectSources(event, state)
            }
            "agent.custom_tool_use" -> {
                val name = event.string("name").orEmpty()
                val input = event["input"]?.toString() ?: "{}"
                state.toolInputs[name] = input
                state.answer.clear()
                runHolder.markWriting(runKey)
                val result = onCustomTool(name, input)
                api.sendEvents(
                    sessionId,
                    listOf(
                        buildJsonObject {
                            put("type", "user.custom_tool_result")
                            put("custom_tool_use_id", id.orEmpty())
                            put("content", JsonArray(listOf(textBlock(result))))
                        },
                    ),
                )
            }
            "session.error" -> {
                val message = event.obj("error")?.string("message") ?: "Something went wrong."
                state.failure = ClaudeException(ClaudeErrorKind.Other, message)
            }
            "session.status_idle" -> {
                if (!state.started) return
                when (event.obj("stop_reason")?.string("type")) {
                    "requires_action" -> Unit
                    "budget_reached" -> {
                        state.budgetReached = true
                        state.done = true
                    }
                    "end_turn" -> state.done = true
                    else -> {
                        if (state.failure == null) state.failure = ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME stopped before finishing.")
                        state.done = true
                    }
                }
            }
            "session.status_terminated" -> state.done = true
        }
    }

    /** Web search results arrive as `search_result` items carrying the page's url and title. */
    private fun collectSources(event: JsonObject, state: TurnState) {
        val items = event["content"] as? JsonArray ?: return
        items.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            if (obj.string("type") != "search_result") return@forEach
            val url = obj.string("source") ?: return@forEach
            state.sources.putIfAbsent(url, ClaudeSource(title = obj.string("title")?.takeIf { it.isNotBlank() } ?: url, url = url))
        }
    }

    private suspend fun coroutineContextEnsureActive() = kotlin.coroutines.coroutineContext.ensureActive()

    private fun userMessage(text: String) = buildJsonObject {
        put("type", "user.message")
        put("content", JsonArray(listOf(textBlock(text))))
    }

    private fun event(type: String) = buildJsonObject { put("type", type) }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", JsonPrimitive(text))
    }

    companion object {
        private const val PREFS_NAME = "garage_log_bob_sessions"
        private const val MAX_RECONNECTS = 3

        /** $1 per session, in cents as the API wants it — Ethan's number. */
        private const val SESSION_BUDGET_CENTS = "100"

        private val AGENT_ID = BuildConfig.BOB_AGENT_ID
        private val ENVIRONMENT_ID = BuildConfig.BOB_ENVIRONMENT_ID
        private val NOTES_STORE_ID = BuildConfig.BOB_NOTES_STORE_ID
        val DATA_STORE_ID: String = BuildConfig.BOB_DATA_STORE_ID
    }
}
