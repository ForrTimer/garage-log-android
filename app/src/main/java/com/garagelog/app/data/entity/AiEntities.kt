package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The saved Claude diagnosis for one issue. Keyed by issue so re-running replaces rather than
 * accumulating — the owner wants the current best answer, not a pile of attempts.
 *
 * [sourcesJson] is a serialized list of {title,url}; storing it denormalized keeps this a single
 * table with no join for what is only ever read as one blob alongside its content.
 *
 * Not synced to Drive and not in the JSON backup (see [com.garagelog.app.data.ai.AiKeyStore] for
 * the same reasoning applied to the key): these are regenerable from the issue itself.
 */
@Entity(tableName = "ai_diagnoses")
data class AiDiagnosisEntity(
    @PrimaryKey val issueId: String,
    val vehicleId: String,
    val content: String,
    val sourcesJson: String,
    val model: String,
    val createdAt: Long,
    /** Odometer when this ran, so a stale diagnosis can say how many miles ago it was written. */
    val milesAtRun: Int?,
)

@Entity(tableName = "ai_chat_messages")
data class AiChatMessageEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    /**
     * Non-null for a follow-up conversation hanging off one issue's diagnosis; null for the
     * vehicle-wide "ask anything" thread. Kept alongside [vehicleId] rather than replacing it so
     * deleting a vehicle still cascades to every thread about it, issue-scoped ones included.
     */
    val issueId: String? = null,
    /** "user" or "assistant" — matches the Messages API role wire values. */
    val role: String,
    val content: String,
    val sourcesJson: String,
    val createdAt: Long,
) {
    companion object {
        /** Identifies a conversation: one per issue, plus one per vehicle for general questions. */
        fun threadKey(vehicleId: String, issueId: String?): String =
            if (issueId != null) "issue:$issueId" else "vehicle:$vehicleId"
    }

    val threadKey: String get() = threadKey(vehicleId, issueId)
}
