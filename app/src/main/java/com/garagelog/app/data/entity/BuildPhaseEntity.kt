package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PhaseStatus { NotStarted, InProgress, Done;
    val label: String get() = when (this) {
        NotStarted -> "Not started"
        InProgress -> "In progress"
        Done -> "Done"
    }
    companion object {
        fun fromLabel(label: String): PhaseStatus = when (label) {
            "In progress" -> InProgress
            "Done" -> Done
            else -> NotStarted
        }
    }
}

@Entity(tableName = "build_phases")
data class BuildPhaseEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val phase: String,
    val status: String,
    val order: Int,
    val notes: String,
)
