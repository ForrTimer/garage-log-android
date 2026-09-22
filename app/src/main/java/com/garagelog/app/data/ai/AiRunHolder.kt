package com.garagelog.app.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Live state of one in-flight request, for the screen that's watching it. */
data class AiRunState(
    val running: Boolean = false,
    val searching: Boolean = false,
    val retrying: Boolean = false,
    /** Bob has finished researching and is writing a structured answer (a tool call). */
    val writing: Boolean = false,
    val partialText: String = "",
    val error: String? = null,
) {
    /** What the spinner says while this is running. */
    val statusText: String
        get() = when {
            retrying -> "$ASSISTANT_NAME is busy. Retrying…"
            writing -> "Putting it together…"
            searching -> "Searching the web…"
            else -> "Thinking…"
        }
}

/**
 * Process-wide record of what's currently running, keyed by what it's running *for* (an issue id
 * for a diagnosis, a thread key for a chat) â same shape as [com.garagelog.app.data.sync.SyncStatusHolder].
 *
 * Keyed rather than single-valued because the run outlives the screen that started it: leaving the
 * diagnosis screen and coming back has to find the same run still going, and two different targets
 * must not overwrite each other's progress. It previously lived in the ViewModel as one value and
 * was reset on every open, which is exactly how an in-flight answer appeared to vanish.
 *
 * This is only for showing progress. The answer itself is written to the database by the worker,
 * so nothing here needs to survive the process dying.
 */
class AiRunHolder {
    private val _runs = MutableStateFlow<Map<String, AiRunState>>(emptyMap())
    val runs: StateFlow<Map<String, AiRunState>> = _runs

    fun start(key: String) = update(key) { AiRunState(running = true) }

    fun appendDelta(key: String, delta: String) = update(key) {
        it.copy(running = true, searching = false, partialText = it.partialText + delta)
    }

    fun markSearching(key: String) = update(key) { it.copy(running = true, searching = true) }

    fun markWriting(key: String) = update(key) { it.copy(running = true, searching = false, writing = true) }

    /** Waiting out an "overloaded" response before trying again; any partial answer is dropped. */
    fun markRetrying(key: String) = update(key) { AiRunState(running = true, retrying = true) }

    fun finish(key: String) { _runs.update { it - key } }

    fun fail(key: String, message: String) = update(key) {
        AiRunState(running = false, error = message)
    }

    fun clearError(key: String) {
        if (_runs.value[key]?.error != null) finish(key)
    }

    fun isRunning(key: String): Boolean = _runs.value[key]?.running == true

    private fun update(key: String, transform: (AiRunState) -> AiRunState) {
        _runs.update { current -> current + (key to transform(current[key] ?: AiRunState())) }
    }
}
