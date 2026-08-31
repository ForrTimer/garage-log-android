package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IssueStatus { Open, InProgress, Resolved;
    val label: String get() = when (this) {
        Open -> "Open"
        InProgress -> "In progress"
        Resolved -> "Resolved"
    }
    companion object {
        fun fromLabel(label: String): IssueStatus = when (label) {
            "In progress" -> InProgress
            "Resolved" -> Resolved
            else -> Open
        }
    }
}

enum class IssuePriority { Normal, SafetyCritical;
    val label: String get() = if (this == SafetyCritical) "Safety-critical" else "Normal"
    companion object {
        fun fromLabel(label: String): IssuePriority = if (label == "Safety-critical") SafetyCritical else Normal
    }
}

@Entity(tableName = "issues")
data class IssueEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val title: String,
    val status: String,
    val priority: String,
    val dateOpened: String,
    val dateResolved: String,
    val description: String,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
