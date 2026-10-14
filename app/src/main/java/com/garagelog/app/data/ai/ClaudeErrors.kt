package com.garagelog.app.data.ai

import kotlinx.serialization.Serializable

/** A web page Bob consulted while answering, surfaced so answers are checkable. */
@Serializable
data class ClaudeSource(val title: String, val url: String)

enum class ClaudeErrorKind { NoApiKey, Auth, RateLimit, Overloaded, Network, Timeout, Other }

class ClaudeException(
    val kind: ClaudeErrorKind,
    message: String,
    /** The HTTP status, when the failure was an error response rather than a dropped connection. */
    val status: Int? = null,
    /** The message is Bob's own reply (he declined to do it), not a failure — shown as his words. */
    val fromAssistant: Boolean = false,
) : Exception(message) {
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
