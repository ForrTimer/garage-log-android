package com.garagelog.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StepPriority { Low, Medium, High;
    companion object {
        fun fromLabel(label: String): StepPriority = entries.find { it.name == label } ?: Medium
    }
}

/**
 * One checklist item under a vehicle's build. `phaseId` is normally set automatically by
 * [com.garagelog.app.util.assignBuildBuckets] based on the step's priority/cost against each
 * phase's bucket criteria — see [BuildPhaseEntity]. It only becomes sticky once a user drags/
 * edits it into a phase directly, at which point [manualPhaseOverride] stops the auto-bucketer
 * from moving it again.
 */
@Entity(tableName = "build_steps")
data class BuildStepEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val phaseId: String?,
    val title: String,
    val notes: String,
    val priority: String,
    val status: String,
    val estimatedCost: Double?,
    val actualCost: Double?,
    val order: Int,
    val manualPhaseOverride: Boolean = false,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
